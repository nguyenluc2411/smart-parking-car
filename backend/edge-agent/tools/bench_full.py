"""Full real ALPR pipeline accuracy over the labelled set, comparing grammar_fix OFF vs ON."""
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import plate as plate_util
from app.services.alpr import AlprService

DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "samples", "real")
MODEL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "models", "yolov8s_vn.pt")
EXCLUDE = {"4.jpg"}


def truth():
    t = {}
    with open(os.path.join(DIR, "labels.csv")) as f:
        for row in csv.reader(f):
            if not row or row[0].startswith("#") or len(row) < 2:
                continue
            t[row[0].strip()] = plate_util.canonicalize(row[1].strip())
    t["30F33333.webp"] = plate_util.canonicalize("30F-33333")
    return t


def run(preprocess, grammar_fix):
    svc = AlprService("real", MODEL, "v", detector="yolo", ocr_languages="en",
                      ocr_gpu=False, preprocess=preprocess, grammar_fix=grammar_fix)
    res = {}
    for fname in truth():
        if fname in EXCLUDE:
            continue
        path = os.path.join(DIR, fname)
        if not os.path.exists(path):
            continue
        d = svc.detect(open(path, "rb").read())
        # mirror the endpoint: a read below the 0.4 confidence threshold is rejected (LOW_CONFIDENCE)
        if d is None:
            res[fname] = (None, "(none)")
        elif d.confidence < 0.4:
            res[fname] = (None, f"(rej {d.confidence:.2f})")
        else:
            res[fname] = (d.plate_number, f"{d.plate_number} {d.confidence:.2f}")
    return res


def main():
    gt = truth()
    configs = {
        "base": run(False, False),
        "gram": run(False, True),
        "prep": run(True, False),
        "prep+gram": run(True, True),
    }
    files = list(configs["base"])
    print(f"{'file':<16}{'truth':<12}" + "".join(f"{k:<18}" for k in configs))
    print("-" * (28 + 18 * len(configs)))
    for f in files:
        row = f"{f:<16}{gt[f]:<12}"
        for k in configs:
            row += f"{configs[k][f][1]:<18}"
        print(row)
    print("-" * (28 + 18 * len(configs)))
    for k, res in configs.items():
        c = sum(res[f][0] == gt[f] for f in files)
        print(f"{k:<12}: {c}/{len(files)} = {c/len(files)*100:.1f}%")


if __name__ == "__main__":
    main()
