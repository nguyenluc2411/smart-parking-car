"""Đo accuracy model ký tự đã train, end-to-end, so với EasyOCR — trên bộ ảnh THẬT có nhãn.

Dùng đúng pipeline edge (AlprService) nên số đo phản ánh thực tế deploy. Bộ ảnh: thư mục chứa
ảnh + labels.csv (dòng: filename,biển_đúng; '#'=comment).

    python training/eval_chars.py --model runs/detect/vn_chars/weights/best.pt \
        --images-dir samples/real --labels samples/real/labels.csv
"""
from __future__ import annotations

import argparse
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app import plate as pu  # noqa: E402
from app.services.alpr import AlprService  # noqa: E402

PLATE_MODEL = "models/yolov8s_vn.pt"


def load_truth(labels):
    t = {}
    for row in csv.reader(open(labels, encoding="utf-8")):
        if row and not row[0].lstrip().startswith("#") and len(row) >= 2:
            t[row[0].strip()] = pu.canonicalize(row[1].strip())
    return t


def run(svc, images_dir, truth, thr=0.4):
    ok = n = 0
    rows = []
    for f, gt in truth.items():
        p = os.path.join(images_dir, f)
        if not os.path.exists(p):
            continue
        n += 1
        d = svc.detect(open(p, "rb").read())
        got = d.plate_number if d and d.confidence >= thr else "(none)"
        ok += got == gt
        rows.append((f, gt, got))
    return ok, n, rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="best.pt của model ký tự")
    ap.add_argument("--images-dir", default="samples/real")
    ap.add_argument("--labels", default="samples/real/labels.csv")
    args = ap.parse_args()

    truth = load_truth(args.labels)
    easy = AlprService("real", PLATE_MODEL, "v", detector="yolo", ocr_gpu=False, preprocess=False)
    char = AlprService("real", PLATE_MODEL, "v", detector="yolo_char", ocr_gpu=False,
                       char_model_path=args.model)

    eo, n, erows = run(easy, args.images_dir, truth)
    co, _, crows = run(char, args.images_dir, truth)
    cmap = {r[0]: r[2] for r in crows}
    print(f"{'file':<18}{'truth':<12}{'EasyOCR':<14}{'yolo_char':<14}")
    print("-" * 58)
    for f, gt, pe in erows:
        print(f"{f:<18}{gt:<12}{pe:<14}{cmap.get(f, '-'):<14}")
    print("-" * 58)
    print(f"EasyOCR: {eo}/{n} = {eo/n*100:.1f}%    yolo_char: {co}/{n} = {co/n*100:.1f}%")
    print("Chỉ deploy yolo_char nếu nó CAO HƠN EasyOCR trên bộ ảnh thật này.")


if __name__ == "__main__":
    main()
