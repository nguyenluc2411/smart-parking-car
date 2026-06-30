"""A/B benchmark: ALPR accuracy WITHOUT vs WITH image preprocessing, on a labelled set."""
import csv
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import plate as plate_util
from app.services.alpr import AlprService

DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "samples", "real")
MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "models", "yolov8s_vn.pt")
EXCLUDE = {"4.jpg"}  # two cars, excluded from scoring per labels.csv


def load_truth():
    truth = {}
    with open(os.path.join(DIR, "labels.csv")) as f:
        for row in csv.reader(f):
            if not row or row[0].startswith("#") or len(row) < 2:
                continue
            truth[row[0].strip()] = plate_util.canonicalize(row[1].strip())
    truth["30F333333.webp"] = plate_util.canonicalize("30F-33333")
    return truth


def run(preprocess: bool, truth: dict):
    svc = AlprService("real", MODEL, "bench", detector="yolo",
                      ocr_languages="en", ocr_gpu=False, preprocess=preprocess)
    # warm up so timings are steady-state
    warm = os.path.join(DIR, next(iter(truth)))
    if os.path.exists(warm):
        svc.detect(open(warm, "rb").read())
    rows, correct, scored = [], 0, 0
    for fname, gt in truth.items():
        if fname in EXCLUDE:
            continue
        path = os.path.join(DIR, fname)
        if not os.path.exists(path):
            continue
        t = time.perf_counter()
        d = svc.detect(open(path, "rb").read())
        ms = int((time.perf_counter() - t) * 1000)
        got = d.plate_number if d else "(none)"
        ok = (got == gt)
        scored += 1
        correct += int(ok)
        rows.append((fname, gt, got, round(d.confidence, 3) if d else "-", ms))
    return rows, correct, scored


def main():
    truth = load_truth()
    print("Running OFF (no preprocess)...", flush=True)
    off_rows, off_c, n = run(False, truth)
    print("Running ON  (preprocess)...", flush=True)
    on_rows, on_c, _ = run(True, truth)

    on_map = {r[0]: r for r in on_rows}
    print(f"\n{'file':<18}{'truth':<12}{'OFF pred':<12}{'ON pred':<12}{'OFFms':>7}{'ONms':>7}")
    print("-" * 70)
    for f, gt, off_pred, _, off_ms in off_rows:
        on = on_map.get(f)
        on_pred, on_ms = (on[2], on[4]) if on else ("-", "-")
        flag = ""
        if off_pred != gt and on_pred == gt:
            flag = "  <- FIXED"
        elif off_pred == gt and on_pred != gt:
            flag = "  <- REGRESSED"
        print(f"{f:<18}{gt:<12}{off_pred:<12}{on_pred:<12}{off_ms:>7}{on_ms:>7}{flag}")
    print("-" * 70)
    print(f"Accuracy  OFF: {off_c}/{n} = {off_c/n*100:.1f}%   "
          f"ON: {on_c}/{n} = {on_c/n*100:.1f}%")


if __name__ == "__main__":
    main()
