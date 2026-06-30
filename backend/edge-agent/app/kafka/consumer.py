"""Kafka consumer for parking.gate.command -> drives the barrier simulator."""
from __future__ import annotations

import asyncio
import json
import logging

from aiokafka import AIOKafkaConsumer

from app.services.barrier import BarrierSimulator

logger = logging.getLogger(__name__)


class GateCommandConsumer:
    def __init__(self, bootstrap_servers: str, topic: str, group_id: str,
                 barrier: BarrierSimulator) -> None:
        self._bootstrap_servers = bootstrap_servers
        self._topic = topic
        self._group_id = group_id
        self._barrier = barrier
        self._consumer: AIOKafkaConsumer | None = None
        self._task: asyncio.Task | None = None

    async def start(self) -> None:
        self._consumer = AIOKafkaConsumer(
            self._topic,
            bootstrap_servers=self._bootstrap_servers,
            group_id=self._group_id,
            value_deserializer=lambda b: json.loads(b.decode("utf-8")),
            auto_offset_reset="earliest",
            enable_auto_commit=True,
        )
        await self._consumer.start()
        self._task = asyncio.create_task(self._consume_loop())
        logger.info("Kafka consumer started (topic=%s, group=%s)", self._topic, self._group_id)

    async def _consume_loop(self) -> None:
        assert self._consumer is not None
        try:
            async for message in self._consumer:
                try:
                    await self._barrier.handle_command(message.value)
                except Exception:  # never let one bad message kill the loop
                    logger.exception("Failed to handle gate command: %s", message.value)
        except asyncio.CancelledError:
            pass

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
        if self._consumer is not None:
            await self._consumer.stop()
            logger.info("Kafka consumer stopped")
