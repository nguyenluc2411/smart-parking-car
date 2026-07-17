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


class PersistentCamera:
    """Manages a single persistent cv2.VideoCapture connection in a background thread.

    Prevents thread starvation and OS device lockups caused by repeatedly opening and
    closing the camera device node or socket every 2 seconds.
    """
    def __init__(self, source: str) -> None:
        self.source = source
        self.running = False
        self._last_frame: bytes | None = None
        self._lock = threading.Lock()
        self._thread: threading.Thread | None = None
        self._error: str | None = None

    def start(self) -> None:
        if self.running:
            return
        self.running = True
        import threading  # noqa: PLC0415
        self._thread = threading.Thread(target=self._run, name=f"cam-reader-{self.source}", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self.running = False
        if self._thread:
            self._thread.join(timeout=2.0)
            self._thread = None

    def get_latest_frame(self) -> bytes | None:
        with self._lock:
            return self._last_frame

    @property
    def error(self) -> str | None:
        with self._lock:
            return self._error

    def _run(self) -> None:
        import time  # noqa: PLC0415
        import cv2  # noqa: PLC0415
        src: str | int = int(self.source) if self.source.isdigit() else self.source
        cap = None

        while self.running:
            if cap is None or not cap.isOpened():
                if cap is not None:
                    cap.release()
                    cap = None
                try:
                    cap = cv2.VideoCapture(src)
                    if not cap.isOpened():
                        with self._lock:
                            self._error = f"Cannot open camera source: {self.source}"
                        time.sleep(2.0)
                        continue
                    cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
                    with self._lock:
                        self._error = None
                except Exception as e:
                    with self._lock:
                        self._error = f"Error opening source {self.source}: {e}"
                    time.sleep(2.0)
                    continue

            try:
                ok, frame = cap.read()
                if ok:
                    ok_enc, buf = cv2.imencode(".jpg", frame)
                    if ok_enc:
                        with self._lock:
                            self._last_frame = buf.tobytes()
                            self._error = None
                else:
                    with self._lock:
                        self._error = "Camera read returned false"
                    cap.release()
                    cap = None
                    time.sleep(1.0)
            except Exception as e:
                with self._lock:
                    self._error = f"Error reading frame: {e}"
                if cap is not None:
                    cap.release()
                    cap = None
                time.sleep(1.0)

        if cap is not None:
            cap.release()


import threading  # noqa: E402


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
        self._cameras: dict[str, PersistentCamera] = {}
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
            if cfg.source not in self._cameras:
                cam = PersistentCamera(cfg.source)
                self._cameras[cfg.source] = cam
                cam.start()

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

        for cam in self._cameras.values():
            cam.stop()
        self._cameras.clear()

    async def _watch_gate(self, cfg: GateAutoConfig) -> None:
        s = self._settings
        interval = s.auto_capture_interval
        key = (cfg.gate_id, cfg.direction)
        if key not in self._zone:
            self._zone[key] = ZoneOccupancyTracker(leave_streak_need=4)
        zone = self._zone[key]

        cam = self._cameras.get(cfg.source)
        if not cam:
            logger.error("No persistent camera reader for source %s", cfg.source)
            return

        while self._running:
            try:
                if not self._fsm.can_trigger(cfg.gate_id, cfg.direction):
                    await asyncio.sleep(0.3)
                    continue

                probe_frame = cam.get_latest_frame()
                cam_err = cam.error
                if cam_err:
                    self._last_error = cam_err
                    await asyncio.sleep(interval)
                    continue

                if not probe_frame:
                    self._last_error = f"No frames from camera source {cfg.source} yet"
                    await asyncio.sleep(interval)
                    continue

                presence = self._alpr.detect_presence(
                    probe_frame, roi_x=0.08, roi_y=0.38, roi_w=0.84, roi_h=0.24,
                    vehicle_only=True)
                zone_update = zone.update(presence.present, presence.source)
                if zone_update.action != "scan":
                    await asyncio.sleep(interval)
                    continue

                frames = []
                for i in range(s.auto_capture_frames):
                    f = cam.get_latest_frame()
                    if f:
                        frames.append(f)
                    if s.auto_capture_frame_gap > 0 and i < s.auto_capture_frames - 1:
                        await asyncio.sleep(s.auto_capture_frame_gap)

                self._last_scan_at = time.time()

                if not frames:
                    self._last_error = f"No frames from {cfg.source} during burst"
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
