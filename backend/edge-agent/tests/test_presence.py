"""Tests for POST /api/v1/detect/presence — lightweight YOLO ROI gate."""
import io

import pytest
from fastapi import UploadFile

from app.routers.detect import detect_presence
from app.services.alpr import PresenceResult


class FakeAlpr:
    def __init__(self, result: PresenceResult):
        self._result = result
        self.calls = []

    def detect_presence(self, content, **kwargs):
        self.calls.append((content, kwargs))
        return self._result


@pytest.mark.asyncio
async def test_presence_returns_yolo_hit():
    alpr = FakeAlpr(PresenceResult(
        present=True, confidence=0.72,
        bbox={"x": 10, "y": 20, "w": 100, "h": 30},
        in_roi=True, processing_ms=45, source="plate",
    ))
    resp = await detect_presence(
        image=UploadFile(file=io.BytesIO(b"roi"), filename="roi.jpg"),
        alpr=alpr,
    )
    assert resp.present is True
    assert resp.inRoi is True
    assert resp.confidence == 0.72
    assert resp.boundingBox.w == 100
    assert resp.processingMs == 45


@pytest.mark.asyncio
async def test_presence_returns_miss():
    alpr = FakeAlpr(PresenceResult(False, 0.0, None, False, 12, "none"))
    resp = await detect_presence(
        image=UploadFile(file=io.BytesIO(b"empty"), filename="roi.jpg"),
        alpr=alpr,
    )
    assert resp.present is False
    assert resp.boundingBox is None
    assert "target" in resp.message or "no" in resp.message
