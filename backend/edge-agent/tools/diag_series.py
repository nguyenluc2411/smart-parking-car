"""Check if re-OCRing the SERIES region with VALID VN letters only fixes A->I (3.jpg)."""
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "samples", "real")
MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "models", "yolov8s_vn.pt")
# VN plates avoid I, J, O, Q, R, W (look-alikes of 1/0/2). Valid series letters:
VALID_LETTERS = "ABCDEFGHKLMNPSTUVXYZĐ"
IMG = os.path.join(DIR, "3.jpg")

from ultralytics import YOLO  # noqa: E402
import easyocr  # noqa: E402

frame = cv2.imdecode(np.fromfile(IMG, np.uint8), cv2.IMREAD_COLOR)
model = YOLO(MODEL)
reader = easyocr.Reader(["en"], gpu=False)
box = sorted(model(frame, verbose=False)[0].boxes, key=lambda b: float(b.conf[0]), reverse=True)[0]
x1, y1, x2, y2 = (int(v) for v in box.xyxy[0])
crop = frame[y1:y2, x1:x2]
up = cv2.resize(crop, None, fx=4, fy=4, interpolation=cv2.INTER_CUBIC)
print(f"crop {crop.shape[1]}x{crop.shape[0]} -> up {up.shape[1]}x{up.shape[0]}")


def dump(label, img, allow):
    r = reader.readtext(img, detail=1, allowlist=allow)
    print(f"  {label:<34}: " + (" | ".join(f"'{t}'({c:.2f})" for _, t, c in r) or "(nothing)"))


dump("whole, letters+digits", up, "0123456789" + VALID_LETTERS + "I")
# series sits just after the 2 head digits — scan a few windows for a single valid letter
W = up.shape[1]
for a, b in [(0.22, 0.40), (0.25, 0.42), (0.28, 0.45)]:
    seg = up[:, int(W * a):int(W * b)]
    dump(f"series [{int(a*100)}-{int(b*100)}%] valid-letters", seg, VALID_LETTERS)
    dump(f"series [{int(a*100)}-{int(b*100)}%] WITH I (control)", seg, VALID_LETTERS + "I")
