"""Diagnose what EasyOCR actually reads on one image — raw, before canonicalize."""
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.services import preprocess as pp

IMG = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "samples", "real", "30F33333.webp")
MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "models", "yolov8s_vn.pt")
ALLOW = "0123456789ABCDEFGHKLMNPSTUVXYZĐ"

from ultralytics import YOLO  # noqa: E402
import easyocr  # noqa: E402

frame = cv2.imdecode(np.fromfile(IMG, np.uint8), cv2.IMREAD_COLOR)
print(f"frame: {frame.shape[1]}x{frame.shape[0]}")
model = YOLO(MODEL)
reader = easyocr.Reader(["en"], gpu=False)

boxes = model(frame, verbose=False)[0].boxes
print(f"YOLO boxes: {len(boxes)}")
if len(boxes) == 0:
    sys.exit("no plate box")
box = sorted(boxes, key=lambda b: float(b.conf[0]), reverse=True)[0]
x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
crop = frame[y1:y2, x1:x2]
print(f"top crop: {crop.shape[1]}x{crop.shape[0]}  (yolo_conf={float(box.conf[0]):.2f})")


def dump(label, img, **kw):
    reads = reader.readtext(img, detail=1, **kw)
    txt = " | ".join(f"'{t}'({c:.2f})" for _, t, c in reads)
    print(f"  {label:<28}: {txt or '(nothing)'}")


print("-- raw OCR on the crop, several ways --")
dump("plain", crop)
dump("plain + allowlist", crop, allowlist=ALLOW)
dump("enhance_plate", pp.enhance_plate(crop))
dump("enhance + allowlist", pp.enhance_plate(crop), allowlist=ALLOW)

for s in (2, 3, 4):
    up = cv2.resize(crop, None, fx=s, fy=s, interpolation=cv2.INTER_CUBIC)
    dump(f"upscale x{s} + allowlist", up, allowlist=ALLOW)

# pad the box (YOLO may clip the first digit) then upscale
ph, pw = crop.shape[0] // 5, crop.shape[1] // 20
py1, py2 = max(0, y1 - ph), min(frame.shape[0], y2 + ph)
px1, px2 = max(0, x1 - pw), min(frame.shape[1], x2 + pw)
padded = frame[py1:py2, px1:px2]
up = cv2.resize(padded, None, fx=3, fy=3, interpolation=cv2.INTER_CUBIC)
dump("padded + upscale x3 + allow", up, allowlist=ALLOW)

# GRAMMAR-DRIVEN: positions 0-1 are digits. Re-OCR the left of the plate with digits only.
print("-- grammar: re-OCR the left region, DIGITS ONLY --")
up = cv2.resize(crop, None, fx=4, fy=4, interpolation=cv2.INTER_CUBIC)
for frac in (0.28, 0.33, 0.40):
    left = up[:, : int(up.shape[1] * frac)]
    dump(f"left {int(frac*100)}% digits-only", left, allowlist="0123456789")

# save crop for human inspection
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "samples", "real", "_crop.png")
cv2.imwrite(out, cv2.resize(crop, None, fx=4, fy=4, interpolation=cv2.INTER_CUBIC))
print(f"saved crop -> {out}")
