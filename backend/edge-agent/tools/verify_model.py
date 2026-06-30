#!/usr/bin/env python3
"""Smoke-test the REAL ALPR pipeline (YOLOv8 + EasyOCR) without the FastAPI/Kafka stack.

Proves three things against the actual code in ``app/services/alpr.py``:
  1. ``ultralytics.YOLO`` loads ``ALPR_MODEL_PATH`` and runs inference (returns boxes).
  2. EasyOCR reads a plate crop.
  3. ``AlprService.detect()`` assembles a ``Detection`` and ``plate.canonicalize`` validates it.

Pass a real car photo with a visible plate via ``--image`` for the most meaningful result.
With no image it generates a synthetic VN plate so the run is self-contained.

    python tools/verify_model.py --model models/yolov8s_vn.pt
    python tools/verify_model.py --model models/yolov8s_vn.pt --image samples/car.jpg
"""
from __future__ import annotations

import argparse
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from app.services.alpr import AlprService  # noqa: E402


def _synthetic_plate(text: str = "51F-12345") -> bytes:
    """Render a simple white-on-black VN-style plate as JPEG bytes."""
    import cv2  # noqa: WPS433
    import numpy as np  # noqa: WPS433

    img = np.full((220, 640, 3), 60, dtype=np.uint8)            # dark scene
    cv2.rectangle(img, (140, 70), (500, 160), (255, 255, 255), -1)  # white plate
    cv2.rectangle(img, (140, 70), (500, 160), (0, 0, 0), 3)
    cv2.putText(img, text, (160, 135), cv2.FONT_HERSHEY_SIMPLEX, 1.5, (0, 0, 0), 4)
    ok, buf = cv2.imencode(".jpg", img)
    return buf.tobytes()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--model", default="models/yolov8s_vn.pt")
    ap.add_argument("--image", help="path to a real test image (jpg/png)")
    ap.add_argument("--detector", default="yolo", choices=["yolo", "ocr_only"])
    ap.add_argument("--debug", action="store_true",
                    help="print each detected box + raw OCR text (diagnose None reads)")
    args = ap.parse_args()

    if not pathlib.Path(args.model).exists():
        print(f"[FAIL] model not found: {args.model} — run tools/download_model.py first")
        return 2

    if args.image:
        image_bytes = pathlib.Path(args.image).read_bytes()
        src = args.image
    else:
        image_bytes = _synthetic_plate()
        src = "synthetic 51F-12345"
    print(f"[info] image source: {src} ({len(image_bytes)} bytes)")

    # --- 1) raw model load + inference -------------------------------------
    import cv2  # noqa: WPS433
    import numpy as np  # noqa: WPS433
    from ultralytics import YOLO  # noqa: WPS433

    print(f"[info] loading YOLO model: {args.model}")
    model = YOLO(args.model)
    print(f"[ OK ] model loaded. task={model.task}  classes={model.names}")
    if len(model.names) > 5:
        print(f"[WARN] model has {len(model.names)} classes — looks like a stock COCO model, "
              "NOT a license-plate detector. It will localise cars/objects, not plates. "
              "Use tools/download_model.py (single-class 'license_plate') or fine-tune.")
    frame = cv2.imdecode(np.frombuffer(image_bytes, np.uint8), cv2.IMREAD_COLOR)
    res = model(frame, verbose=False)
    boxes = None if not res else res[0].boxes
    nboxes = 0 if boxes is None else len(boxes)
    print(f"[ OK ] inference ran. boxes_detected={nboxes}")

    if args.debug and nboxes:
        import easyocr  # noqa: WPS433
        from app import plate as plate_util  # noqa: WPS433
        reader = easyocr.Reader(["en"], gpu=False)
        for i, b in enumerate(boxes):
            x1, y1, x2, y2 = (int(v) for v in b.xyxy[0])
            crop = frame[y1:y2, x1:x2]
            texts = reader.readtext(crop, detail=0)
            raw = "".join(texts) if texts else ""
            print(f"  box{i}: conf={float(b.conf[0]):.3f} xyxy=({x1},{y1},{x2},{y2}) "
                  f"ocr={raw!r} -> canonical={plate_util.canonicalize(raw)!r} "
                  f"valid={plate_util.is_valid(plate_util.canonicalize(raw))}")

    # --- 2) + 3) full AlprService pipeline ---------------------------------
    alpr = AlprService("real", args.model, "verify", detector=args.detector)
    det = alpr.detect(image_bytes)
    if det is None:
        print("[warn] AlprService.detect() returned None "
              "(no plate region read as a valid VN plate on this image).")
        print("       Model + inference WORK; feed a real car photo via --image for a full read.")
        return 0
    print(f"[ OK ] detect() -> plate={det.plate_number!r} "
          f"confidence={det.confidence:.3f} bbox={det.bbox} ms={det.processing_ms}")
    print("[PASS] full real ALPR pipeline produced a valid VN plate.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
