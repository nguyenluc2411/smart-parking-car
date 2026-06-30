#!/usr/bin/env python3
"""Batch ALPR evaluation — measures the BR-001 recognition KPI + ALPR latency, in-process.

Runs the REAL ALPR pipeline over a labelled image set (no edge server needed) and reports
accuracy, confidence and per-frame latency. Use day/ vs night/ subsets to verify the
≥95% day / ≥90% night targets.

With ``--multi-frame`` it ALSO groups frames of the same car and votes across them (see
``app.services.aggregate``) — reporting per-group consensus accuracy next to the per-frame number
so the multi-frame accuracy gain is visible in one run. Frames are grouped by an optional 3rd
labels column ``group_id``; without it, all frames sharing a ground-truth plate form one car's burst.

Setup:  pip install -r requirements-alpr.txt
Labels: a CSV  image_filename,true_plate[,group_id]  (lines starting with # are ignored)
Run:    python tools/alpr_eval.py --images-dir samples --labels samples/labels.csv --detector ocr_only
        python tools/alpr_eval.py --images-dir samples --labels samples/labels.csv \
               --detector yolo --model-path models/yolov8s_vn.pt
        python tools/alpr_eval.py --images-dir samples --labels samples/labels.csv --multi-frame
"""
from __future__ import annotations

import argparse
import csv
import pathlib
import statistics
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))

from app import plate as plate_util  # noqa: E402
from app.services.aggregate import decide, tally  # noqa: E402
from app.services.alpr import AlprService  # noqa: E402


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--images-dir", required=True)
    ap.add_argument("--labels", required=True, help="CSV: image_filename,true_plate")
    ap.add_argument("--detector", default="ocr_only", choices=["yolo", "ocr_only"])
    ap.add_argument("--model-path", default="models/yolov8s_vn.pt")
    ap.add_argument("--threshold", type=float, default=0.85)
    ap.add_argument("--ocr-gpu", action="store_true")
    ap.add_argument("--multi-frame", action="store_true",
                    help="also vote across frames of the same car and report consensus accuracy")
    ap.add_argument("--min-votes", type=int, default=2, help="frames that must agree (multi-frame)")
    ap.add_argument("--min-conf", type=float, default=0.80,
                    help="min mean confidence to accept a consensus (multi-frame)")
    args = ap.parse_args()

    alpr = AlprService("real", args.model_path, "eval",
                       detector=args.detector, ocr_gpu=args.ocr_gpu)
    base = pathlib.Path(args.images_dir)

    total = correct = read_ok = above_threshold = 0
    latencies: list[int] = []
    confidences: list[float] = []
    misses: list[tuple[str, str, str]] = []
    # group_id -> {"truth": str, "reads": [(plate, conf), ...]} for the multi-frame vote
    groups: dict[str, dict] = {}

    with open(args.labels, newline="", encoding="utf-8") as f:
        for row in csv.reader(f):
            if not row or row[0].lstrip().startswith("#"):
                continue
            fname, truth = row[0].strip(), plate_util.normalize(row[1].strip())
            group_id = row[2].strip() if len(row) > 2 and row[2].strip() else truth
            image = base / fname
            if not image.exists():
                print(f"  [skip] missing {image}", file=sys.stderr)
                continue

            total += 1
            group = groups.setdefault(group_id, {"truth": truth, "reads": []})
            det = alpr.detect(image.read_bytes())
            if det is None:
                misses.append((fname, truth, "(no read)"))
                continue
            read_ok += 1
            latencies.append(det.processing_ms)
            confidences.append(det.confidence)
            if det.confidence >= args.threshold:
                above_threshold += 1
            pred = plate_util.normalize(det.plate_number)
            group["reads"].append((pred, det.confidence))
            if pred == truth:
                correct += 1
            else:
                misses.append((fname, truth, pred))

    print("=" * 52)
    print(f"Detector          : {args.detector}")
    print(f"Images evaluated  : {total}")
    print(f"Read (non-null)   : {read_ok}")
    if total:
        print(f"Correct plate     : {correct}  ->  accuracy {correct / total * 100:.1f}%")
        print(f"Conf >= {args.threshold:.2f}      : {above_threshold}")
    if latencies:
        latencies.sort()
        p95 = latencies[min(len(latencies) - 1, int(len(latencies) * 0.95))]
        print(f"ALPR latency (ms) : mean {statistics.mean(latencies):.0f} | "
              f"p95 {p95} | max {max(latencies)}")
        print(f"Confidence        : mean {statistics.mean(confidences):.3f}")
    if misses:
        print("-- misses (file | truth | predicted) --")
        for fname, truth, pred in misses[:20]:
            print(f"   {fname} | {truth} | {pred}")
        if len(misses) > 20:
            print(f"   ... and {len(misses) - 20} more")
    if args.multi_frame:
        report_multi_frame(groups, args.min_votes, args.min_conf)
    print("=" * 52)


def report_multi_frame(groups: dict[str, dict], min_votes: int, min_conf: float) -> None:
    """Vote across each car's frames and report consensus accuracy vs the per-frame number.

    For every group: ``decide`` gives the accepted consensus (or None if no plate gathered enough
    votes); the best-ranked plate is still used as the prediction so a near-miss is scored too.
    ``accepted`` counts groups that actually cleared the thresholds (the ones the gate would open
    on), letting the report separate "right answer" from "confident enough to act".
    """
    n = correct = accepted = 0
    group_misses: list[tuple[str, str, str]] = []
    for gid, g in groups.items():
        ranked = tally(g["reads"])
        if not ranked:
            group_misses.append((gid, g["truth"], "(no read)"))
            n += 1
            continue
        n += 1
        consensus = decide(g["reads"], min_votes=min_votes, min_mean_confidence=min_conf)
        accepted += consensus is not None
        pred = (consensus or ranked[0]).plate_number
        if pred == g["truth"]:
            correct += 1
        else:
            group_misses.append((gid, g["truth"], pred))

    print("-" * 52)
    print(f"Multi-frame       : {n} cars  (≥{min_votes} votes, conf≥{min_conf:.2f})")
    if n:
        print(f"Consensus correct : {correct}  ->  accuracy {correct / n * 100:.1f}%")
        print(f"Reached consensus : {accepted}/{n}  (gate would open)")
    if group_misses:
        print("-- group misses (group | truth | predicted) --")
        for gid, truth, pred in group_misses[:20]:
            print(f"   {gid} | {truth} | {pred}")
        if len(group_misses) > 20:
            print(f"   ... and {len(group_misses) - 20} more")


if __name__ == "__main__":
    main()
