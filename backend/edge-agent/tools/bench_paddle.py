#!/usr/bin/env python3
"""Head-to-head OCR benchmark: EasyOCR vs PaddleOCR on the SAME plate crops.

Isolates the OCR-engine variable. The plate is located once (YOLO, or the whole image when
``--no-detect``), then EVERY engine reads the identical crop and goes through the identical
post-processing (``enhance_plate`` -> ``best_plate_from_ocr`` -> ``canonicalize`` -> ``normalize``).
So any accuracy gap is the recognizer, not the detector or the cleanup logic.

Configs compared per image (all on the same YOLO crop):
  - easyocr_enh : EasyOCR on the enhanced crop + VN allowlist  (current production path)
  - paddle_raw  : PaddleOCR on the raw BGR crop
  - paddle_enh  : PaddleOCR on the enhanced (CLAHE/upscale) crop

Windows note: paddle + torch clash on DLL load unless torch is imported FIRST, so this module
imports torch before paddleocr. KMP_DUPLICATE_LIB_OK is set for the duplicate-OpenMP guard.

Setup:  pip install -r requirements-alpr.txt && pip install paddlepaddle==3.0.0 "paddleocr>=2.9,<3"
Labels: CSV  image_filename,true_plate  (# lines ignored) — same format as alpr_eval.py
Run:    python tools/bench_paddle.py --images-dir samples/real --labels samples/real/labels.csv \
               --model-path models/yolov8s_vn.pt
        python tools/bench_paddle.py --images-dir samples --labels samples/labels.csv --no-detect
"""
from __future__ import annotations

import argparse
import csv
import os
import pathlib
import statistics
import sys
import time

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

import torch  # noqa: E402,F401  -- MUST precede paddleocr so torch wins the DLL load race on Windows
import cv2  # noqa: E402
import numpy as np  # noqa: E402

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from app import plate as plate_util  # noqa: E402
from app.services.alpr import _expand_box, best_plate_from_ocr  # noqa: E402
from app.services.preprocess import enhance_plate  # noqa: E402

_OCR_ALLOWLIST = "0123456789ABCDEFGHKLMNPSTUVXYZ"
_TOP_K = 3


# --------------------------------------------------------------------------- engines
def make_easyocr():
    import easyocr
    reader = easyocr.Reader(["en"], gpu=False)

    def run(crop_bgr):
        enh = enhance_plate(crop_bgr)
        return reader.readtext(enh, detail=1, allowlist=_OCR_ALLOWLIST)

    return run


def make_paddle(enhance: bool):
    from paddleocr import PaddleOCR
    ocr = PaddleOCR(use_angle_cls=True, lang="en", show_log=False)

    def run(crop_bgr):
        img = enhance_plate(crop_bgr) if enhance else crop_bgr
        if img.ndim == 2:                       # paddle wants 3-channel
            img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
        res = ocr.ocr(img, cls=True)
        lines = res[0] if res and res[0] else []
        # PaddleOCR line = [box(4 pts), (text, score)] -> EasyOCR-shaped (box, text, conf)
        return [(ln[0], ln[1][0], float(ln[1][1])) for ln in lines]

    return run


# --------------------------------------------------------------------------- crop step
def yolo_boxes(model, frame):
    results = model(frame, verbose=False)
    boxes = results[0].boxes if results else None
    if boxes is None or len(boxes) == 0:
        return []
    ranked = sorted(boxes, key=lambda b: float(b.conf[0]), reverse=True)[:_TOP_K]
    h, w = frame.shape[:2]
    out = []
    for b in ranked:
        x1, y1, x2, y2 = (int(v) for v in b.xyxy[0])
        out.append(_expand_box(x1, y1, x2, y2, w, h))
    return out


def predict(frame, crops, ocr_run):
    """Run one OCR engine over the plate crop(s); return the best valid plate or None."""
    best = None
    for (px1, py1, px2, py2) in crops:
        crop = frame[py1:py2, px1:px2]
        if crop.size == 0:
            continue
        det = best_plate_from_ocr(ocr_run(crop))
        if det is not None and (best is None or det.confidence > best.confidence):
            best = det
    return best


# --------------------------------------------------------------------------- main
def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-dir", required=True)
    ap.add_argument("--labels", required=True)
    ap.add_argument("--model-path", default="models/yolov8s_vn.pt")
    ap.add_argument("--no-detect", action="store_true",
                    help="skip YOLO; treat the whole image as the plate crop (for pre-cropped sets)")
    args = ap.parse_args()

    model = None
    if not args.no_detect:
        from ultralytics import YOLO
        model = YOLO(args.model_path)

    print("Building OCR engines (first call downloads PaddleOCR weights ~10MB)...")
    configs = {
        "easyocr_enh": make_easyocr(),
        "paddle_raw": make_paddle(enhance=False),
        "paddle_enh": make_paddle(enhance=True),
    }

    base = pathlib.Path(args.images_dir)
    rows = []
    with open(args.labels, newline="", encoding="utf-8") as f:
        for row in csv.reader(f):
            if not row or row[0].lstrip().startswith("#"):
                continue
            rows.append((row[0].strip(), plate_util.normalize(row[1].strip())))

    stats = {name: {"correct": 0, "lat": []} for name in configs}
    detail = []
    total = 0
    for fname, truth in rows:
        image = base / fname
        if not image.exists():
            print(f"  [skip] missing {image}", file=sys.stderr)
            continue
        frame = cv2.imdecode(np.frombuffer(image.read_bytes(), np.uint8), cv2.IMREAD_COLOR)
        if frame is None:
            print(f"  [skip] unreadable {image}", file=sys.stderr)
            continue
        total += 1
        crops = [(0, 0, frame.shape[1], frame.shape[0])] if args.no_detect else yolo_boxes(model, frame)
        line = {"file": fname, "truth": truth}
        for name, run in configs.items():
            t0 = time.perf_counter()
            det = predict(frame, crops, run) if crops else None
            stats[name]["lat"].append((time.perf_counter() - t0) * 1000)
            pred = plate_util.normalize(det.plate_number) if det else "(no read)"
            conf = det.confidence if det else 0.0
            line[name] = (pred, conf)
            if pred == truth:
                stats[name]["correct"] += 1
        detail.append(line)

    # ---- report
    print("\n" + "=" * 78)
    print(f"Images: {total}   |   crops via: {'whole-image' if args.no_detect else args.model_path}")
    print("=" * 78)
    hdr = f'{"file":<22}{"truth":<12}' + "".join(f"{n:<22}" for n in configs)
    print(hdr)
    print("-" * len(hdr))
    for ln in detail:
        cells = f'{ln["file"][:21]:<22}{ln["truth"]:<12}'
        for name in configs:
            pred, conf = ln[name]
            mark = "OK " if pred == ln["truth"] else "  X"
            cells += f"{mark}{pred} {conf:.2f}"[:21].ljust(22)
        print(cells)
    print("-" * len(hdr))
    print(f'{"ACCURACY":<34}' + "".join(
        f'{stats[n]["correct"]}/{total} ({stats[n]["correct"]/total*100:.0f}%)'.ljust(22)
        for n in configs) if total else "")
    print(f'{"mean latency ms":<34}' + "".join(
        f'{statistics.mean(stats[n]["lat"]):.0f}'.ljust(22) for n in configs if stats[n]["lat"]))
    print("=" * 78)


if __name__ == "__main__":
    main()
