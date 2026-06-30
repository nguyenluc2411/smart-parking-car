import pytest

from app.routers.simulate import simulate_trigger
from app.schemas import SimulateTriggerRequest


class FakeProducer:
    topic = "parking.plate.detected"

    def __init__(self):
        self.published = []

    async def publish(self, event, key):
        self.published.append((event, key))


@pytest.mark.asyncio
async def test_simulate_trigger_publishes_normalized_event():
    producer = FakeProducer()
    request = SimulateTriggerRequest(
        gate_id="GATE_ENTRY_01", plate_number=" 51f-123.45 ", direction="IN")

    response = await simulate_trigger(request, producer=producer)

    assert response.published is True
    assert response.topic == "parking.plate.detected"
    event, key = producer.published[0]
    assert event["plateNumber"] == "51F-12345"   # normalized (dotless)
    assert event["gateId"] == "GATE_ENTRY_01"
    assert key == "GATE_ENTRY_01"


@pytest.mark.asyncio
async def test_simulate_trigger_rejects_invalid_plate():
    from fastapi import HTTPException

    producer = FakeProducer()
    request = SimulateTriggerRequest(
        gate_id="GATE_ENTRY_01", plate_number="not-a-plate", direction="IN")

    with pytest.raises(HTTPException) as exc:
        await simulate_trigger(request, producer=producer)
    assert exc.value.status_code == 400
    assert producer.published == []
