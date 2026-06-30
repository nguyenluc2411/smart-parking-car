#!/usr/bin/env python3
"""Run the REAL ALPR pipeline over a VIDEO file and vote per car — the closest offline proxy to a
live gate camera. Samples frames, reads each through AlprService (YOLO + PaddleOCR by default),
segments the stream into cars by read-gaps, and applies temporal voting (app.services.aggregate)
so a plate seen across several frames beats a lucky single read.

This is the prototype of the live edge flow without needing a running server or Kafka.

Setup:  pip install -r requirements-alpr.txt && pip install paddlepaddle==3.0.0 paddleocr==2.10.0
Run:    python tools/bench_video.py --video samples/video/gate_day.mp4 --model-path models/yolov8s_vn.pt
        # score it: pass the true plate(s) in order of appearance (comma-separated)
        python tools/bench_video.py --video gate.mp4 --expect 51F-12345,30A-33918
"""
from __future__ import annotations

import argparse
import os
import pathlib
import statistics
import sys
import time

os.environ.setdefault("KMP_DUPLICATE_LIB_OK", "TRUE")

import torch  # noqa: E402,F401  -- import before paddleocr so torch wins the Windows DLL load race
import cv2  # noqa: E402

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from app import plate as plate_util  # noqa: E402
from app.services.aggregate import decide, tally  # noqa: E402
from app.services.alpr import AlprService  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--video", required=True, help="path to an .mp4/.avi/... gate clip")
    ap.add_argument("--model-path", default="models/yolov8s_vn.pt")
    ap.add_argument("--detector", default="yolo", choices=["yolo", "ocr_only", "yolo_char"])
    ap.add_argument("--engine", default="paddle", choices=["paddle", "easyocr"])
    ap.add_argument("--every", type=int, default=5, help="run ALPR on every Nth frame (sampling)")
    ap.add_argument("--gap", type=int, default=4,
                    help="this many sampled frames with NO read closes a car and starts the next")
    ap.add_argument("--min-votes", type=int, default=2, help="frames that must agree to ACCEPT a car")
    ap.add_argument("--min-conf", type=float, default=0.80, help="min mean confidence to ACCEPT")
    ap.add_argument("--expect", default=None,
                    help="comma-separated true plate(s) in order of appearance, to score accuracy")
    ap.add_argument("--save-crops", default=None, help="dir to dump the best frame per car (debug)")
    args = ap.parse_args()

    alpr = AlprService("real", args.model_path, "video", detector=args.detector, ocr_engine=args.engine)
    cap = cv2.VideoCapture(args.video)
    if not cap.isOpened():
        print(f"Cannot open video: {args.video}", file=sys.stderr)
        sys.exit(1)
    fps = cap.get(cv2.CAP_PROP_FPS) or 25.0

    cars: list[dict] = []          # each: {"reads": [(plate,conf)], "start_f", "end_f", "best": (conf, jpg)}
    cur: dict | None = None
    miss_run = 0                   # consecutive sampled frames with no read (for car segmentation)
    latencies: list[int] = []
    frame_idx = sampled = read_ok = 0

    while True:
        ok, frame = cap.read()
        if not ok:
            break
        frame_idx += 1
        if frame_idx % args.every:
            continue
        sampled += 1
        ok_enc, buf = cv2.imencode(".jpg", frame)
        if not ok_enc:
            continue
        t0 = time.perf_counter()
        det = alpr.detect(buf.tobytes())
        latencies.append(int((time.perf_counter() - t0) * 1000))

        if det is None:
            miss_run += 1
            if cur is not None and miss_run >= args.gap:   # gap -> this car has left frame
                cars.append(cur)
                cur = None
            continue
        miss_run = 0
        read_ok += 1
        if cur is None:
            cur = {"reads": [], "start_f": frame_idx, "end_f": frame_idx, "best": (-1.0, None)}
        cur["reads"].append((det.plate_number, det.confidence))
        cur["end_f"] = frame_idx
        if det.confidence > cur["best"][0]:
            cur["best"] = (det.confidence, buf.tobytes())
    if cur is not None:
        cars.append(cur)
    cap.release()

    expected = [plate_util.normalize(p) for p in args.expect.split(",")] if args.expect else None
    if args.save_crops:
        pathlib.Path(args.save_crops).mkdir(parents=True, exist_ok=True)

    print("=" * 70)
    print(f"Video: {args.video}")
    print(f"Frames: {frame_idx} total | sampled {sampled} (every {args.every}) | "
          f"{read_ok} produced a read ({read_ok/sampled*100:.0f}% of sampled)" if sampled else "")
    print(f"Engine: {args.engine} | detector: {args.detector}")
    if latencies:
        latencies.sort()
        p95 = latencies[min(len(latencies) - 1, int(len(latencies) * 0.95))]
        print(f"ALPR latency/frame ms: mean {statistics.mean(latencies):.0f} | p95 {p95} | max {max(latencies)}")
    print("-" * 70)

    correct = 0
    for i, car in enumerate(cars):
        ranked = tally(car["reads"])
        consensus = decide(car["reads"], min_votes=args.min_votes, min_mean_confidence=args.min_conf)
        winner = consensus or (ranked[0] if ranked else None)
        t_start, t_end = car["start_f"] / fps, car["end_f"] / fps
        if winner is None:
            print(f"car {i+1}: (no plate)  t={t_start:.1f}-{t_end:.1f}s")
            continue
        tag = "ACCEPT" if consensus else " weak "
        line = (f"car {i+1}: [{tag}] {winner.plate_number}  votes={winner.votes}/{winner.frames} "
                f"conf={winner.mean_confidence:.2f}  t={t_start:.1f}-{t_end:.1f}s")
        if expected is not None and i < len(expected):
            hit = plate_util.normalize(winner.plate_number) == expected[i]
            correct += hit
            line += f"   {'OK' if hit else 'X expected ' + expected[i]}"
        print(line)
        if args.save_crops and car["best"][1]:
            (pathlib.Path(args.save_crops) / f"car{i+1}_{winner.plate_number}.jpg").write_bytes(car["best"][1])

    print("-" * 70)
    print(f"Cars detected: {len(cars)}")
    if expected is not None:
        print(f"Accuracy vs --expect: {correct}/{len(expected)} "
              f"({correct/len(expected)*100:.0f}%)" if expected else "")
    print("=" * 70)


if __name__ == "__main__":
    main()
