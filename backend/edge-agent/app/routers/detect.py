"""POST /api/v1/detect — run ALPR on a frame and publish plate.detected on success."""
from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from app.config import RuntimeConfig
from app.dependencies import get_alpr, get_producer, get_runtime, get_storage
from app.events import build_plate_detected
from app.kafka.producer import PlateEventProducer
from app.metrics import (
    burst_detect_total,
    detect_requests_total,
    detection_processing_ms,
    plate_detected_total,
)
from app.schemas import (
    BoundingBox,
    BurstDetectResponse,
    DetectFailedResponse,
    DetectResponse,
    PlateVote,
)
from app.services import aggregate
from app.services.alpr import AlprService
from app.services.storage import FrameStorage

logger = logging.getLogger("edge-agent.detect")

router = APIRouter()


@router.post("/detect")
async def detect(
    image: UploadFile = File(...),
    gate_id: str = Form(...),
    direction: str = Form(...),
    producer: PlateEventProducer = Depends(get_producer),
    alpr: AlprService = Depends(get_alpr),
    runtime: RuntimeConfig = Depends(get_runtime),
    storage: FrameStorage = Depends(get_storage),
):
    if direction not in ("IN", "OUT"):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="direction must be IN or OUT")

    content = await image.read()
    # ALPR is blocking CPU work (YOLO + EasyOCR, ~seconds). Run it off the event loop so a
    # slow detection cannot stall the single uvicorn worker / other in-flight requests.
    detection = await run_in_threadpool(alpr.detect, content)

    # BR-001-2: below the confidence threshold (or unreadable) -> 422, nothing published.
    if detection is None or detection.confidence < runtime.confidence_threshold:
        # Log WHY so a rejection is never opaque (esp. "ảnh rõ vẫn báo không đọc được"): None means
        # no valid VN plate was assembled (detector miss / OCR engine down — check /health alprReady);
        # a low-confidence read means the plate was read but didn't clear the threshold.
        if detection is None:
            logger.warning("detect REJECTED (gate=%s dir=%s, %d bytes): no valid VN plate read "
                           "(detector miss or OCR engine unavailable — see /health alprReady)",
                           gate_id, direction, len(content))
        else:
            logger.warning("detect REJECTED (gate=%s dir=%s): read '%s' but confidence %.2f < "
                           "threshold %.2f", gate_id, direction, detection.plate_number,
                           detection.confidence, runtime.confidence_threshold)
        detect_requests_total.labels(result="rejected").inc()
        return JSONResponse(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                            content=DetectFailedResponse().model_dump())

    detection_processing_ms.observe(detection.processing_ms)
    # Persist the frame to object storage so the entry/exit photo can be traced later, with the plate
    # boxed + labelled (and a close-up saved alongside) so an operator can actually read it in the UI.
    # Best-effort: a storage failure returns None and we fall back to the upload filename.
    object_key = await run_in_threadpool(
        storage.put_detection, content, detection.bbox, detection.plate_number,
        detection.confidence, gate_id, direction)
    image_ref = object_key or (f"frames/{image.filename}" if image.filename else None)
    event = build_plate_detected(
        detection.plate_number, detection.confidence, gate_id, direction,
        image_ref=image_ref,
        processing_ms=detection.processing_ms,
    )
    await producer.publish(event, key=gate_id)
    plate_detected_total.labels(direction=direction, source="detect").inc()
    detect_requests_total.labels(result="accepted").inc()

    return DetectResponse(
        plateNumber=detection.plate_number,
        confidence=detection.confidence,
        processingMs=detection.processing_ms,
        boundingBox=BoundingBox(**detection.bbox),
    )


@router.post("/detect/burst")
async def detect_burst(
    images: list[UploadFile] = File(..., description="frames of the same car, oldest first"),
    gate_id: str = Form(...),
    direction: str = Form(...),
    min_votes: int = Form(2, ge=1, description="frames that must agree to accept a plate"),
    min_confidence: float | None = Form(
        None, ge=0.0, le=1.0,
        description="min mean OCR confidence to accept (defaults to the runtime threshold)"),
    producer: PlateEventProducer = Depends(get_producer),
    alpr: AlprService = Depends(get_alpr),
    runtime: RuntimeConfig = Depends(get_runtime),
    storage: FrameStorage = Depends(get_storage),
):
    """Multi-frame consensus: OCR each frame, vote, and publish ONE plate.detected for the winner.

    A real camera sees a car for several seconds, so one frame's misread/blur shouldn't drive the
    gate. Each frame is OCR'd and frames that read the SAME canonical plate vote together
    (``app.services.aggregate``); the burst stops early the moment a plate clears ``min_votes`` AND
    ``min_confidence`` (keeps camera→gate latency bounded). Only the winning plate is published, and
    only the single best frame of that plate is stored — so a burst yields at most one event and one
    image (or none, returning 202, when no plate reaches consensus).
    """
    if direction not in ("IN", "OUT"):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="direction must be IN or OUT")
    if not images:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="at least one frame is required")

    min_conf = runtime.confidence_threshold if min_confidence is None else min_confidence
    min_votes = min(min_votes, len(images))  # never need more votes than frames submitted

    start = time.perf_counter()
    # Per winning plate we keep the highest-confidence frame's bytes so we can store exactly one image.
    reads: list[tuple[str, float]] = []
    best_frame: dict[str, tuple[float, bytes, dict]] = {}  # plate -> (best conf, frame bytes, bbox)
    consensus = None
    for upload in images:
        content = await upload.read()
        # ALPR is blocking CPU work; run it off the event loop so the burst can't stall the worker.
        detection = await run_in_threadpool(alpr.detect, content)
        # BR-001-2: a frame below the confidence threshold is treated as unreadable (no vote).
        if detection is None or detection.confidence < runtime.confidence_threshold:
            continue
        reads.append((detection.plate_number, detection.confidence))
        prev = best_frame.get(detection.plate_number)
        if prev is None or detection.confidence > prev[0]:
            best_frame[detection.plate_number] = (detection.confidence, content, detection.bbox)
        # Stop as soon as the votes so far are decisive — a clean plate opens the gate fast.
        consensus = aggregate.decide(reads, min_votes=min_votes, min_mean_confidence=min_conf)
        if consensus is not None:
            break

    processing_ms = int((time.perf_counter() - start) * 1000)
    ranked = aggregate.tally(reads)
    candidates = [PlateVote(plateNumber=c.plate_number, votes=c.votes,
                            meanConfidence=round(c.mean_confidence, 4)) for c in ranked]
    frames_read = ranked[0].frames if ranked else 0

    # No plate reached consensus -> publish nothing, store nothing. Report the near-miss (202).
    if consensus is None:
        burst_detect_total.labels(result="rejected" if reads else "empty").inc()
        top = ranked[0] if ranked else None
        return JSONResponse(
            status_code=status.HTTP_202_ACCEPTED,
            content=BurstDetectResponse(
                accepted=False, published=False,
                plateNumber=top.plate_number if top else None,
                confidence=round(top.mean_confidence, 4) if top else 0.0,
                votes=top.votes if top else 0,
                framesRead=frames_read, framesSubmitted=len(images),
                processingMs=processing_ms, candidates=candidates,
            ).model_dump(),
        )

    detection_processing_ms.observe(processing_ms)
    # Store ONLY the best frame of the winning plate (boxed + labelled + close-up), then publish a
    # single consensus event.
    _, winning_frame, winning_bbox = best_frame[consensus.plate_number]
    object_key = await run_in_threadpool(
        storage.put_detection, winning_frame, winning_bbox, consensus.plate_number,
        consensus.mean_confidence, gate_id, direction)
    event = build_plate_detected(
        consensus.plate_number, consensus.mean_confidence, gate_id, direction,
        image_ref=object_key, processing_ms=processing_ms,
    )
    await producer.publish(event, key=gate_id)
    plate_detected_total.labels(direction=direction, source="burst").inc()
    burst_detect_total.labels(result="accepted").inc()

    return BurstDetectResponse(
        accepted=True, published=True,
        plateNumber=consensus.plate_number, confidence=round(consensus.mean_confidence, 4),
        votes=consensus.votes, framesRead=frames_read, framesSubmitted=len(images),
        processingMs=processing_ms, imageRef=object_key, candidates=candidates,
    )
