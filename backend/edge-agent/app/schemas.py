"""Pydantic request/response models (shapes per docs/api-contracts.md)."""
from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field

Direction = Literal["IN", "OUT"]


class SimulateTriggerRequest(BaseModel):
    gate_id: str
    plate_number: str
    direction: Direction
    simulate_confidence: float = Field(default=0.95, ge=0.0, le=1.0)


class SimulateTriggerResponse(BaseModel):
    eventId: str
    published: bool
    topic: str


class BoundingBox(BaseModel):
    x: int
    y: int
    w: int
    h: int


class DetectResponse(BaseModel):
    plateNumber: str
    confidence: float
    processingMs: int
    boundingBox: BoundingBox


class DetectFailedResponse(BaseModel):
    plateNumber: Optional[str] = None
    confidence: float = 0.0
    reason: str = "LOW_CONFIDENCE"


class PresenceResponse(BaseModel):
    """YOLO / vehicle probe in the trigger ROI — no OCR, no Kafka publish."""
    present: bool
    confidence: float = 0.0
    inRoi: bool = False
    boundingBox: Optional[BoundingBox] = None
    processingMs: int = 0
    source: str = ""   # plate | vehicle | motion | none
    message: str = ""


class PlateVote(BaseModel):
    plateNumber: str
    votes: int
    meanConfidence: float


class BurstDetectResponse(BaseModel):
    """Result of a multi-frame burst: frames vote, one consensus plate is published."""
    accepted: bool
    published: bool
    plateNumber: Optional[str] = None
    confidence: float = 0.0
    votes: int = 0
    framesRead: int = 0
    framesSubmitted: int = 0
    processingMs: int = 0
    imageRef: Optional[str] = None
    candidates: list[PlateVote] = Field(default_factory=list)


class ConfigResponse(BaseModel):
    confidenceThreshold: float
    retryAttempts: int
    gateMapping: dict[str, str]
    modelVersion: str


class ConfigUpdateRequest(BaseModel):
    confidenceThreshold: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    retryAttempts: Optional[int] = Field(default=None, ge=0)
