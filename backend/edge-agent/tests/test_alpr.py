from pytest import approx

from app import plate
from app.services.alpr import AlprService, best_plate_from_ocr


def _box(x0, y0, x1, y1):
    return [[x0, y0], [x1, y0], [x1, y1], [x0, y1]]


def test_simulate_returns_valid_plate_above_threshold():
    service = AlprService(mode="simulate", model_path="x", model_version="v")
    detection = service.detect(b"some-frame-bytes")

    assert detection is not None
    assert plate.is_valid(detection.plate_number)
    assert detection.confidence >= 0.85
    assert {"x", "y", "w", "h"} <= detection.bbox.keys()


def test_simulate_empty_frame_returns_none():
    service = AlprService(mode="simulate", model_path="x", model_version="v")
    assert service.detect(b"") is None


# --- multi-candidate OCR (_ocr_best): stub _read so raw/enhanced reads are controllable --------
def _reads(text, conf):
    """One EasyOCR-shaped read whose text canonicalises to a plate (or [] for an unreadable frame)."""
    return [] if text is None else [(_box(0, 0, 100, 30), text, conf)]


def _stub_service(raw, enh):
    """AlprService whose _read returns ``raw`` (enhance=False) / ``enh`` (enhance=True). gctx=None."""
    svc = AlprService(mode="real", model_path="x", model_version="v",
                      ocr_engine="paddle", multi_candidate=True)
    svc._read = lambda image, enhance=None: ((enh if enhance else raw), None)
    return svc


def test_ocr_best_confident_raw_skips_enhanced():
    called = {"enh": False}

    svc = AlprService(mode="real", model_path="x", model_version="v",
                      ocr_engine="paddle", multi_candidate=True)

    def fake_read(image, enhance=None):
        if enhance:
            called["enh"] = True
        return _reads("51F12345", 0.95), None

    svc._read = fake_read
    det, _, _ = svc._ocr_best(object())
    assert det is not None and det.plate_number == "51F-12345"
    assert called["enh"] is False           # confident raw read -> no enhanced pass (latency bound)


def test_ocr_best_rescues_when_raw_invalid():
    svc = _stub_service(raw=_reads("garbage", 0.9), enh=_reads("51F12345", 0.7))
    det, _, _ = svc._ocr_best(object())
    assert det is not None and det.plate_number == "51F-12345"   # enhanced rescued an invalid raw


def test_ocr_best_keeps_raw_on_disagreement():
    svc = _stub_service(raw=_reads("51F12345", 0.6), enh=_reads("30A11122", 0.95))
    det, _, _ = svc._ocr_best(object())
    assert det.plate_number == "51F-12345"   # both valid but differ -> keep raw (safer, measured)


def test_ocr_best_boosts_confidence_on_agreement():
    svc = _stub_service(raw=_reads("51F12345", 0.5), enh=_reads("51F12345", 0.9))
    det, _, _ = svc._ocr_best(object())
    assert det.plate_number == "51F-12345"
    assert det.confidence > 0.5              # agreement lifts confidence toward the mean


def test_simulate_is_deterministic_per_frame():
    service = AlprService(mode="simulate", model_path="x", model_version="v")
    assert service.detect(b"abc").plate_number == service.detect(b"abc").plate_number


def test_best_plate_from_ocr_single_box():
    det = best_plate_from_ocr([(_box(0, 0, 100, 30), "51F-12345", 0.9)])
    assert det is not None
    assert det.plate_number == "51F-12345"
    assert det.confidence == 0.9


def test_best_plate_from_ocr_reassembles_split_plate():
    # plate broken into two boxes, given out of left-to-right order
    det = best_plate_from_ocr([
        (_box(110, 0, 200, 30), "12345", 0.8),
        (_box(0, 0, 100, 30), "51F", 0.9),
    ])
    assert det.plate_number == "51F-12345"
    assert det.confidence == approx(0.85)  # mean of the window


def test_best_plate_from_ocr_split_with_confusion():
    det = best_plate_from_ocr([
        (_box(0, 0, 100, 30), "3OA", 0.9),
        (_box(110, 0, 200, 30), "I2345", 0.9),
    ])
    assert det.plate_number == "30A-12345"


def test_best_plate_from_ocr_ignores_distractor_text():
    det = best_plate_from_ocr([
        (_box(0, 0, 80, 30), "EXIT", 0.99),
        (_box(0, 40, 120, 70), "51F-12345", 0.7),
    ])
    assert det.plate_number == "51F-12345"


def test_best_plate_from_ocr_none_when_no_plate():
    assert best_plate_from_ocr([]) is None
    assert best_plate_from_ocr([(_box(0, 0, 50, 20), "HELLO", 0.9)]) is None


# --- yolo_char: char-assembly logic (pure, no ML) ---
from app.services.alpr import assemble_plate_chars  # noqa: E402


def test_assemble_chars_single_line_left_to_right():
    chars = [
        (50, 0, 60, 20, "F", 0.9), (0, 0, 10, 20, "5", 0.9), (25, 0, 35, 20, "1", 0.9),
        (70, 0, 80, 20, "1", 0.9), (90, 0, 100, 20, "2", 0.9), (110, 0, 120, 20, "3", 0.9),
        (130, 0, 140, 20, "4", 0.9), (150, 0, 160, 20, "5", 0.9),
    ]
    assert assemble_plate_chars(chars) == "51F12345"


def test_assemble_chars_two_lines_top_then_bottom():
    chars = [
        (0, 0, 10, 20, "5", 0.9), (15, 0, 25, 20, "1", 0.9), (30, 0, 40, 20, "F", 0.9),
        (0, 40, 10, 60, "1", 0.9), (15, 40, 25, 60, "2", 0.9), (30, 40, 40, 60, "3", 0.9),
        (45, 40, 55, 60, "4", 0.9), (60, 40, 70, 60, "5", 0.9),
    ]
    assert assemble_plate_chars(chars) == "51F12345"


def test_assemble_chars_empty():
    assert assemble_plate_chars([]) == ""
