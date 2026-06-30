"""Tests for POST /api/v1/detect/burst — multi-frame consensus voting."""
import io

import pytest
from fastapi import HTTPException, UploadFile

from app.config import RuntimeConfig
from app.routers.detect import detect_burst
from app.services.alpr import Detection

BBOX = {"x": 0, "y": 0, "w": 10, "h": 10}


class FakeProducer:
    topic = "parking.plate.detected"

    def __init__(self):
        self.published = []

    async def publish(self, event, key):
        self.published.append((event, key))


class FakeStorage:
    def __init__(self):
        self.stored = []  # (bytes, gate, direction)

    def put_frame(self, image_bytes, gate_id, direction):
        self.stored.append((image_bytes, gate_id, direction))
        return f"frames/{len(self.stored)}.jpg"

    def put_detection(self, image_bytes, bbox, plate_number, confidence, gate_id, direction):
        # The router stores via put_detection now (boxed + labelled photo); the test only cares which
        # frame's bytes were persisted, so record the same (bytes, gate, direction) shape.
        self.stored.append((image_bytes, gate_id, direction))
        return f"frames/{len(self.stored)}.jpg"


class FakeAlpr:
    """Maps exact frame bytes -> (plate, confidence); unknown bytes read as nothing."""

    def __init__(self, mapping):
        self._mapping = mapping

    def detect(self, content):
        hit = self._mapping.get(content)
        if hit is None:
            return None
        plate, conf = hit
        return Detection(plate, conf, BBOX, 100)


def _uploads(*names):
    return [UploadFile(file=io.BytesIO(n), filename="f.jpg") for n in names]


def _runtime(threshold=0.4):
    return RuntimeConfig(confidence_threshold=threshold, retry_attempts=3)


@pytest.mark.asyncio
async def test_burst_publishes_single_consensus_event_and_one_image():
    producer, storage = FakeProducer(), FakeStorage()
    alpr = FakeAlpr({
        b"f0": ("51G-97162", 0.50),
        b"f1": ("51G-97162", 0.60),
        b"f2": ("80F-33333", 0.95),   # one noisy outlier — must not win
        b"f3": ("51G-97162", 0.70),
    })

    resp = await detect_burst(
        images=_uploads(b"f0", b"f1", b"f2", b"f3"),
        gate_id="GATE_ENTRY_01", direction="IN", min_votes=2, min_confidence=None,
        producer=producer, alpr=alpr, runtime=_runtime(), storage=storage,
    )

    assert resp.accepted is True and resp.published is True
    assert resp.plateNumber == "51G-97162"
    assert resp.votes >= 2
    # exactly ONE event published, ONE image stored (the winning plate's best frame so far)
    assert len(producer.published) == 1
    event, key = producer.published[0]
    assert event["plateNumber"] == "51G-97162" and event["gateId"] == "GATE_ENTRY_01"
    assert key == "GATE_ENTRY_01"
    assert len(storage.stored) == 1
    assert storage.stored[0][0] == b"f1"  # highest-confidence 51G frame seen before early-exit


@pytest.mark.asyncio
async def test_burst_without_consensus_returns_202_and_publishes_nothing():
    producer, storage = FakeProducer(), FakeStorage()
    alpr = FakeAlpr({
        b"f0": ("51G-97162", 0.90),
        b"f1": ("80F-33333", 0.90),   # every plate seen once -> no plate reaches min_votes=2
    })

    resp = await detect_burst(
        images=_uploads(b"f0", b"f1"),
        gate_id="GATE_ENTRY_01", direction="IN", min_votes=2, min_confidence=None,
        producer=producer, alpr=alpr, runtime=_runtime(), storage=storage,
    )

    assert resp.status_code == 202          # JSONResponse, near-miss reported
    assert producer.published == []         # nothing published
    assert storage.stored == []             # nothing stored


@pytest.mark.asyncio
async def test_burst_ignores_frames_below_confidence_threshold():
    producer, storage = FakeProducer(), FakeStorage()
    alpr = FakeAlpr({
        b"f0": ("51G-97162", 0.20),   # below 0.4 threshold -> not a vote
        b"f1": ("51G-97162", 0.50),
        b"f2": ("51G-97162", 0.60),
    })

    resp = await detect_burst(
        images=_uploads(b"f0", b"f1", b"f2"),
        gate_id="GATE_ENTRY_01", direction="IN", min_votes=2, min_confidence=None,
        producer=producer, alpr=alpr, runtime=_runtime(threshold=0.4), storage=storage,
    )

    assert resp.accepted is True
    assert resp.votes == 2                   # the 0.20 frame was skipped
    assert storage.stored[0][0] == b"f2"     # highest-confidence valid frame of the winning plate


@pytest.mark.asyncio
async def test_burst_rejects_invalid_direction():
    with pytest.raises(HTTPException) as exc:
        await detect_burst(
            images=_uploads(b"f0"),
            gate_id="GATE_ENTRY_01", direction="SIDEWAYS", min_votes=2, min_confidence=None,
            producer=FakeProducer(), alpr=FakeAlpr({}), runtime=_runtime(), storage=FakeStorage(),
        )
    assert exc.value.status_code == 400
