"""Builders for outbound Kafka event payloads (shapes per docs/api-contracts.md)."""
from __future__ import annotations

from datetime import datetime, timezone
from uuid import uuid4


def build_plate_detected(
    plate_number: str,
    confidence: float,
    gate_id: str,
    direction: str,
    image_ref: str | None = None,
    processing_ms: int = 0,
) -> dict:
    """parking.plate.detected payload."""
    return {
        "eventId": str(uuid4()),
        "plateNumber": plate_number,
        "confidence": confidence,
        "gateId": gate_id,
        "direction": direction,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "imageRef": image_ref,
        "processingMs": processing_ms,
    }
