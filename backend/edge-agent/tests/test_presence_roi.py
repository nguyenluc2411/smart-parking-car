"""Unit tests for ROI overlap helper used by presence detection."""
from app.services.alpr import AlprService


def test_box_overlaps_roi_when_plate_inside_guide():
    assert AlprService._box_overlaps_roi(
        80, 380, 320, 460, 1000, 1000,
        roi_x=0.08, roi_y=0.38, roi_w=0.84, roi_h=0.24,
    )


def test_box_overlaps_roi_rejects_outside():
    assert not AlprService._box_overlaps_roi(
        10, 10, 50, 50, 1000, 1000,
        roi_x=0.08, roi_y=0.38, roi_w=0.84, roi_h=0.24,
    )
