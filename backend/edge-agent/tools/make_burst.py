#!/usr/bin/env python3
"""Synthesise multi-frame BURSTS from labelled single CAR images — to measure the multi-frame uplift.

A real gate camera sees a car across many frames under varying blur / brightness / angle, so voting
across frames (see ``app.services.aggregate``) recovers a plate that any single noisy frame gets
wrong. Our sample sets are ~1 frame per car, which leaves the vote nothing to work on. This tool
takes each labelled image and emits N variants (clean original + motion blur, dim "night", glare,
slight rotation, sensor noise) that share one ``group_id`` + truth, producing a realistic burst.

The variants are SYNTHETIC augmentation, NOT new captures — treat the resulting number as a
robustness / multi-frame-uplift indicator, not a substitute for a real captured-video KPI run.

Run:  python tools/make_burst.py --images-dir samples/real --labels samples/real/labels.csv \
            --out-dir samples/burst --frames 6
Then: python tools/alpr_eval.py --images-dir samples/burst --labels samples/burst/labels.csv \
            --detector yolo --model-path models/yolov8s_vn.pt --multi-frame
"""
from __future__ import annotations

import argparse
import csv
import pathlib
import sys

import cv2
import numpy as np


def _motion_blur(frame, rng):
    k = int(rng.choice([3, 5]))          # mild — a real burst has only slight roll blur
    kernel = np.zeros((k, k), np.float32)
    kernel[k // 2, :] = 1.0 / k          # horizontal streak ~ a car rolling past the camera
    return cv2.filter2D(frame, -1, kernel)


def _dim(frame, rng):
    return np.clip(frame.astype(np.float32) * rng.uniform(0.6, 0.85), 0, 255).astype(np.uint8)


def _glare(frame, rng):
    return np.clip(frame.astype(np.float32) * rng.uniform(1.15, 1.35), 0, 255).astype(np.uint8)


def _rotate(frame, rng):
    h, w = frame.shape[:2]
    angle = rng.uniform(-4, 4)           # camera/plate never perfectly square-on
    m = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    return cv2.warpAffine(frame, m, (w, h), borderMode=cv2.BORDER_REPLICATE)


def _noise(frame, rng):
    noise = rng.normal(0, rng.uniform(4, 9), frame.shape).astype(np.float32)
    return np.clip(frame.astype(np.float32) + noise, 0, 255).astype(np.uint8)


def augment(frame, idx, rng):
    """idx 0 = clean original; later frames get AT MOST 2 mild, independent degradations.

    Kept deliberately mild and capped at two effects: a real gate burst has a few sharp frames and
    a few slightly blurred/tilted ones, with errors scattered FRAME-TO-FRAME. Stacking heavy effects
    on every frame instead produces the SAME systematic OCR misread everywhere, which majority voting
    cannot fix (and isn't representative of a real camera).
    """
    if idx == 0:
        return frame
    effects = [_motion_blur, _dim, _glare, _rotate, _noise]
    chosen = rng.choice(len(effects), size=rng.integers(1, 3), replace=False)  # 1 or 2 effects
    out = frame
    for c in chosen:
        out = effects[int(c)](out, rng)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-dir", required=True)
    ap.add_argument("--labels", required=True, help="CSV: image_filename,true_plate[,group_id]")
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--frames", type=int, default=6, help="variants per source image (incl. original)")
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    src = pathlib.Path(args.images_dir)
    out = pathlib.Path(args.out_dir)
    out.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(args.seed)

    rows_out, made = [], 0
    with open(args.labels, newline="", encoding="utf-8") as f:
        for row in csv.reader(f):
            if not row or row[0].lstrip().startswith("#"):
                continue
            fname, truth = row[0].strip(), row[1].strip()
            group = row[2].strip() if len(row) > 2 and row[2].strip() else truth
            frame = cv2.imread(str(src / fname))
            if frame is None:
                print(f"  [skip] cannot read {src / fname}", file=sys.stderr)
                continue
            stem = pathlib.Path(fname).stem
            for i in range(args.frames):
                vname = f"{stem}_f{i}.jpg"
                cv2.imwrite(str(out / vname), augment(frame, i, rng))
                rows_out.append([vname, truth, group])   # group_id keeps the burst together
                made += 1

    with open(out / "labels.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["# SYNTHETIC burst — augmented variants of labelled samples (not real captures)"])
        w.writerows(rows_out)
    print(f"Wrote {made} frames -> {out}  ({out / 'labels.csv'})")


if __name__ == "__main__":
    main()
