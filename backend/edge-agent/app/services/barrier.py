"""Simulated barrier: reacts to parking.gate.command and auto-closes (BR-006-1/2).

The physical relay is replaced by an in-memory state machine + asyncio timers, so the whole
entry/exit flow can be observed end-to-end (logs + the edge_gate_state metric) without hardware.
"""
from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable, Iterable

from app.metrics import gate_commands_total, gate_state

logger = logging.getLogger(__name__)

# (gate_id, status, reason) -> publish parking.gate.state. Async so it can hit Kafka.
StateCallback = Callable[[str, str, str], Awaitable[None]]

# Barrier states
OPEN = "OPEN"
CLOSED = "CLOSED"

# Commands carried by parking.gate.command (GateCommand enum)
CMD_OPEN = "OPEN"
CMD_CLOSE = "CLOSE"


class BarrierSimulator:
    def __init__(self, auto_close_seconds: int,
                 on_state_change: StateCallback | None = None) -> None:
        self._auto_close_seconds = auto_close_seconds
        self._on_state_change = on_state_change
        self._states: dict[str, str] = {}
        self._close_tasks: dict[str, asyncio.Task] = {}

    async def announce_initial(self, gate_ids: Iterable[str]) -> None:
        """On startup the physical relay is de-energized → every gate is CLOSED. Publish it so
        parking-service does not stay stuck on a stale OPEN from before the restart (BR-006-2)."""
        for gate_id in gate_ids:
            self._states[gate_id] = CLOSED
            gate_state.labels(gate_id=gate_id).set(0)
            await self._emit(gate_id, CLOSED, reason="startup")

    def state_of(self, gate_id: str) -> str:
        return self._states.get(gate_id, CLOSED)

    def snapshot(self) -> dict[str, str]:
        return dict(self._states)

    async def handle_command(self, command: dict) -> None:
        """Apply a parking.gate.command payload {gateId, command, sessionId, ...}."""
        gate_id = command.get("gateId")
        action = command.get("command")
        if not gate_id or action not in (CMD_OPEN, CMD_CLOSE):
            logger.warning("Ignoring malformed gate command: %s", command)
            return

        gate_commands_total.labels(command=action).inc()
        if action == CMD_OPEN:
            await self._open(gate_id, command.get("sessionId"))
        else:
            await self._close(gate_id, reason="command")

    async def _open(self, gate_id: str, session_id: str | None) -> None:
        self._states[gate_id] = OPEN
        gate_state.labels(gate_id=gate_id).set(1)
        logger.info("🚧 GATE %s OPEN (session=%s) — auto-close in %ss",
                    gate_id, session_id, self._auto_close_seconds)
        await self._emit(gate_id, OPEN, reason="command")
        # BR-006-2: restart the auto-close timer on each OPEN.
        self._cancel_timer(gate_id)
        self._close_tasks[gate_id] = asyncio.create_task(self._auto_close(gate_id))

    async def _auto_close(self, gate_id: str) -> None:
        try:
            await asyncio.sleep(self._auto_close_seconds)
            await self._close(gate_id, reason="auto")
        except asyncio.CancelledError:  # superseded by a newer OPEN
            pass

    async def _close(self, gate_id: str, reason: str) -> None:
        self._cancel_timer(gate_id)
        self._states[gate_id] = CLOSED
        gate_state.labels(gate_id=gate_id).set(0)
        logger.info("🚧 GATE %s CLOSED (%s)", gate_id, reason)
        await self._emit(gate_id, CLOSED, reason=reason)

    async def _emit(self, gate_id: str, status: str, reason: str) -> None:
        """Publish a state change; never let a Kafka hiccup break the barrier state machine."""
        if self._on_state_change is None:
            return
        try:
            await self._on_state_change(gate_id, status, reason)
        except Exception:  # noqa: BLE001
            logger.exception("Failed to publish parking.gate.state for %s (%s)", gate_id, status)

    def _cancel_timer(self, gate_id: str) -> None:
        task = self._close_tasks.pop(gate_id, None)
        if task is not None and not task.done():
            task.cancel()

    async def shutdown(self) -> None:
        for task in list(self._close_tasks.values()):
            if not task.done():
                task.cancel()
        self._close_tasks.clear()
