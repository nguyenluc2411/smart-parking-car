"""ROI occupancy tracker — one OCR burst per vehicle visit, not per timer tick.

State machine:
  EMPTY    → no vehicle in the guide zone; watching for a new arrival
  OCCUPIED → a vehicle was seen and scanned; hold until it leaves the zone
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass
class ZoneUpdate:
    state: str           # EMPTY | OCCUPIED
    action: str          # idle | hold | scan
    leave_streak: int = 0
    leave_streak_need: int = 0


class ZoneOccupancyTracker:
    """Gate zone occupancy — trigger scan once on vehicle entry, not while parked."""

    EMPTY = "EMPTY"
    OCCUPIED = "OCCUPIED"
    TRIGGER_SOURCES = frozenset({"vehicle"})

    def __init__(self, leave_streak_need: int = 4) -> None:
        self._leave_need = max(1, leave_streak_need)
        self._state = self.EMPTY
        self._leave_streak = 0

    @property
    def state(self) -> str:
        return self._state

    @property
    def leave_streak(self) -> int:
        return self._leave_streak

    def reset(self) -> None:
        self._state = self.EMPTY
        self._leave_streak = 0

    def update(self, present: bool, source: str = "") -> ZoneUpdate:
        """Feed one presence probe result; returns whether to run a burst scan."""
        is_vehicle = present and source in self.TRIGGER_SOURCES

        if self._state == self.EMPTY:
            self._leave_streak = 0
            if is_vehicle:
                self._state = self.OCCUPIED
                return ZoneUpdate(self._state, "scan", 0, self._leave_need)
            return ZoneUpdate(self._state, "idle", 0, self._leave_need)

        # OCCUPIED — same vehicle still in view (or plate visible after scan)
        if is_vehicle or (present and source == "plate"):
            self._leave_streak = 0
            return ZoneUpdate(self._state, "hold", 0, self._leave_need)

        self._leave_streak += 1
        if self._leave_streak >= self._leave_need:
            self._state = self.EMPTY
            self._leave_streak = 0
            return ZoneUpdate(self._state, "idle", 0, self._leave_need)

        return ZoneUpdate(
            self._state, "hold", self._leave_streak, self._leave_need)
