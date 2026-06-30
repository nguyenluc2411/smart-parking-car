"""Tests for AlprService._grammar_head_fix — province head letter->digit recovery (3->B->8)."""
import numpy as np

from app.services.alpr import AlprService, Detection

BBOX = {"x": 0, "y": 0, "w": 10, "h": 10}
# one EasyOCR read spanning the whole plate: box (4 points), text, confidence
PLATE_BOX = [[10, 5], [390, 5], [390, 45], [10, 45]]


class FakeReader:
    """Returns a scripted result for the head re-OCR; records whether it was called."""

    def __init__(self, head_result):
        self._head_result = head_result
        self.called = False

    def readtext(self, image, detail=1, allowlist=None):
        self.called = True
        return self._head_result


def _svc():
    return AlprService("real", "m", "v", grammar_fix=True, preprocess=False)


def _img():
    return np.zeros((50, 400, 3), dtype=np.uint8)


def test_head_letter_is_re_ocrd_to_digits():
    # raw read "B0F33333" -> canonicalize coerced head to 80F-33333; head re-OCR reads "30"
    reader = FakeReader([([[0, 0], [80, 0], [80, 40], [0, 40]], "30", 0.94)])
    reads = [(PLATE_BOX, "B0F33333", 0.81)]
    detection = Detection("80F-33333", 0.81, BBOX, 100)

    out = _svc()._grammar_head_fix(reader, _img(), reads, detection)

    assert reader.called is True
    assert out.plate_number == "30F-33333"      # head spliced from the digit-only re-read
    assert out.bbox == BBOX                      # geometry preserved
    # confidence lifted toward the confident head re-read: max(0.81, mean(0.81, 0.94)) = 0.875
    assert out.confidence == 0.875


def test_digit_head_is_left_untouched():
    # raw head already digits -> guard skips, reader must NOT be called, plate unchanged
    reader = FakeReader([([[0, 0], [80, 0], [80, 40], [0, 40]], "99", 0.99)])
    reads = [(PLATE_BOX, "30A61329", 0.88)]
    detection = Detection("30A-61329", 0.88, BBOX, 100)

    out = _svc()._grammar_head_fix(reader, _img(), reads, detection)

    assert reader.called is False
    assert out.plate_number == "30A-61329"


def test_low_confidence_head_reocr_is_rejected():
    # head re-OCR not confident enough (<0.6) -> keep the original plate
    reader = FakeReader([([[0, 0], [80, 0], [80, 40], [0, 40]], "30", 0.40)])
    reads = [(PLATE_BOX, "B0F33333", 0.81)]
    detection = Detection("80F-33333", 0.81, BBOX, 100)

    out = _svc()._grammar_head_fix(reader, _img(), reads, detection)
    assert out.plate_number == "80F-33333"


def test_invalid_result_is_not_applied():
    # head re-OCR yields only 1 digit -> can't form a 2-digit head -> unchanged
    reader = FakeReader([([[0, 0], [80, 0], [80, 40], [0, 40]], "3", 0.95)])
    reads = [(PLATE_BOX, "B0F33333", 0.81)]
    detection = Detection("80F-33333", 0.81, BBOX, 100)

    out = _svc()._grammar_head_fix(reader, _img(), reads, detection)
    assert out.plate_number == "80F-33333"


def test_disabled_when_no_detection():
    out = _svc()._grammar_head_fix(FakeReader([]), _img(), [], None)
    assert out is None
