"""Background auto-capture from RTSP/USB — no human trigger (Phase 1).

Mirrors ``tools/camera_agent.py`` burst + vote + publish, integrated into the edge-agent
process. Disabled by default; enable via ``AUTO_CAPTURE_ENABLED=true`` and set a camera
source per gate.
"""
from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass, field

from app.config import Settings
from app.config import RuntimeConfig
from app.kafka.producer import PlateEventProducer
from app.services.alpr import AlprService
from app.services.burst_pipeline import BurstPipelineResult, run_burst_pipeline
from app.services.gate_fsm import GateCaptureFsm, GateFsmSnapshot
from app.services.zone_tracker import ZoneOccupancyTracker
from app.services.storage import FrameStorage

logger = logging.getLogger("edge-agent.auto")


@dataclass
class GateAutoConfig:
    gate_id: str
    direction: str
    source: str


@dataclass
class AutoCaptureStatus:
    enabled: bool
    running: bool
    gates: list[GateFsmSnapshot] = field(default_factory=list)
    last_error: str | None = None
    last_scan_at: float | None = None
    last_plate: str | None = None
    last_published: bool = False


def _parse_gate_configs(settings: Settings) -> list[GateAutoConfig]:
    configs: list[GateAutoConfig] = []
    if settings.auto_capture_entry_source.strip():
        configs.append(GateAutoConfig(
            "GATE_ENTRY_01", "IN", settings.auto_capture_entry_source.strip()))
    if settings.auto_capture_exit_source.strip():
        configs.append(GateAutoConfig(
            "GATE_EXIT_01", "OUT", settings.auto_capture_exit_source.strip()))
    return configs


def _capture_burst_frames(source: str, frames: int, gap: float) -> list[bytes]:
    import cv2  # noqa: PLC0415 — optional; only when auto-capture enabled

    src: str | int = int(source) if source.isdigit() else source
    cap = cv2.VideoCapture(src)
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open camera source: {source}")
    out: list[bytes] = []
    try:
        for i in range(frames):
            ok, frame = cap.read()
            if not ok:
                break
            ok_enc, buf = cv2.imencode(".jpg", frame)
            if ok_enc:
                out.append(buf.tobytes())
            if gap > 0 and i < frames - 1:
                time.sleep(gap)
    finally:
        cap.release()
    return out


class AutoCaptureService:
    """One asyncio task per configured gate camera."""

    def __init__(
        self,
        settings: Settings,
        runtime: RuntimeConfig,
        alpr: AlprService,
        storage: FrameStorage,
        producer: PlateEventProducer,
    ) -> None:
        self._settings = settings
        self._runtime = runtime
        self._alpr = alpr
        self._storage = storage
        self._producer = producer
        self._fsm = GateCaptureFsm(settings.auto_capture_cooldown)
        self._zone: dict[tuple[str, str], ZoneOccupancyTracker] = {}
        self._tasks: list[asyncio.Task] = []
        self._running = False
        self._last_error: str | None = None
        self._last_scan_at: float | None = None
        self._last_plate: str | None = None
        self._last_published = False

    @property
    def enabled(self) -> bool:
        return self._settings.auto_capture_enabled and bool(_parse_gate_configs(self._settings))

    def status(self) -> AutoCaptureStatus:
        gates = _parse_gate_configs(self._settings)
        snapshots = [self._fsm.snapshot(c.gate_id, c.direction) for c in gates]
        return AutoCaptureStatus(
            enabled=self.enabled,
            running=self._running,
            gates=snapshots,
            last_error=self._last_error,
            last_scan_at=self._last_scan_at,
            last_plate=self._last_plate,
            last_published=self._last_published,
        )

    async def start(self) -> None:
        if not self.enabled:
            logger.info("Auto-capture disabled (AUTO_CAPTURE_ENABLED=false or no sources)")
            return
        configs = _parse_gate_configs(self._settings)
        self._running = True
        for cfg in configs:
            task = asyncio.create_task(self._watch_gate(cfg), name=f"auto-{cfg.gate_id}")
            self._tasks.append(task)
        logger.info("Auto-capture started for %d gate(s)", len(configs))

    async def stop(self) -> None:
        self._running = False
        for task in self._tasks:
            task.cancel()
        if self._tasks:
            await asyncio.gather(*self._tasks, return_exceptions=True)
        self._tasks.clear()

    async def _watch_gate(self, cfg: GateAutoConfig) -> None:
        s = self._settings
        interval = s.auto_capture_interval
        key = (cfg.gate_id, cfg.direction)
        if key not in self._zone:
            self._zone[key] = ZoneOccupancyTracker(leave_streak_need=4)
        zone = self._zone[key]
        while self._running:
            try:
                if not self._fsm.can_trigger(cfg.gate_id, cfg.direction):
                    await asyncio.sleep(0.3)
                    continue

                probe_frames = await asyncio.to_thread(
                    _capture_burst_frames, cfg.source, 1, 0.0)
                if not probe_frames:
                    self._last_error = f"No frames from {cfg.source}"
                    await asyncio.sleep(interval)
                    continue

                presence = self._alpr.detect_presence(
                    probe_frames[0], roi_x=0.08, roi_y=0.38, roi_w=0.84, roi_h=0.24,
                    vehicle_only=True)
                zone_update = zone.update(presence.present, presence.source)
                if zone_update.action != "scan":
                    await asyncio.sleep(interval)
                    continue

                frames = await asyncio.to_thread(
                    _capture_burst_frames, cfg.source, s.auto_capture_frames,
                    s.auto_capture_frame_gap)
                self._last_scan_at = time.time()

                if not frames:
                    self._last_error = f"No frames from {cfg.source}"
                    await asyncio.sleep(interval)
                    continue

                self._last_error = None
                result = await run_burst_pipeline(
                    frames,
                    gate_id=cfg.gate_id,
                    direction=cfg.direction,
                    alpr=self._alpr,
                    runtime=self._runtime,
                    storage=self._storage,
                    producer=self._producer,
                    min_votes=s.auto_capture_min_votes,
                    min_confidence=s.auto_capture_min_confidence,
                    source="auto",
                )
                self._last_plate = result.plate_number
                self._last_published = result.published
                if result.published and result.plate_number:
                    self._fsm.mark_published(cfg.gate_id, cfg.direction, result.plate_number)
                    logger.info(
                        "Auto-capture published: gate=%s dir=%s plate=%s votes=%d",
                        cfg.gate_id, cfg.direction, result.plate_number, result.votes)

                await asyncio.sleep(interval)
            except asyncio.CancelledError:
                raise
            except Exception as exc:  # noqa: BLE001 — keep watcher alive
                self._last_error = f"{type(exc).__name__}: {exc}"
                logger.exception("Auto-capture error gate=%s", cfg.gate_id)
                await asyncio.sleep(max(interval, 2.0))
