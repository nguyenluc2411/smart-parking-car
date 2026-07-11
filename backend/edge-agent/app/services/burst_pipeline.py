"""Shared multi-frame burst OCR + Kafka publish (used by REST and auto-capture)."""
from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any

from app.config import RuntimeConfig
from app.events import build_plate_detected
from app.kafka.producer import PlateEventProducer
from app.metrics import burst_detect_total, detection_processing_ms, plate_detected_total
from app.schemas import PlateVote
from app.services import aggregate
from app.services.alpr import AlprService
from app.services.storage import FrameStorage


@dataclass
class BurstPipelineResult:
    accepted: bool
    published: bool
    plate_number: str | None = None
    confidence: float = 0.0
    votes: int = 0
    frames_read: int = 0
    frames_submitted: int = 0
    processing_ms: int = 0
    image_ref: str | None = None
    candidates: list[PlateVote] = field(default_factory=list)
    skip_reason: str | None = None
    _winning_frame: bytes | None = field(default=None, repr=False)
    _winning_bbox: dict[str, Any] | None = field(default=None, repr=False)


def process_burst_frames(
    frames: list[bytes],
    *,
    alpr: AlprService,
    runtime: RuntimeConfig,
    min_votes: int = 2,
    min_confidence: float | None = None,
) -> BurstPipelineResult:
    """CPU-bound OCR + voting (no I/O). Safe to run in a thread pool."""
    if not frames:
        return BurstPipelineResult(
            accepted=False, published=False, frames_submitted=0, skip_reason="NO_FRAMES")

    min_conf = runtime.confidence_threshold if min_confidence is None else min_confidence
    min_votes = min(min_votes, len(frames))

    start = time.perf_counter()
    reads: list[tuple[str, float]] = []
    best_frame: dict[str, tuple[float, bytes, dict]] = {}
    consensus = None

    for content in frames:
        detection = alpr.detect(content)
        if detection is None or detection.confidence < runtime.confidence_threshold:
            continue
        reads.append((detection.plate_number, detection.confidence))
        prev = best_frame.get(detection.plate_number)
        if prev is None or detection.confidence > prev[0]:
            best_frame[detection.plate_number] = (
                detection.confidence, content, detection.bbox)
        consensus = aggregate.decide(
            reads, min_votes=min_votes, min_mean_confidence=min_conf)
        if consensus is not None:
            break

    processing_ms = int((time.perf_counter() - start) * 1000)
    ranked = aggregate.tally(reads)
    candidates = [
        PlateVote(plateNumber=c.plate_number, votes=c.votes,
                  meanConfidence=round(c.mean_confidence, 4))
        for c in ranked
    ]
    frames_read = ranked[0].frames if ranked else 0

    if consensus is None:
        top = ranked[0] if ranked else None
        return BurstPipelineResult(
            accepted=False,
            published=False,
            plate_number=top.plate_number if top else None,
            confidence=round(top.mean_confidence, 4) if top else 0.0,
            votes=top.votes if top else 0,
            frames_read=frames_read,
            frames_submitted=len(frames),
            processing_ms=processing_ms,
            candidates=candidates,
        )

    _, winning_frame, winning_bbox = best_frame[consensus.plate_number]
    return BurstPipelineResult(
        accepted=True,
        published=False,
        plate_number=consensus.plate_number,
        confidence=round(consensus.mean_confidence, 4),
        votes=consensus.votes,
        frames_read=frames_read,
        frames_submitted=len(frames),
        processing_ms=processing_ms,
        candidates=candidates,
        _winning_frame=winning_frame,
        _winning_bbox=winning_bbox,
    )


async def run_burst_pipeline(
    frames: list[bytes],
    *,
    gate_id: str,
    direction: str,
    alpr: AlprService,
    runtime: RuntimeConfig,
    storage: FrameStorage,
    producer: PlateEventProducer,
    min_votes: int = 2,
    min_confidence: float | None = None,
    source: str = "burst",
) -> BurstPipelineResult:
    """OCR each frame (thread pool), vote, publish one plate.detected when consensus is reached."""
    from fastapi.concurrency import run_in_threadpool

    if not frames:
        burst_detect_total.labels(result="empty").inc()
        return BurstPipelineResult(
            accepted=False, published=False, frames_submitted=0, skip_reason="NO_FRAMES")

    result = await run_in_threadpool(
        process_burst_frames, frames,
        alpr=alpr, runtime=runtime, min_votes=min_votes, min_confidence=min_confidence,
    )

    if not result.accepted:
        burst_detect_total.labels(result="rejected" if result.frames_read else "empty").inc()
        return result

    if result._winning_frame is None or result._winning_bbox is None:
        burst_detect_total.labels(result="rejected").inc()
        return BurstPipelineResult(
            accepted=False, published=False, frames_submitted=len(frames),
            processing_ms=result.processing_ms, candidates=result.candidates)

    detection_processing_ms.observe(result.processing_ms)
    object_key = await run_in_threadpool(
        storage.put_detection, result._winning_frame, result._winning_bbox,
        result.plate_number, result.confidence, gate_id, direction)
    event = build_plate_detected(
        result.plate_number, result.confidence, gate_id, direction,
        image_ref=object_key, processing_ms=result.processing_ms,
    )
    await producer.publish(event, key=gate_id)
    plate_detected_total.labels(direction=direction, source=source).inc()
    burst_detect_total.labels(result="accepted").inc()

    result.published = True
    result.image_ref = object_key
    return result
