"""Render the detected plate onto the stored frame.

The raw full-vehicle frame has the plate as a tiny, often unreadable region — an operator/admin
looking at the entry/exit photo in the dashboard "mất biển" (can't make out the plate). This module
produces two operator-friendly artifacts from the SAME frame + detection box:

1. ``annotate_and_crop`` returns the full frame with a box around the plate AND the recognized plate
   text printed large next to it (so the photo is self-explanatory: where the plate is + what the
   system read), PLUS a padded, upscaled close-up crop of just the plate (legible on its own).

Pure cv2/numpy, imported lazily so the base (simulate) install stays light.
"""
from __future__ import annotations

import logging

logger = logging.getLogger(__name__)

_BOX_COLOR = (0, 0, 255)        # red box (BGR)
_TEXT_COLOR = (255, 255, 255)   # white text on a red plate
_TEXT_BG = (0, 0, 255)
_CROP_PAD = 0.25                # extra margin around the plate box on the close-up crop
_CROP_MIN_WIDTH = 400           # upscale narrower crops so the plate is legible in the UI


def _encode(frame):
    import cv2  # noqa: WPS433 (lazy)
    ok, buf = cv2.imencode(".jpg", frame)
    return buf.tobytes() if ok else None


def annotate_and_crop(image_bytes: bytes, bbox: dict, plate_text: str,
                      confidence: float | None = None) -> tuple[bytes | None, bytes | None]:
    """Decode the frame once and return ``(annotated_full_jpeg, plate_crop_jpeg)``.

    Either element is ``None`` if it can't be produced (undecodable image, empty crop, encode
    failure); callers fall back to the raw frame in that case. ``bbox`` is ``{x, y, w, h}`` in the
    ORIGINAL frame's pixel coordinates (as returned by :class:`app.services.alpr.Detection`).
    """
    import cv2  # noqa: WPS433 (lazy heavy import)
    import numpy as np  # noqa: WPS433

    frame = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
    if frame is None:
        return None, None
    h, w = frame.shape[:2]
    x, y = max(0, int(bbox.get("x", 0))), max(0, int(bbox.get("y", 0)))
    bw, bh = int(bbox.get("w", 0)), int(bbox.get("h", 0))
    x2, y2 = min(w, x + bw), min(h, y + bh)

    # --- close-up crop (padded + upscaled) ---
    crop_bytes = None
    dx, dy = int(bw * _CROP_PAD), int(bh * _CROP_PAD)
    cx1, cy1 = max(0, x - dx), max(0, y - dy)
    cx2, cy2 = min(w, x2 + dx), min(h, y2 + dy)
    crop = frame[cy1:cy2, cx1:cx2]
    if crop.size > 0:
        if 0 < crop.shape[1] < _CROP_MIN_WIDTH:
            scale = _CROP_MIN_WIDTH / crop.shape[1]
            crop = cv2.resize(crop, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
        crop_bytes = _encode(crop)

    # --- annotated full frame (box + recognized text) ---
    thick = max(2, round(w / 400))
    cv2.rectangle(frame, (x, y), (x2, y2), _BOX_COLOR, thick)
    label = plate_text + (f"  {confidence * 100:.0f}%" if confidence is not None else "")
    scale = max(0.8, w / 1200)
    ft = max(1, round(scale * 2))
    (tw, th), base = cv2.getTextSize(label, cv2.FONT_HERSHEY_SIMPLEX, scale, ft)
    ty = y - 10 if (y - 10 - th) > 0 else y2 + th + 12   # label above the box, or below if no room
    cv2.rectangle(frame, (x, ty - th - base), (min(w, x + tw), ty + base), _TEXT_BG, -1)
    cv2.putText(frame, label, (x, ty), cv2.FONT_HERSHEY_SIMPLEX, scale, _TEXT_COLOR, ft, cv2.LINE_AA)
    return _encode(frame), crop_bytes
