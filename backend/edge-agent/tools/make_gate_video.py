#!/usr/bin/env python3
"""Synthesize a 'gate camera' clip from real still car photos, so the video pipeline + temporal
voting can be exercised WITHOUT a real camera. Each still becomes one car that 'approaches' (zooms
in) across N frames with per-frame jitter — brightness drift, motion blur, sensor noise, tiny
rotation/translation — so the frames differ (voting has something to vote on). A few blank road
frames separate cars so bench_video.py's gap-segmentation splits them.

NOTE: this is synthesized MOTION from one still per car, so frames are correlated — it validates the
harness and shows voting working, but it is NOT a substitute for a genuine multi-viewpoint gate clip
(real motion blur, real new angles each frame). Treat its numbers as a pipeline check, not a KPI.

Run: python tools/make_gate_video.py --out samples/video/synthetic_gate.mp4
"""
from __future__ import annotations

import argparse
import pathlib
import sys

import cv2
import numpy as np

# (image under samples/real, true plate) — only stills the paddle benchmark already reads correctly
CARS = [
    ("samples/real/lp_shot1.png", "30A-61235"),
    ("samples/real/clip4_new_0.jpg", "51G-97162"),
    ("samples/real/3.jpg", "30A-33918"),
]
W, H, FPS = 1280, 720, 25


def letterbox(img, scale):
    """Place ``img`` centred on a W×H gray canvas at the given scale (simulates approach distance)."""
    canvas = np.full((H, W, 3), 110, np.uint8)
    ih, iw = img.shape[:2]
    s = scale * min(W / iw, H / ih)
    rw, rh = max(1, int(iw * s)), max(1, int(ih * s))
    resized = cv2.resize(img, (rw, rh))
    x, y = (W - rw) // 2, (H - rh) // 2
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + rw), min(H, y + rh)
    canvas[y0:y1, x0:x1] = resized[y0 - y:y1 - y, x0 - x:x1 - x]
    return canvas


def augment(frame, t, rng):
    """t in [0,1] across the car's frames. Middle frames are cleanest; edges blurrier/dimmer —
    mimics a car that is sharpest as it passes the trigger line."""
    edge = abs(t - 0.5) * 2                       # 0 at middle, 1 at the ends
    # brightness drift
    frame = np.clip(frame.astype(np.float32) * (0.75 + 0.5 * (1 - edge)), 0, 255).astype(np.uint8)
    # motion blur, worse at the edges
    k = int(1 + round(edge * 6))
    if k >= 2:
        frame = cv2.blur(frame, (k, k))
    # small rotation + translation jitter
    ang = float(rng.uniform(-4, 4))
    M = cv2.getRotationMatrix2D((W / 2, H / 2), ang, 1.0)
    M[0, 2] += rng.uniform(-12, 12)
    M[1, 2] += rng.uniform(-8, 8)
    frame = cv2.warpAffine(frame, M, (W, H), borderValue=(110, 110, 110))
    # sensor noise
    noise = rng.normal(0, 6, frame.shape).astype(np.float32)
    return np.clip(frame.astype(np.float32) + noise, 0, 255).astype(np.uint8)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="samples/video/synthetic_gate.mp4")
    ap.add_argument("--frames-per-car", type=int, default=30)
    ap.add_argument("--gap-frames", type=int, default=12)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    rng = np.random.default_rng(args.seed)
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    writer = cv2.VideoWriter(str(out), cv2.VideoWriter_fourcc(*"mp4v"), FPS, (W, H))
    if not writer.isOpened():
        print("Cannot open VideoWriter (codec missing?)", file=sys.stderr)
        sys.exit(1)

    gap = np.full((H, W, 3), 110, np.uint8)        # empty road between cars
    n = args.frames_per_car
    for path, plate in CARS:
        img = cv2.imread(path)
        if img is None:
            print(f"  [skip] missing {path}", file=sys.stderr)
            continue
        for i in range(n):
            t = i / (n - 1)
            scale = 0.45 + 0.55 * t                # zoom from far (0.45) to near (1.0)
            writer.write(augment(letterbox(img, scale), t, rng))
        for _ in range(args.gap_frames):
            writer.write(gap)
        print(f"  car {plate}: {n} frames")
    writer.release()
    print(f"Wrote {out}  ({len(CARS)} cars, {FPS} fps)")
    print("Expected (in order):", ",".join(p for _, p in CARS))


if __name__ == "__main__":
    main()
