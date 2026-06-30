"""Image preprocessing for ALPR OCR.

Small, low-contrast or noisy plate crops are the main cause of OCR misreads (e.g. ``3`` read as
``8`` when the thin stroke that separates them is lost). Upscaling small crops so those strokes are
resolvable, lifting local contrast (CLAHE) for glare/shadow, and a light denoise markedly improve
character separation before EasyOCR. EasyOCR accepts a single-channel (grayscale) array directly.

Pure cv2/numpy and imported lazily (cv2 only on first call) so the base install stays light.
"""
from __future__ import annotations

# EasyOCR resolves small text poorly; upscale crops narrower than this (px) so strokes survive.
_MIN_OCR_WIDTH = 200
_MAX_SCALE = 4.0


def enhance_plate(image):
    """Return an OCR-friendly grayscale version of a plate crop (or full frame).

    Steps: grayscale -> upscale (cubic) if the crop is narrow -> CLAHE local contrast ->
    light bilateral denoise. Returns the input unchanged for an empty/None image. A wide
    image (e.g. a full frame) skips the upscale, so this is safe to call on either a crop or
    the whole frame.
    """
    import cv2  # noqa: WPS433 (lazy heavy import)

    if image is None or getattr(image, "size", 0) == 0:
        return image

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if image.ndim == 3 else image
    h, w = gray.shape[:2]
    if 0 < w < _MIN_OCR_WIDTH:
        scale = min(_MAX_SCALE, _MIN_OCR_WIDTH / w)
        gray = cv2.resize(gray, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    gray = clahe.apply(gray)
    # mild edge-preserving denoise to tame sensor noise that CLAHE amplifies (sharpening was tried
    # and made it WORSE — it over-amplifies strokes and creates new look-alike confusions like 5/6).
    gray = cv2.bilateralFilter(gray, d=5, sigmaColor=50, sigmaSpace=50)
    return gray
