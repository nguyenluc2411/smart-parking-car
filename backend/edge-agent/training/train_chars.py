"""Train/fine-tune YOLOv8 nhận-ký-tự biển VN (chạy trên RTX 4060).

Tự dựng dataset.yaml với đường dẫn TUYỆT ĐỐI (tránh gotcha datasets_dir của ultralytics) rồi train.
Class names = CLASSES trong gen_synthetic.py → model embed đúng tên ký tự, khớp pipeline edge
(AlprService detector="yolo_char" đọc model.names).

    # 1) sinh data tổng hợp
    python training/gen_synthetic.py --out training/data --n 5000
    # 2) train
    python training/train_chars.py --data training/data --epochs 80 --imgsz 416 --batch 32 --device 0
    # weights tốt nhất: runs/detect/<name>/weights/best.pt
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_synthetic import CLASSES  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="training/data", help="thư mục có images/ + labels/")
    ap.add_argument("--base", default="yolov8n.pt", help="model nền (n nhanh; s chính xác hơn)")
    ap.add_argument("--epochs", type=int, default=80)
    ap.add_argument("--imgsz", type=int, default=416)
    ap.add_argument("--batch", type=int, default=32)
    ap.add_argument("--device", default="0", help="0 = GPU đầu tiên; 'cpu' nếu không có GPU")
    ap.add_argument("--name", default="vn_chars")
    args = ap.parse_args()

    from ultralytics import YOLO  # import muộn để --help nhanh

    data_dir = Path(args.data).resolve()
    if not (data_dir / "images" / "train").exists():
        sys.exit(f"Không thấy {data_dir}/images/train — chạy gen_synthetic.py trước.")

    yaml_path = data_dir.parent / "dataset.auto.yaml"
    yaml.safe_dump(
        {"path": str(data_dir), "train": "images/train", "val": "images/val",
         "names": {i: c for i, c in enumerate(CLASSES)}},
        open(yaml_path, "w", encoding="utf-8"), allow_unicode=True, sort_keys=False)

    model = YOLO(args.base)
    model.train(data=str(yaml_path), epochs=args.epochs, imgsz=args.imgsz,
                batch=args.batch, device=args.device, name=args.name)
    best = Path("runs/detect") / args.name / "weights" / "best.pt"
    print(f"\n✅ Xong. Weights: {best}")
    print("Triển khai: copy best.pt -> backend/edge-agent/models/yolov8_vn_chars.pt,")
    print("            đặt ALPR_DETECTOR=yolo_char trong docker-compose, rebuild edge.")


if __name__ == "__main__":
    main()
