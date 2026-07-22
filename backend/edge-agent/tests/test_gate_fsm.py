"""Tests for gate capture cooldown FSM."""
from __future__ import annotations

import time

from app.services.gate_fsm import GateCaptureFsm


def test_idle_allows_trigger():
    fsm = GateCaptureFsm(cooldown_seconds=8.0)
    assert fsm.can_trigger("GATE_ENTRY_01", "IN") is True
    assert fsm.snapshot("GATE_ENTRY_01", "IN").state == "IDLE"


def test_cooldown_blocks_retrigger():
    fsm = GateCaptureFsm(cooldown_seconds=0.2)
    fsm.mark_published("GATE_ENTRY_01", "IN", "51F-12345")
    assert fsm.can_trigger("GATE_ENTRY_01", "IN") is False
    assert fsm.snapshot("GATE_ENTRY_01", "IN").state == "COOLDOWN"
    time.sleep(0.25)
    assert fsm.can_trigger("GATE_ENTRY_01", "IN") is True


def test_directions_are_independent():
    fsm = GateCaptureFsm(cooldown_seconds=8.0)
    fsm.mark_published("GATE_ENTRY_01", "IN", "51F-12345")
    assert fsm.can_trigger("GATE_EXIT_01", "OUT") is True


def test_plates_recorded_on_snapshot():
    fsm = GateCaptureFsm(cooldown_seconds=8.0)
    fsm.mark_published("GATE_EXIT_01", "OUT", "30A-99999")
    snap = fsm.snapshot("GATE_EXIT_01", "OUT")
    assert snap.last_plate == "30A-99999"
    assert snap.state == "COOLDOWN"
