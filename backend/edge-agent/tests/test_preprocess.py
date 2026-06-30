"""Tests for app.services.preprocess.enhance_plate (pure cv2/numpy)."""
import numpy as np

from app.services.preprocess import enhance_plate


def test_small_crop_is_upscaled_to_min_width():
    crop = np.full((30, 80, 3), 127, dtype=np.uint8)  # 80px wide < 200 -> must grow
    out = enhance_plate(crop)
    assert out.ndim == 2                  # grayscale single channel for EasyOCR
    assert out.shape[1] >= 200            # upscaled to at least the min OCR width
    assert out.dtype == np.uint8


def test_wide_frame_is_not_upscaled():
    frame = np.full((200, 640, 3), 100, dtype=np.uint8)  # already wider than min width
    out = enhance_plate(frame)
    assert out.ndim == 2
    assert out.shape == (200, 640)        # height/width unchanged, only contrast/denoise applied


def test_empty_image_returned_unchanged():
    empty = np.zeros((0, 0, 3), dtype=np.uint8)
    assert enhance_plate(empty).size == 0
    assert enhance_plate(None) is None


def test_grayscale_input_is_accepted():
    gray = np.full((40, 300), 90, dtype=np.uint8)  # already single channel
    out = enhance_plate(gray)
    assert out.ndim == 2
    assert out.shape == (40, 300)
