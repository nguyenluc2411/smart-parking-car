"""POST /api/v1/simulate/trigger — fake a camera trigger (publishes plate.detected)."""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status

from app import plate as plate_util
from app.dependencies import get_producer
from app.events import build_plate_detected
from app.kafka.producer import PlateEventProducer
from app.metrics import plate_detected_total
from app.schemas import SimulateTriggerRequest, SimulateTriggerResponse

router = APIRouter()


@router.post("/simulate/trigger", response_model=SimulateTriggerResponse)
async def simulate_trigger(
    request: SimulateTriggerRequest,
    producer: PlateEventProducer = Depends(get_producer),
) -> SimulateTriggerResponse:
    plate = plate_util.normalize(request.plate_number)
    if not plate_util.is_valid(plate):  # BR-001-3
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid plate format: {request.plate_number}",
        )

    event = build_plate_detected(plate, request.simulate_confidence, request.gate_id,
                                 request.direction)
    await producer.publish(event, key=request.gate_id)
    plate_detected_total.labels(direction=request.direction, source="simulate").inc()

    return SimulateTriggerResponse(eventId=event["eventId"], published=True, topic=producer.topic)
