"""Per-gate capture cooldown — prevents duplicate plate.detected while a car sits in view.

Phase 1: simple time-based cooldown after a successful publish (default 8s, same as
``tools/camera_agent.py``). Does NOT replace parking-service exit-dedup (30s) or
BR-002-4 duplicate ACTIVE checks — those remain the authority on the backend.
"""
from __future__ import annotations

import time
from dataclasses import dataclass, field


@dataclass
class GateFsmSnapshot:
    gate_id: str
    direction: str
    state: str  # IDLE | COOLDOWN
    cooldown_until: float | None = None
    last_plate: str | None = None
    last_published_at: float | None = None


class GateCaptureFsm:
    """One cooldown window per (gate_id, direction) after each successful publish."""

    def __init__(self, cooldown_seconds: float = 8.0) -> None:
        self._cooldown = max(0.0, cooldown_seconds)
        self._until: dict[tuple[str, str], float] = {}
        self._last_plate: dict[tuple[str, str], str] = {}
        self._last_at: dict[tuple[str, str], float] = {}

    @property
    def cooldown_seconds(self) -> float:
        return self._cooldown

    def can_trigger(self, gate_id: str, direction: str) -> bool:
        return time.monotonic() >= self._until.get((gate_id, direction), 0.0)

    def cooldown_remaining(self, gate_id: str, direction: str) -> float:
        rem = self._until.get((gate_id, direction), 0.0) - time.monotonic()
        return max(0.0, rem)

    def mark_published(self, gate_id: str, direction: str, plate: str) -> None:
        key = (gate_id, direction)
        now = time.monotonic()
        self._until[key] = now + self._cooldown
        self._last_plate[key] = plate
        self._last_at[key] = now

    def snapshot(self, gate_id: str, direction: str) -> GateFsmSnapshot:
        key = (gate_id, direction)
        rem = self.cooldown_remaining(gate_id, direction)
        return GateFsmSnapshot(
            gate_id=gate_id,
            direction=direction,
            state="COOLDOWN" if rem > 0 else "IDLE",
            cooldown_until=rem if rem > 0 else None,
            last_plate=self._last_plate.get(key),
            last_published_at=self._last_at.get(key),
        )

    def all_snapshots(self) -> list[GateFsmSnapshot]:
        keys = set(self._until) | set(self._last_plate) | set(self._last_at)
        return [self.snapshot(g, d) for g, d in sorted(keys)]
