"""Tests for app.services.annotate — drawing the plate onto the stored frame."""
import cv2
import numpy as np

from app.services.annotate import annotate_and_crop


def _jpeg(width=400, height=300):
    ok, buf = cv2.imencode(".jpg", np.full((height, width, 3), 255, np.uint8))
    return buf.tobytes()


def _decode(b):
    return cv2.imdecode(np.frombuffer(b, np.uint8), cv2.IMREAD_COLOR)


def test_returns_full_and_crop():
    full, crop = annotate_and_crop(_jpeg(400, 300), {"x": 120, "y": 100, "w": 150, "h": 60},
                                   "51F-12345", 0.91)
    assert full is not None and crop is not None
    # the annotated full keeps the original frame size; the crop is non-empty
    full_img, crop_img = _decode(full), _decode(crop)
    assert full_img.shape[:2] == (300, 400)
    assert crop_img.size > 0


def test_small_crop_is_upscaled_for_legibility():
    # a narrow plate box (< _CROP_MIN_WIDTH) must be upscaled so it's readable in the UI
    _, crop = annotate_and_crop(_jpeg(400, 300), {"x": 50, "y": 50, "w": 40, "h": 20},
                                "51F-12345", 0.9)
    assert crop is not None
    assert _decode(crop).shape[1] >= 400


def test_box_near_top_keeps_label_in_frame():
    # box at y=0 has no room above -> label must be placed below without crashing/None
    full, crop = annotate_and_crop(_jpeg(400, 300), {"x": 10, "y": 0, "w": 120, "h": 50},
                                   "30A-00001", 1.0)
    assert full is not None and crop is not None


def test_undecodable_bytes_return_none():
    full, crop = annotate_and_crop(b"not-a-jpeg", {"x": 0, "y": 0, "w": 10, "h": 10}, "51F-12345")
    assert full is None and crop is None
