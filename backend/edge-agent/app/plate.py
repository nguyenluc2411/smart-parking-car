"""License-plate normalization + validation (BR-001-3/4)."""
from __future__ import annotations

import re

# BR-001-3: valid VN plate. 2 digits + 1-2 series letters (incl. Đ; 51F, 51LD, 51MK, ...)
# + dash + 3-5 digits + optional .NN, e.g. 51F-12345, 51F-123.45, 51LD-123.45
#
# VN series letters NEVER include I, J, O, Q, R, W (chosen to avoid digit look-alikes). Excluding
# them means an OCR look-alike can't form an impossible-but-"valid-looking" plate (e.g. "30I"):
# such a read fails validation and burst voting picks a clean frame instead.
_SERIES_LETTERS = "ABCDEFGHKLMNPSTUVXYZĐ"
_PLATE_RE = re.compile(rf"^[0-9]{{2}}[{_SERIES_LETTERS}]{{1,2}}-[0-9]{{3,5}}(\.[0-9]{{2}})?$")


def normalize(raw: str | None) -> str | None:
    """BR-001-4: uppercase, strip whitespace AND the cosmetic dot — canonical dotless form
    (``30A-123.45`` -> ``30A-12345``) so the camera output matches stored/whitelist plates."""
    if raw is None:
        return None
    return re.sub(r"[\s.]", "", raw).upper()


def is_valid(normalized: str | None) -> bool:
    return normalized is not None and bool(_PLATE_RE.match(normalized))


# OCR confusion maps, applied position-aware: letters -> digits where the plate needs a
# digit, digits -> letters where it needs a letter. Covers the common look-alike pairs
# (O/0, I/1, Z/2, S/5, G/6, B/8, ...).
_TO_DIGIT = str.maketrans({
    "O": "0", "Q": "0", "D": "0",
    "I": "1", "L": "1",
    "Z": "2", "A": "4", "S": "5",
    "G": "6", "T": "7", "B": "8",
})
# Map digits only to VALID VN series letters. "0"->O and "1"->I are intentionally dropped (O/I are
# not used on VN plates) so a misread fails validation rather than becoming an impossible plate.
_TO_LETTER = str.maketrans({
    "2": "Z", "4": "A", "5": "S", "6": "G", "8": "B",
})


def canonicalize(raw: str | None) -> str | None:
    """Turn raw OCR text into a canonical VN plate (BR-001-3 format).

    Real OCR rarely emits the structural ``-`` (and often drops the cosmetic ``.``) and
    confuses look-alike characters, so ``normalize`` alone leaves readable plates failing
    ``is_valid``. This normalizes, keeps the text as-is if already valid, otherwise strips
    separators and rebuilds the plate position-aware: a VN plate is 2 digits + 1-2 series
    letters + 3-5 digits, so each position is coerced to its expected class (fixing O/0,
    I/1, ...) for both possible series lengths, and the first reading that validates wins.
    Examples: ``"51F 12345"`` -> ``"51F-12345"``, ``"3OA-I2345"`` -> ``"30A-12345"``.
    Returns the cleaned string unchanged when nothing parses (caller still gates on
    ``is_valid``).
    """
    s = normalize(raw)
    if s is None:
        return None
    s = re.sub(r"[^0-9A-ZĐ.\-]", "", s)  # drop OCR noise (quotes, stray punctuation)
    if is_valid(s):
        return s
    compact = re.sub(r"[.\-]", "", s)
    for n_letters in (1, 2):
        tail_start = 2 + n_letters
        if not (tail_start + 3 <= len(compact) <= tail_start + 5):
            continue
        head = compact[:2].translate(_TO_DIGIT)
        series = compact[2:tail_start].translate(_TO_LETTER)
        tail = compact[tail_start:].translate(_TO_DIGIT)
        candidate = f"{head}{series}-{tail}"
        if is_valid(candidate):
            return candidate
    return s
