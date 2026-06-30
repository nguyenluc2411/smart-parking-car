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


class PlateVote(BaseModel):
    plateNumber: str
    votes: int
    meanConfidence: float


class BurstDetectResponse(BaseModel):
    """Result of a multi-frame burst: frames vote, one consensus plate is published.

    ``accepted`` (and ``published``) is True only when a plate cleared the vote/confidence
    thresholds — that is the single ``plate.detected`` that opened the gate. When no plate
    reached consensus the top-ranked candidate is still reported (accepted=False, nothing
    published, no image stored) so a near-miss is visible for the KPI report.
    """
    accepted: bool
    published: bool
    plateNumber: Optional[str] = None
    confidence: float = 0.0          # mean OCR confidence of the winning/top-ranked plate
    votes: int = 0                   # frames that read the winning plate
    framesRead: int = 0              # frames that produced ANY readable plate
    framesSubmitted: int = 0
    processingMs: int = 0
    imageRef: Optional[str] = None
    candidates: list[PlateVote] = Field(default_factory=list)  # tally, best-first


class ConfigResponse(BaseModel):
    confidenceThreshold: float
    retryAttempts: int
    gateMapping: dict[str, str]
    modelVersion: str


class ConfigUpdateRequest(BaseModel):
    confidenceThreshold: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    retryAttempts: Optional[int] = Field(default=None, ge=0)
