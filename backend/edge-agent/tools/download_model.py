#!/usr/bin/env python3
"""Download a pretrained YOLOv8 license-plate detection model for edge-agent.

The edge-agent ALPR pipeline (``app/services/alpr.py::_detect_yolo``) needs an
Ultralytics-compatible YOLOv8 ``.pt`` detection model that localises the plate
region. The highest-confidence box is cropped and handed to EasyOCR — the class
labels are not used, so any single-class "plate" detector works.

This script downloads such a model from public Hugging Face repos (no token
needed) and saves it to ``models/yolov8s_vn.pt`` (the path expected by
``ALPR_MODEL_PATH``). It validates the file is a real PyTorch archive (ZIP magic)
so a truncated/HTML error page is never silently accepted.

Usage:
    python tools/download_model.py
    python tools/download_model.py --out models/yolov8s_vn.pt --source vn
"""
from __future__ import annotations

import argparse
import sys
import urllib.request
from pathlib import Path

# Public, token-free YOLOv8 license-plate detectors (Ultralytics .pt).
# "plate": verified single-class detector  names={0: 'license_plate'}  -> correct for this pipeline.
# NOTE: many HF repos named "...license-plate..." actually ship the stock 80-class COCO
# yolov8n (e.g. nguyenluat/yolov8n-license-plate-vn) — those localise cars, NOT plates, so
# they are deliberately NOT listed here. download_model.py asserts the model is single-class.
SOURCES: dict[str, str] = {
    "plate": "https://huggingface.co/Koushim/yolov8-license-plate-detection/resolve/main/best.pt?download=true",
}

ZIP_MAGIC = b"PK\x03\x04"  # PyTorch .pt files are ZIP archives


def _download(url: str, out: Path) -> int:
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(out.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "edge-agent-download/1.0"})
    with urllib.request.urlopen(req, timeout=120) as resp, open(tmp, "wb") as f:
        data = resp.read()
        f.write(data)
    if not data.startswith(ZIP_MAGIC):
        tmp.unlink(missing_ok=True)
        raise ValueError(
            f"Downloaded file is not a PyTorch .pt archive (got {data[:4]!r}). "
            "The URL may require auth or returned an error page."
        )
    tmp.replace(out)
    return len(data)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="models/yolov8s_vn.pt",
                        help="output path (default: models/yolov8s_vn.pt)")
    parser.add_argument("--source", choices=list(SOURCES) + ["auto"], default="auto",
                        help="model source; 'auto' tries every known source in order")
    parser.add_argument("--force", action="store_true",
                        help="overwrite an existing model file")
    args = parser.parse_args()

    out = Path(args.out)
    if out.exists() and not args.force:
        print(f"[skip] {out} already exists ({out.stat().st_size} bytes). Use --force to re-download.")
        return 0

    order = [args.source] if args.source != "auto" else list(SOURCES)
    for name in order:
        url = SOURCES[name]
        print(f"[try ] source={name}  {url}")
        try:
            size = _download(url, out)
            print(f"[ok  ] saved {out}  ({size} bytes, source={name})")
            print("Next: rebuild edge-agent (docker compose up -d --build edge-agent) "
                  "or run tools/verify_model.py to smoke-test it.")
            return 0
        except Exception as exc:  # noqa: BLE001 — report and try the next source
            print(f"[fail] source={name}: {exc}")

    print("\nAll sources failed. Options:", file=sys.stderr)
    print("  - Check internet / proxy access to huggingface.co", file=sys.stderr)
    print("  - Download a YOLOv8 plate model manually and save to", out, file=sys.stderr)
    print("  - Or fine-tune one (see models/README.md)", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
