"""ALPR pipeline. Modes & detectors:

- ``simulate`` (default): no ML deps; deterministic detection so the whole pipeline can be exercised
  end-to-end. The plate is derived from the image bytes so repeated frames are stable.
- ``real`` + ``alpr_detector=yolo``: YOLOv8 finds the plate region, EasyOCR reads it. Needs a model
  at ALPR_MODEL_PATH (highest accuracy).
- ``real`` + ``alpr_detector=ocr_only``: EasyOCR reads the whole frame and keeps the best VN-plate
  match — works WITHOUT a YOLO model (lower accuracy, fine for early research runs).

Heavy deps (ultralytics/easyocr/cv2) are imported lazily so the base install stays light. The
EasyOCR reader is created once and reused (it is expensive to build).
"""
from __future__ import annotations

import logging
import time
from dataclasses import dataclass

from app import plate as plate_util

logger = logging.getLogger(__name__)

_SIMULATED_PLATES = ["51F-12345", "51G-67890", "30A-11122", "29B-33344", "43A-55566"]

# YOLO can return several plate-like boxes (a second car, a reflection, an on-screen overlay) and
# the clearest plate is often NOT the top-detected one. OCR the top-K boxes and rank by OCR
# confidence; stop early once a box reads confidently enough (keeps latency bounded on CPU).
_YOLO_TOP_K = 3
_OCR_CONF_EARLY_EXIT = 0.85

# VN plates use only digits + a restricted letter set — they NEVER use I, J, O, Q, R, W.
# Constraining EasyOCR to this charset removes the biggest misread sources at the source
# (a plate digit can no longer come back as O/I, and a series letter can't be a look-alike digit-letter).
_OCR_ALLOWLIST = "0123456789ABCDEFGHKLMNPSTUVXYZ"

# Per-character detection confidence floor for the char-detection (yolo_char) path.
_CHAR_MIN_CONF = 0.25

# YOLO plate boxes hug the plate tightly; EasyOCR drops the edge characters when the crop has no
# margin (measured: a tight box truncates the first/last digit, e.g. "61F-32488" -> "61F-3288").
# Expand every plate box by this fraction per side before cropping (clamped to the frame). The
# reported bbox stays the original detection box — only the OCR/char input is padded.
_YOLO_CROP_PAD = 0.15


@dataclass
class Detection:
    plate_number: str
    confidence: float
    bbox: dict[str, int]
    processing_ms: int


@dataclass
class PresenceResult:
    """Lightweight ROI target check — no OCR, no Kafka."""
    present: bool
    confidence: float
    bbox: dict[str, int] | None
    in_roi: bool
    processing_ms: int
    source: str = ""  # plate | vehicle | motion | none


def _box_bounds(box) -> tuple[int, int, int, int]:
    """An EasyOCR box (4 [x, y] points) -> (min_x, min_y, max_x, max_y)."""
    xs = [int(p[0]) for p in box]
    ys = [int(p[1]) for p in box]
    return min(xs), min(ys), max(xs), max(ys)


def _expand_box(x1: int, y1: int, x2: int, y2: int, width: int, height: int,
                pad: float = _YOLO_CROP_PAD) -> tuple[int, int, int, int]:
    """Grow a box by ``pad`` fraction of its size on each side, clamped to the frame.

    Gives EasyOCR / the char model a margin so plate-edge characters aren't truncated by a
    tight YOLO box (see :data:`_YOLO_CROP_PAD`)."""
    dx = int((x2 - x1) * pad)
    dy = int((y2 - y1) * pad)
    return max(0, x1 - dx), max(0, y1 - dy), min(width, x2 + dx), min(height, y2 + dy)


def best_plate_from_ocr(results) -> Detection | None:
    """Pick the best VN plate from EasyOCR ``readtext(detail=1)`` output.

    ``results`` is an iterable of ``(box, text, confidence)``. A plate is often split across
    several boxes (e.g. ``"51F"`` + ``"12345"``), so boxes are sorted into reading order
    (top-to-bottom lines, then left-to-right) and every contiguous window is concatenated and
    run through :func:`plate.canonicalize`. Single-box reads are just the size-1 windows.
    Returns the validating window with the highest mean confidence (``processing_ms`` is set
    by the caller).
    """
    items = [(_box_bounds(box), text, float(conf)) for box, text, conf in results]
    if not items:
        return None
    heights = sorted((b[3] - b[1]) for b, _, _ in items)
    median_h = heights[len(heights) // 2] or 1
    # cluster into lines by vertical band, then read left-to-right within each line
    items.sort(key=lambda it: (round((it[0][1] + it[0][3]) / 2 / median_h), it[0][0]))

    best: tuple[str, float, dict[str, int]] | None = None
    for i in range(len(items)):
        for j in range(i + 1, len(items) + 1):
            window = items[i:j]
            plate = plate_util.canonicalize("".join(w[1] for w in window))
            if not plate_util.is_valid(plate):
                continue
            conf = sum(w[2] for w in window) / len(window)
            if best is None or conf > best[1]:
                x0 = min(w[0][0] for w in window)
                y0 = min(w[0][1] for w in window)
                x1 = max(w[0][2] for w in window)
                y1 = max(w[0][3] for w in window)
                best = (plate, conf, {"x": x0, "y": y0, "w": x1 - x0, "h": y1 - y0})
    if best is None:
        return None
    return Detection(best[0], best[1], best[2], 0)


def assemble_plate_chars(chars) -> str:
    """Join per-character detections (yolo_char) into a plate string in reading order.

    ``chars`` is an iterable of ``(x1, y1, x2, y2, label, conf)``. Characters are clustered into
    lines by their vertical centre (handles a 1- or 2-line plate) — lines top-to-bottom, then each
    line left-to-right — and the labels concatenated. Pure (no ML) so it is cheap to unit-test.
    """
    items = list(chars)
    if not items:
        return ""
    heights = sorted((c[3] - c[1]) for c in items)
    median_h = heights[len(heights) // 2] or 1
    items.sort(key=lambda c: (round(((c[1] + c[3]) / 2) / median_h), c[0]))
    return "".join(str(c[4]) for c in items)


class AlprService:
    def __init__(self, mode: str, model_path: str, model_version: str,
                 detector: str = "yolo", ocr_languages: str = "en", ocr_gpu: bool = False,
                 preprocess: bool = True, grammar_fix: bool = True,
                 char_model_path: str = "/app/models/yolov8_vn_chars.pt",
                 ocr_engine: str = "easyocr", multi_candidate: bool = False) -> None:
        self.mode = mode
        self.detector = detector
        self.model_path = model_path
        self.model_version = model_version
        self.ocr_engine = ocr_engine    # paddle | easyocr — text recognizer (paddle is more accurate
                                         #   AND faster on our plates; see tools/bench_paddle.py)
        self.preprocess = preprocess    # enhance the image (upscale/contrast/denoise) before OCR
        self.multi_candidate = multi_candidate  # OCR raw + (on weak/invalid) enhanced, pick best valid
        self.grammar_fix = grammar_fix  # re-OCR a letter-misread province head digits-only (3->B->8)
        self._ocr_languages = [lang.strip() for lang in ocr_languages.split(",") if lang.strip()]
        self._ocr_gpu = ocr_gpu
        self.char_model_path = char_model_path  # YOLOv8 char-detection model (detector="yolo_char")
        self._model = None       # YOLO plate detector, lazily loaded
        self._char_model = None  # YOLO char detector, lazily loaded
        self._vehicle_model = None  # YOLOv8n COCO — vehicle presence in ROI
        self._reader = None      # EasyOCR reader, lazily loaded + reused
        self._paddle = None      # PaddleOCR reader, lazily loaded + reused

    def _prep(self, image):
        """Return the image to OCR — enhanced (upscale/contrast/denoise) when preprocessing is on."""
        if self.preprocess:
            return self._enhance(image)
        return image

    def _enhance(self, image):
        """Enhanced (upscale/contrast/denoise) variant of a crop/frame for the multi-candidate pass."""
        from app.services import preprocess as _pp  # noqa: WPS433 (lazy: pulls in cv2)
        return _pp.enhance_plate(image)

    def _read(self, image, enhance: bool | None = None):
        """OCR an image (crop or full frame) with the selected engine.

        ``enhance``: ``None`` follows ``self.preprocess`` (back-compat); ``True``/``False`` forces the
        enhanced/raw variant (used by the multi-candidate pass). Returns ``(reads, grammar_ctx)`` where
        ``reads`` is a list of ``(box, text, conf)`` (the EasyOCR ``readtext(detail=1)`` shape, which
        :func:`best_plate_from_ocr` consumes) and ``grammar_ctx`` is ``(reader, ocr_image)`` for the
        EasyOCR head-fix — or ``None`` for paddle (it reads province heads correctly)."""
        use_enh = self.preprocess if enhance is None else enhance
        if self.ocr_engine == "paddle":
            return self._read_paddle(image, use_enh), None
        # EasyOCR: the allowlist constrains it to the VN plate charset (no I/J/O/Q/R/W) so look-alike
        # misreads (O->0, I->1) can't even be emitted.
        reader = self._reader_instance()
        ocr_image = self._enhance(image) if use_enh else image
        reads = reader.readtext(ocr_image, detail=1, allowlist=_OCR_ALLOWLIST)
        return reads, (reader, ocr_image)

    def _read_paddle(self, image, enhance: bool = False):
        """Read with PaddleOCR, adapted to the ``(box, text, conf)`` shape EasyOCR returns.

        PaddleOCR's built-in DBNet text-detection + angle classifier handle the slight skew of
        gate-camera plates well, so we feed the raw BGR crop (enhancing barely helps it —
        measured: paddle_raw == paddle_enh, see tools/bench_paddle.py). When ``enhance`` is set
        (multi-candidate rescue of a weak read) the grayscale enhance is re-expanded to 3 channels."""
        import cv2  # noqa: WPS433 (lazy)
        img = self._enhance(image) if enhance else image
        if getattr(img, "ndim", 3) == 2:
            img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
        result = self._paddle_instance().ocr(img, cls=True)
        lines = result[0] if result and result[0] else []
        return [(ln[0], ln[1][0], float(ln[1][1])) for ln in lines]

    def _ocr_best(self, crop):
        """Multi-candidate OCR for one crop/frame. Returns ``(Detection|None, grammar_ctx, reads)``
        for the CHOSEN variant.

        Reads the RAW image first; only if that yields no valid plate or a low-confidence one does it
        pay for an ENHANCED (upscale/CLAHE/denoise) pass, then combines per measured behaviour:
        - raw confident + valid  -> raw   (clear plates: no preprocessing cost, no regression risk)
        - raw invalid/None       -> enhanced rescue (helps small/dim/blurred/night crops)
        - both valid & AGREE     -> raw, confidence boosted (two independent reads concur)
        - both valid but DIFFER  -> raw   (measured: enhancing regresses more often than it helps)

        With ``multi_candidate`` off it's a single read honouring ``self.preprocess`` (legacy)."""
        if not self.multi_candidate:
            reads, gctx = self._read(crop)
            return (best_plate_from_ocr(reads) if reads else None), gctx, reads

        reads_raw, gctx_raw = self._read(crop, enhance=False)
        cand_raw = best_plate_from_ocr(reads_raw) if reads_raw else None
        # Clear, confident raw read -> done. (Skips the enhanced pass: bounds latency on good frames.)
        if cand_raw is not None and cand_raw.confidence >= _OCR_CONF_EARLY_EXIT:
            return cand_raw, gctx_raw, reads_raw

        reads_enh, gctx_enh = self._read(crop, enhance=True)
        cand_enh = best_plate_from_ocr(reads_enh) if reads_enh else None
        if cand_raw is None:                       # raw produced nothing valid -> enhanced rescue
            if cand_enh is not None:
                return cand_enh, gctx_enh, reads_enh
            return None, gctx_raw, reads_raw
        if cand_enh is not None and cand_enh.plate_number == cand_raw.plate_number:
            cand_raw.confidence = max(cand_raw.confidence,
                                      (cand_raw.confidence + cand_enh.confidence) / 2)
        return cand_raw, gctx_raw, reads_raw       # disagreement -> keep raw (safer)

    def _grammar_head_fix(self, reader, ocr_image, reads, detection):
        """Fix a province head misread as a LETTER (e.g. 3->B->8) by re-OCRing it digits-only.

        VN plate positions 0-1 are always digits, but the general OCR sometimes reads a look-alike
        letter there; `canonicalize` then coerces it to the wrong digit. Only fires when the raw read
        actually had a non-digit in the head (so a correct digit head is never touched), and only
        accepts a confident 2-digit re-read that keeps the plate valid — so it can't corrupt good reads.
        """
        if detection is None or not reads:
            return detection
        raw = "".join(t for _, t, _ in sorted(reads, key=lambda r: r[0][0][0]))
        compact = "".join(ch for ch in raw.upper() if ch.isalnum())
        if len(compact) < 2 or compact[0].isdigit() and compact[1].isdigit():
            return detection  # head already read as digits -> nothing to fix

        box, text, _ = max(reads, key=lambda r: _box_bounds(r[0])[2] - _box_bounds(r[0])[0])
        x0, y0, x1, y1 = _box_bounds(box)
        head_x1 = x0 + int((x1 - x0) * 2.2 / max(len(text), 3))  # left ~2 of N chars = province head
        head_img = ocr_image[max(0, y0):y1, max(0, x0):head_x1]
        if getattr(head_img, "size", 0) == 0:
            return detection
        # the 2-char head is a small slice; upscale it so EasyOCR can resolve the digits reliably.
        import cv2  # noqa: WPS433 (lazy)
        if head_img.shape[1] < 160:
            scale = 160 / head_img.shape[1]
            head_img = cv2.resize(head_img, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)
        head_reads = reader.readtext(head_img, detail=1, allowlist="0123456789")
        confident = [(t, conf) for _, t, conf in head_reads if conf >= 0.6]
        digits = "".join(ch for t, _ in confident for ch in t if ch.isdigit())
        if len(digits) >= 2:
            fixed = digits[:2] + detection.plate_number[2:]
            if fixed != detection.plate_number and plate_util.is_valid(fixed):
                # The body read can score low on the very plate whose head was misread; a confident
                # digits-only head re-read is strong evidence, so lift confidence toward it (mean) —
                # otherwise a correctly-recovered plate can still be dropped by CONFIDENCE_THRESHOLD.
                head_conf = sum(c for _, c in confident) / len(confident)
                confidence = max(detection.confidence, (detection.confidence + head_conf) / 2)
                logger.info("Grammar head-fix: %s -> %s (conf %.2f -> %.2f)",
                            detection.plate_number, fixed, detection.confidence, confidence)
                return Detection(fixed, confidence, detection.bbox, detection.processing_ms)
        return detection

    def warmup(self) -> None:
        """Eagerly load the detector model AND the OCR engine so the first real request is fast and
        any init failure surfaces at STARTUP (and via /health) instead of on the first car at the gate.

        This is the guard that makes "PaddleOCR must work" enforceable at runtime: if paddle can't
        load in the running image (missing libgomp1, un-downloaded models, etc.) this RAISES at boot
        — loud and early — rather than silently degrading every detection to a 422. No-op outside
        ``real`` mode (simulate needs no models)."""
        if self.mode != "real":
            return
        import numpy as np  # noqa: WPS433 (lazy)
        if self.detector in ("yolo", "yolo_char"):
            self._load_model()
        if self.detector == "yolo_char":
            self._load_char_model()
        # Force the OCR engine to initialise on a tiny blank image (paddle: triggers model load;
        # easyocr: builds the reader). Raises here if the engine is unavailable.
        self._read(np.full((48, 160, 3), 255, np.uint8))
        logger.info("ALPR warmup complete (detector=%s, ocr_engine=%s)", self.detector, self.ocr_engine)

    def detect(self, image_bytes: bytes) -> Detection | None:
        """Run ALPR on a frame. Returns ``None`` when nothing readable/valid is found."""
        if self.mode != "real":
            return self._detect_simulated(image_bytes)
        try:
            return self._detect_real(image_bytes)
        except Exception:  # never let an ALPR failure crash the request
            logger.exception("Real ALPR failed")
            return None

    def detect_presence(
        self,
        image_bytes: bytes,
        *,
        roi_x: float = 0.0,
        roi_y: float = 0.0,
        roi_w: float = 1.0,
        roi_h: float = 1.0,
        min_confidence: float = 0.15,
        vehicle_only: bool = True,
    ) -> PresenceResult:
        """Fast ROI probe: vehicle YOLO (primary) → plate YOLO if allowed. No motion heuristic."""
        start = time.perf_counter()
        if not image_bytes:
            return PresenceResult(False, 0.0, None, False, 0, "none")

        import numpy as np  # noqa: WPS433
        import cv2  # noqa: WPS433

        frame = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
        if frame is None:
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")

        if self.mode != "real":
            if vehicle_only:
                ms = int((time.perf_counter() - start) * 1000)
                return PresenceResult(False, 0.0, None, False, ms, "none")
            result = self._presence_variance(frame, roi_x, roi_y, roi_w, roi_h, start)
            result.processing_ms = int((time.perf_counter() - start) * 1000)
            return result

        try:
            if self.detector == "ocr_only":
                ms = int((time.perf_counter() - start) * 1000)
                return PresenceResult(False, 0.0, None, False, ms, "none")

            vehicle = self._presence_vehicle_yolo(
                frame, roi_x, roi_y, roi_w, roi_h, 0.25, start)
            if vehicle.present:
                return vehicle
            if not vehicle_only:
                plate = self._presence_plate_yolo(
                    frame, roi_x, roi_y, roi_w, roi_h, min_confidence, start)
                if plate.present:
                    return plate
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")
        except Exception:
            logger.exception("Presence detection failed")
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")

    @staticmethod
    def _box_overlaps_roi(x1: int, y1: int, x2: int, y2: int, img_w: int, img_h: int,
                          roi_x: float, roi_y: float, roi_w: float, roi_h: float,
                          min_overlap: float = 0.08) -> bool:
        """True when at least ``min_overlap`` of the box area lies inside the normalized ROI."""
        if img_w <= 0 or img_h <= 0:
            return False
        bx1, by1 = x1 / img_w, y1 / img_h
        bx2, by2 = x2 / img_w, y2 / img_h
        rx1, ry1 = roi_x, roi_y
        rx2, ry2 = roi_x + roi_w, roi_y + roi_h
        ix1, iy1 = max(bx1, rx1), max(by1, ry1)
        ix2, iy2 = min(bx2, rx2), min(by2, ry2)
        if ix2 <= ix1 or iy2 <= iy1:
            return False
        inter = (ix2 - ix1) * (iy2 - iy1)
        box_area = max((bx2 - bx1) * (by2 - by1), 1e-9)
        return (inter / box_area) >= min_overlap

    @staticmethod
    def _center_in_roi(cx: float, cy: float, img_w: int, img_h: int,
                       roi_x: float, roi_y: float, roi_w: float, roi_h: float) -> bool:
        nx, ny = cx / max(img_w, 1), cy / max(img_h, 1)
        return roi_x <= nx <= roi_x + roi_w and roi_y <= ny <= roi_y + roi_h

    def _presence_plate_yolo(self, frame, roi_x: float, roi_y: float,
                            roi_w: float, roi_h: float, min_confidence: float,
                            start: float) -> PresenceResult:
        model = self._load_model()
        results = model(frame, verbose=False)
        boxes = results[0].boxes if results else None
        h, w = frame.shape[:2]
        if boxes is None or len(boxes) == 0:
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")

        best_conf = 0.0
        best_bbox: dict[str, int] | None = None
        for box in boxes:
            conf = float(box.conf[0])
            if conf < min_confidence:
                continue
            x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
            cx, cy = (x1 + x2) / 2, (y1 + y2) / 2
            in_roi = (
                self._box_overlaps_roi(x1, y1, x2, y2, w, h, roi_x, roi_y, roi_w, roi_h)
                or self._center_in_roi(cx, cy, w, h, roi_x, roi_y, roi_w, roi_h)
            )
            if not in_roi:
                continue
            if conf > best_conf:
                best_conf = conf
                best_bbox = {"x": x1, "y": y1, "w": x2 - x1, "h": y2 - y1}

        ms = int((time.perf_counter() - start) * 1000)
        if best_bbox is None:
            return PresenceResult(False, 0.0, None, False, ms, "none")
        return PresenceResult(True, round(best_conf, 4), best_bbox, True, ms, "plate")

    _VEHICLE_CLASSES = frozenset({2, 3, 5, 7})  # COCO: car, motorcycle, bus, truck

    def _load_vehicle_model(self):
        if self._vehicle_model is None:
            from ultralytics import YOLO  # noqa: WPS433
            self._vehicle_model = YOLO("yolov8n.pt")
        return self._vehicle_model

    def _presence_vehicle_yolo(self, frame, roi_x: float, roi_y: float,
                               roi_w: float, roi_h: float, min_confidence: float,
                               start: float) -> PresenceResult:
        """Detect car/motorcycle/bus/truck overlapping the guide ROI."""
        model = self._load_vehicle_model()
        results = model(frame, verbose=False, imgsz=416, classes=list(self._VEHICLE_CLASSES))
        boxes = results[0].boxes if results else None
        h, w = frame.shape[:2]
        if boxes is None or len(boxes) == 0:
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")

        best_conf = 0.0
        best_bbox: dict[str, int] | None = None
        for box in boxes:
            conf = float(box.conf[0])
            if conf < min_confidence:
                continue
            x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
            if not self._box_overlaps_roi(x1, y1, x2, y2, w, h, roi_x, roi_y, roi_w, roi_h, 0.15):
                continue
            if conf > best_conf:
                best_conf = conf
                best_bbox = {"x": x1, "y": y1, "w": x2 - x1, "h": y2 - y1}

        ms = int((time.perf_counter() - start) * 1000)
        if best_bbox is None:
            return PresenceResult(False, 0.0, None, False, ms, "none")
        return PresenceResult(True, round(best_conf, 4), best_bbox, True, ms, "vehicle")

    def _presence_yolo(self, image_bytes: bytes, roi_x: float, roi_y: float,
                       roi_w: float, roi_h: float, min_confidence: float,
                       start: float) -> PresenceResult:
        import numpy as np  # noqa: WPS433
        import cv2  # noqa: WPS433

        frame = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
        if frame is None:
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")

        if self.detector == "ocr_only":
            return self._presence_variance(frame, roi_x, roi_y, roi_w, roi_h, start)

        return self._presence_plate_yolo(frame, roi_x, roi_y, roi_w, roi_h, min_confidence, start)

    def _presence_variance(self, frame, roi_x, roi_y, roi_w, roi_h, start) -> PresenceResult:
        """Fallback when YOLO is unavailable: ROI edge/sharpness heuristic (cheap, no OCR)."""
        import cv2  # noqa: WPS433

        h, w = frame.shape[:2]
        x1 = int(w * roi_x)
        y1 = int(h * roi_y)
        x2 = int(w * (roi_x + roi_w))
        y2 = int(h * (roi_y + roi_h))
        crop = frame[y1:y2, x1:x2]
        if crop.size == 0:
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")
        gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        variance = float(cv2.Laplacian(gray, cv2.CV_64F).var())
        mean_brightness = float(gray.mean())
        # Empty/blur ROI ~ variance < 60; plate/vehicle detail typically >> 100
        present = variance >= 70.0 and 25.0 <= mean_brightness <= 245.0
        conf = min(0.85, variance / 350.0) if present else 0.0
        ms = int((time.perf_counter() - start) * 1000)
        source = "motion" if present else "none"
        return PresenceResult(present, round(conf, 4), None, present, ms, source)

    def _presence_simulated(self, image_bytes, roi_x, roi_y, roi_w, roi_h, start) -> PresenceResult:
        import numpy as np  # noqa: WPS433
        import cv2  # noqa: WPS433

        frame = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
        if frame is None:
            ms = int((time.perf_counter() - start) * 1000)
            return PresenceResult(False, 0.0, None, False, ms, "none")
        result = self._presence_variance(frame, roi_x, roi_y, roi_w, roi_h, start)
        result.processing_ms = int((time.perf_counter() - start) * 1000)
        return result

    # ----------------------------------------------------------------- simulate
    def _detect_simulated(self, image_bytes: bytes) -> Detection | None:
        start = time.perf_counter()
        if not image_bytes:
            return None
        plate = _SIMULATED_PLATES[sum(image_bytes) % len(_SIMULATED_PLATES)]
        confidence = round(0.88 + (len(image_bytes) % 11) / 100.0, 4)  # 0.88–0.98 band
        processing_ms = int((time.perf_counter() - start) * 1000)
        return Detection(plate, confidence, {"x": 120, "y": 80, "w": 340, "h": 90}, processing_ms)

    # --------------------------------------------------------------------- real
    def _detect_real(self, image_bytes: bytes) -> Detection | None:
        start = time.perf_counter()
        import numpy as np  # noqa: WPS433 (lazy)
        import cv2  # noqa: WPS433

        frame = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
        if frame is None:
            return None

        if self.detector == "ocr_only":
            detection = self._detect_ocr_only(frame)
        elif self.detector == "yolo_char":
            detection = self._detect_char_yolo(frame)
            if detection is None:  # char model unavailable / nothing valid -> EasyOCR fallback
                logger.info("yolo_char produced no plate; falling back to EasyOCR")
                detection = self._detect_yolo(frame)
        else:
            detection = self._detect_yolo(frame)
            if detection is None:  # YOLO located/read nothing -> slow but worth a full-frame retry
                logger.info("YOLO path produced no plate; falling back to full-frame OCR")
                detection = self._detect_ocr_only(frame)
        if detection is None:
            return None
        detection.processing_ms = int((time.perf_counter() - start) * 1000)
        return detection

    def _detect_yolo(self, frame) -> Detection | None:
        """Locate plate regions with YOLO, then OCR the top-K boxes and return the VALID plate
        with the highest OCR confidence.

        YOLO's detection confidence only says a region looks like a plate, not that OCR read it
        right — and the clearest plate is often not the top-detected box (a reflection, a second
        car or an on-screen overlay can score higher). Ranking the readable candidates by OCR
        confidence picks the actually-legible plate and yields a confidence that reflects read
        quality, so CONFIDENCE_THRESHOLD can reject misreads instead of waving them through.
        """
        model = self._load_model()
        results = model(frame, verbose=False)
        boxes = results[0].boxes if results else None
        if boxes is None or len(boxes) == 0:
            return None

        ranked = sorted(boxes, key=lambda b: float(b.conf[0]), reverse=True)[:_YOLO_TOP_K]
        best: Detection | None = None
        h, w = frame.shape[:2]
        for box in ranked:
            x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
            px1, py1, px2, py2 = _expand_box(x1, y1, x2, y2, w, h)  # pad so OCR keeps edge chars
            # Multi-candidate OCR (raw, + enhanced rescue on a weak/invalid read). The chosen variant's
            # reads/gctx come back so the grammar head-fix below runs against the right image.
            sub, gctx, reads = self._ocr_best(frame[py1:py2, px1:px2])
            if sub is None:
                continue
            plate, ocr_conf = sub.plate_number, sub.confidence
            if best is None or ocr_conf > best.confidence:
                detection = Detection(plate, ocr_conf,
                                      {"x": x1, "y": y1, "w": x2 - x1, "h": y2 - y1}, 0)
                if self.grammar_fix and gctx is not None:
                    detection = self._grammar_head_fix(gctx[0], gctx[1], reads, detection)
                best = detection
            if ocr_conf >= _OCR_CONF_EARLY_EXIT:
                break
        return best

    def _detect_ocr_only(self, frame) -> Detection | None:
        # Engine reads the whole frame (multi-candidate: raw + enhanced rescue on a weak read).
        detection, gctx, reads = self._ocr_best(frame)
        if self.grammar_fix and gctx is not None and detection is not None:
            detection = self._grammar_head_fix(gctx[0], gctx[1], reads, detection)
        return detection

    # ------------------------------------------------------------- char detection
    def _detect_char_yolo(self, frame) -> Detection | None:
        """Two-stage YOLO: plate detector crops the plate, a char-detection model classifies every
        character. Most accurate for VN plates — each char is an object constrained to the plate
        charset, so sequence-OCR look-alike confusions (e.g. 3<->8) largely disappear. Returns the
        highest-confidence VALID plate across the top-K plate boxes."""
        plate_model = self._load_model()
        results = plate_model(frame, verbose=False)
        boxes = results[0].boxes if results else None
        if boxes is None or len(boxes) == 0:
            return None
        char_model = self._load_char_model()
        ranked = sorted(boxes, key=lambda b: float(b.conf[0]), reverse=True)[:_YOLO_TOP_K]
        h, w = frame.shape[:2]
        best: Detection | None = None
        for box in ranked:
            x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
            px1, py1, px2, py2 = _expand_box(x1, y1, x2, y2, w, h)  # pad so edge chars aren't cut
            det = self._read_chars(char_model, frame[py1:py2, px1:px2],
                                   {"x": x1, "y": y1, "w": x2 - x1, "h": y2 - y1})
            if det is not None and (best is None or det.confidence > best.confidence):
                best = det
            if det is not None and det.confidence >= _OCR_CONF_EARLY_EXIT:
                break
        return best

    def _read_chars(self, char_model, crop, bbox) -> Detection | None:
        """Run the char-detection model on a plate crop, assemble the plate, validate it."""
        if getattr(crop, "size", 0) == 0:
            return None
        results = char_model(crop, verbose=False)
        if not results:
            return None
        cboxes = results[0].boxes
        names = results[0].names or {}
        if cboxes is None or len(cboxes) == 0:
            return None
        chars = []
        for b in cboxes:
            conf = float(b.conf[0])
            if conf < _CHAR_MIN_CONF:
                continue
            label = str(names.get(int(b.cls[0]), "")).upper()
            if len(label) != 1 or not label.isalnum():
                continue  # skip non-character classes (e.g. a "license plate" box) and blanks
            cx1, cy1, cx2, cy2 = (float(v) for v in b.xyxy[0])
            chars.append((cx1, cy1, cx2, cy2, label, conf))
        if len(chars) < 4:  # a VN plate has >= 6 chars; too few -> unreliable
            return None
        plate = plate_util.canonicalize(assemble_plate_chars(chars))
        if not plate_util.is_valid(plate):
            return None
        confidence = sum(c[5] for c in chars) / len(chars)
        return Detection(plate, confidence, bbox, 0)

    # ------------------------------------------------------------------- models
    def _load_model(self):
        if self._model is None:
            from ultralytics import YOLO  # noqa: WPS433 (lazy heavy import)
            logger.info("Loading YOLO model from %s", self.model_path)
            self._model = YOLO(self.model_path)
        return self._model

    def _load_char_model(self):
        if self._char_model is None:
            from ultralytics import YOLO  # noqa: WPS433 (lazy heavy import)
            logger.info("Loading char model from %s", self.char_model_path)
            self._char_model = YOLO(self.char_model_path)
        return self._char_model

    def _reader_instance(self):
        if self._reader is None:
            import easyocr  # noqa: WPS433 (lazy heavy import)
            logger.info("Initialising EasyOCR reader (langs=%s, gpu=%s)",
                        self._ocr_languages, self._ocr_gpu)
            self._reader = easyocr.Reader(self._ocr_languages, gpu=self._ocr_gpu)
        return self._reader

    def _paddle_instance(self):
        if self._paddle is None:
            # Windows: paddle and torch clash on DLL load unless torch is imported FIRST. The YOLO
            # detector already pulls torch in before this runs, but force the order for the
            # ocr_only+paddle path too (no-op where torch isn't installed). See tools/bench_paddle.py.
            try:
                import torch  # noqa: F401, WPS433
            except Exception:  # torch absent -> no clash to avoid
                pass
            import os  # noqa: WPS433
            os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")
            from paddleocr import PaddleOCR  # noqa: WPS433 (lazy heavy import)
            logger.info("Initialising PaddleOCR (lang=en, angle_cls=True)")
            self._paddle = PaddleOCR(use_angle_cls=True, lang="en", show_log=False)
        return self._paddle
