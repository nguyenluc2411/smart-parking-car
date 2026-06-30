"""Validate a grammar-driven head re-OCR fix across ALL labelled samples (no regressions?).

Idea: VN plate head (first 2 chars) is ALWAYS digits. The general OCR sometimes reads a digit there
as a look-alike letter (3->B). After a valid plate is read, re-OCR just the head region with a
DIGITS-ONLY allowlist and, if it reads 2 confident digits, splice them into the head.
"""
import csv
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import plate as plate_util

DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "samples", "real")
MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "models", "yolov8s_vn.pt")
LETTERS_DIGITS = "0123456789ABCDEFGHKLMNPSTUVXYZĐ"
DIGITS = "0123456789"
EXCLUDE = {"4.jpg"}

from ultralytics import YOLO  # noqa: E402
import easyocr  # noqa: E402

model = YOLO(MODEL)
reader = easyocr.Reader(["en"], gpu=False)


def crop_plate(path):
    frame = cv2.imdecode(np.fromfile(path, np.uint8), cv2.IMREAD_COLOR)
    boxes = model(frame, verbose=False)[0].boxes
    if boxes is None or len(boxes) == 0:
        return None
    box = sorted(boxes, key=lambda b: float(b.conf[0]), reverse=True)[0]
    x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
    return frame[y1:y2, x1:x2]


def base_read(crop):
    up = cv2.resize(crop, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
    reads = reader.readtext(up, detail=1, allowlist=LETTERS_DIGITS)
    if not reads:
        return None, None, up
    reads.sort(key=lambda r: r[0][0][0])  # left-to-right
    text = "".join(t for _, t, _ in reads)
    return plate_util.canonicalize(text), reads, up


def _raw_head_has_letter(reads):
    """True if the raw OCR text has a non-digit in the first 2 (province) positions."""
    text = "".join(t for _, t, _ in sorted(reads, key=lambda r: r[0][0][0]))
    compact = "".join(ch for ch in text.upper() if ch.isalnum())
    return len(compact) >= 2 and any(not ch.isdigit() for ch in compact[:2])


def head_fix(plate, reads, up):
    """Re-OCR the head region (digits-only) and splice 2 digits into the plate head.

    GUARD: only fire when the raw read had a LETTER in the digit-only province head (e.g. 3->B).
    A head that already read as digits is left untouched, so correct plates can't be corrupted.
    """
    if not plate or not reads or not _raw_head_has_letter(reads):
        return plate
    # widest text box = the plate string; take its left 2/N as the 2-char province head.
    box, text, _ = max(reads, key=lambda r: r[0][2][0] - r[0][0][0])
    xs = [p[0] for p in box]; ys = [p[1] for p in box]
    x0, x1, y0, y1 = int(min(xs)), int(max(xs)), int(min(ys)), int(max(ys))
    n = max(len(text), 3)
    hx1 = x0 + int((x1 - x0) * 2.2 / n)
    head_img = up[max(0, y0):y1, max(0, x0):hx1]
    if head_img.size == 0:
        return plate
    hreads = reader.readtext(head_img, detail=1, allowlist=DIGITS)
    digits = "".join(t for _, t, c in hreads if c >= 0.5)
    digits = "".join(ch for ch in digits if ch.isdigit())
    if len(digits) >= 2:
        fixed = digits[:2] + plate[2:]
        if plate_util.is_valid(fixed):
            return fixed
    return plate


truth = {}
with open(os.path.join(DIR, "labels.csv")) as f:
    for row in csv.reader(f):
        if not row or row[0].startswith("#") or len(row) < 2:
            continue
        truth[row[0].strip()] = plate_util.canonicalize(row[1].strip())
truth["30F33333.webp"] = plate_util.canonicalize("30F-33333")

print(f"{'file':<16}{'truth':<12}{'base':<12}{'+headfix':<12}{'note'}")
print("-" * 60)
b_ok = f_ok = n = 0
for fname, gt in truth.items():
    if fname in EXCLUDE:
        continue
    path = os.path.join(DIR, fname)
    if not os.path.exists(path):
        continue
    crop = crop_plate(path)
    if crop is None:
        print(f"{fname:<16}{gt:<12}{'(no box)':<12}{'(no box)':<12}")
        n += 1
        continue
    base, reads, up = base_read(crop)
    fixed = head_fix(base, reads, up)
    n += 1
    b_ok += (base == gt)
    f_ok += (fixed == gt)
    note = ""
    if base != gt and fixed == gt:
        note = "FIXED"
    elif base == gt and fixed != gt:
        note = "REGRESSED"
    print(f"{fname:<16}{gt:<12}{str(base):<12}{str(fixed):<12}{note}")
print("-" * 60)
print(f"base accuracy: {b_ok}/{n} = {b_ok/n*100:.1f}%   +headfix: {f_ok}/{n} = {f_ok/n*100:.1f}%")
