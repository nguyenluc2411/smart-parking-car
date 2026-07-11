"""Tests for ROI occupancy — one scan per vehicle visit."""
from app.services.zone_tracker import ZoneOccupancyTracker


def test_empty_waits_for_vehicle():
    t = ZoneOccupancyTracker(leave_streak_need=3)
    u = t.update(False, "none")
    assert u.action == "idle" and u.state == "EMPTY"


def test_vehicle_entry_triggers_single_scan():
    t = ZoneOccupancyTracker(leave_streak_need=3)
    u = t.update(True, "vehicle")
    assert u.action == "scan" and u.state == "OCCUPIED"


def test_same_vehicle_does_not_rescan():
    t = ZoneOccupancyTracker(leave_streak_need=3)
    t.update(True, "vehicle")
    u = t.update(True, "vehicle")
    assert u.action == "hold" and u.state == "OCCUPIED"


def test_motion_does_not_trigger_scan():
    t = ZoneOccupancyTracker(leave_streak_need=3)
    u = t.update(True, "motion")
    assert u.action == "idle" and u.state == "EMPTY"


def test_vehicle_leave_allows_next_scan():
    t = ZoneOccupancyTracker(leave_streak_need=2)
    t.update(True, "vehicle")
    t.update(False, "none")
    u = t.update(False, "none")
    assert u.state == "EMPTY"
    u2 = t.update(True, "vehicle")
    assert u2.action == "scan"
