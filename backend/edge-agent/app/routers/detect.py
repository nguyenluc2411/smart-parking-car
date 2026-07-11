"""POST /api/v1/detect — run ALPR on a frame and publish plate.detected on success."""
from __future__ import annotations

import logging

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from app.config import RuntimeConfig
from app.dependencies import get_alpr, get_producer, get_runtime, get_storage
from app.events import build_plate_detected
from app.kafka.producer import PlateEventProducer
from app.metrics import detect_requests_total, detection_processing_ms, plate_detected_total
from app.schemas import (
    BoundingBox,
    BurstDetectResponse,
    DetectFailedResponse,
    DetectResponse,
    PresenceResponse,
)
from app.services.alpr import AlprService
from app.services.burst_pipeline import run_burst_pipeline
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
    detection = await run_in_threadpool(alpr.detect, content)

    if detection is None or detection.confidence < runtime.confidence_threshold:
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
    if direction not in ("IN", "OUT"):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="direction must be IN or OUT")
    if not images:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="at least one frame is required")

    frames: list[bytes] = []
    for upload in images:
        frames.append(await upload.read())

    result = await run_burst_pipeline(
        frames,
        gate_id=gate_id,
        direction=direction,
        alpr=alpr,
        runtime=runtime,
        storage=storage,
        producer=producer,
        min_votes=min_votes,
        min_confidence=min_confidence,
        source="burst",
    )

    if not result.accepted:
        return JSONResponse(
            status_code=status.HTTP_202_ACCEPTED,
            content=BurstDetectResponse(
                accepted=False, published=False,
                plateNumber=result.plate_number,
                confidence=result.confidence,
                votes=result.votes,
                framesRead=result.frames_read,
                framesSubmitted=result.frames_submitted,
                processingMs=result.processing_ms,
                candidates=result.candidates,
            ).model_dump(),
        )

    return BurstDetectResponse(
        accepted=True, published=result.published,
        plateNumber=result.plate_number, confidence=result.confidence,
        votes=result.votes, framesRead=result.frames_read,
        framesSubmitted=result.frames_submitted,
        processingMs=result.processing_ms,
        imageRef=result.image_ref, candidates=result.candidates,
    )


@router.post("/detect/presence")
async def detect_presence(
    image: UploadFile = File(...),
    roi_x: float = Form(0.0, ge=0.0, le=1.0),
    roi_y: float = Form(0.0, ge=0.0, le=1.0),
    roi_w: float = Form(1.0, gt=0.0, le=1.0),
    roi_h: float = Form(1.0, gt=0.0, le=1.0),
    min_confidence: float = Form(0.15, ge=0.0, le=1.0),
    vehicle_only: bool = Form(True),
    alpr: AlprService = Depends(get_alpr),
):
    """Lightweight vehicle probe in ROI — no OCR, no Kafka. Gates full burst scans."""
    content = await image.read()
    result = await run_in_threadpool(
        alpr.detect_presence, content,
        roi_x=roi_x, roi_y=roi_y, roi_w=roi_w, roi_h=roi_h,
        min_confidence=min_confidence, vehicle_only=vehicle_only,
    )
    bbox = None
    if result.bbox:
        bbox = BoundingBox(**result.bbox)
    msg = {
        "plate": "plate in ROI",
        "vehicle": "vehicle in ROI",
        "motion": "target detail in ROI",
    }.get(result.source, "no target in ROI" if not result.present else "target in ROI")
    return PresenceResponse(
        present=result.present,
        confidence=result.confidence,
        inRoi=result.in_roi,
        boundingBox=bbox,
        processingMs=result.processing_ms,
        source=result.source,
        message=msg,
    )
