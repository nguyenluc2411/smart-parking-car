"""Kafka producers for parking.plate.detected and parking.gate.state (key = gateId, CLAUDE.md §5)."""
from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone

from aiokafka import AIOKafkaProducer

logger = logging.getLogger(__name__)


class PlateEventProducer:
    def __init__(self, bootstrap_servers: str, topic: str) -> None:
        self._bootstrap_servers = bootstrap_servers
        self._topic = topic
        self._producer: AIOKafkaProducer | None = None

    @property
    def topic(self) -> str:
        return self._topic

    async def start(self) -> None:
        # Values are sent as JSON strings (parking-service consumes String + ObjectMapper).
        self._producer = AIOKafkaProducer(
            bootstrap_servers=self._bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k is not None else None,
            acks="all",
            enable_idempotence=True,
        )
        await self._producer.start()
        logger.info("Kafka producer started (topic=%s)", self._topic)

    async def stop(self) -> None:
        if self._producer is not None:
            await self._producer.stop()
            logger.info("Kafka producer stopped")

    async def publish(self, event: dict, key: str) -> None:
        if self._producer is None:
            raise RuntimeError("Producer not started")
        await self._producer.send_and_wait(self._topic, value=event, key=key)
        logger.info("Published %s: plate=%s gate=%s dir=%s",
                    self._topic, event.get("plateNumber"), event.get("gateId"),
                    event.get("direction"))


class GateStateProducer:
    """Publishes parking.gate.state so parking-service can keep gates.status in sync (BR-006-2).

    Shape per docs/api-contracts.md; key = gateId. Used as the barrier's on_state_change callback.
    """

    def __init__(self, bootstrap_servers: str, topic: str) -> None:
        self._bootstrap_servers = bootstrap_servers
        self._topic = topic
        self._producer: AIOKafkaProducer | None = None

    async def start(self) -> None:
        self._producer = AIOKafkaProducer(
            bootstrap_servers=self._bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k is not None else None,
            acks="all",
            enable_idempotence=True,
        )
        await self._producer.start()
        logger.info("Kafka gate-state producer started (topic=%s)", self._topic)

    async def stop(self) -> None:
        if self._producer is not None:
            await self._producer.stop()
            logger.info("Kafka gate-state producer stopped")

    async def publish(self, gate_id: str, status: str, reason: str) -> None:
        """on_state_change callback for BarrierSimulator. status: OPEN|CLOSED; reason: command|auto|startup."""
        if self._producer is None:
            raise RuntimeError("Producer not started")
        event = {
            "eventId": str(uuid.uuid4()),
            "gateId": gate_id,
            "status": status,
            "reason": reason,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        await self._producer.send_and_wait(self._topic, value=event, key=gate_id)
        logger.info("Published %s: gate=%s status=%s reason=%s", self._topic, gate_id, status, reason)
