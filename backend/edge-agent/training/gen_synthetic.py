"""Sinh DỮ LIỆU TỔNG HỢP biển số VN + nhãn YOLO (char-detection) — gỡ nút thắt gán nhãn tay.

Mỗi ảnh: 1 biển 1 dòng (ô tô) gồm 2 số tỉnh + 1-2 chữ series + 4-5 số serial, vẽ trên nền biển
(trắng) đặt trên canvas nền ngẫu nhiên. Nhãn YOLO ghi bbox CHO TỪNG KÝ TỰ alphanumeric (bỏ qua
dấu '-'). Ảnh để TRỤC THẲNG, bbox chính xác; phép xoay/mờ/sáng để YOLO tự augment lúc train.

Class id = chỉ số trong CLASSES (khớp dataset.yaml). Chạy:
    python training/gen_synthetic.py --out training/data --n 4000 --val-split 0.1
"""
from __future__ import annotations

import argparse
import os
import random

from PIL import Image, ImageDraw, ImageFont

# Bộ ký tự biển VN (không I, J, O, Q, R, W — dễ nhầm số). Class id = vị trí trong list này.
DIGITS = "0123456789"
LETTERS = "ABCDEFGHKLMNPSTUVXYZ"
CLASSES = list(DIGITS) + list(LETTERS)
CLASS_ID = {c: i for i, c in enumerate(CLASSES)}

FONTS = [r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\consolab.ttf"]


def random_plate() -> str:
    prov = f"{random.randint(0, 9)}{random.randint(0, 9)}"
    series = "".join(random.choice(LETTERS) for _ in range(random.choice([1, 1, 1, 2])))
    serial = "".join(random.choice(DIGITS) for _ in range(random.choice([4, 5, 5, 5])))
    return f"{prov}{series}-{serial}"


def render(plate: str):
    """Vẽ biển, trả (PIL image, list[(class_id, x0,y0,x1,y1)]) — bbox theo pixel ảnh cuối."""
    font = ImageFont.truetype(random.choice(FONTS), random.randint(64, 88))
    pad_x, pad_y, gap = 26, 18, random.randint(4, 12)
    # đo từng ký tự
    dummy = ImageDraw.Draw(Image.new("RGB", (10, 10)))
    glyphs, w = [], pad_x
    for ch in plate:
        bb = dummy.textbbox((0, 0), ch, font=font)
        gw, gh = bb[2] - bb[0], bb[3] - bb[1]
        glyphs.append((ch, w, gw, gh, bb))
        w += gw + (gap if ch != "-" else gap + 6)
    plate_w = w + pad_x - gap
    plate_h = max(g[3] for g in glyphs) + 2 * pad_y

    # nền biển (trắng ngà), bo góc
    plate_img = Image.new("RGB", (plate_w, plate_h), (random.randint(225, 255),) * 3)
    d = ImageDraw.Draw(plate_img)
    ink = (random.randint(0, 40),) * 3
    boxes = []
    for ch, x, gw, gh, bb in glyphs:
        y = (plate_h - gh) // 2
        d.text((x - bb[0], y - bb[1]), ch, font=font, fill=ink)
        if ch in CLASS_ID:
            boxes.append((CLASS_ID[ch], x, y, x + gw, y + gh))

    # đặt biển lên canvas nền ngẫu nhiên (mô phỏng ngữ cảnh quanh biển)
    m = random.randint(20, 90)
    canvas = Image.new("RGB", (plate_w + 2 * m, plate_h + 2 * m),
                       tuple(random.randint(40, 200) for _ in range(3)))
    canvas.paste(plate_img, (m, m))
    boxes = [(c, x0 + m, y0 + m, x1 + m, y1 + m) for c, x0, y0, x1, y1 in boxes]
    return canvas, boxes


def to_yolo(boxes, W, H):
    out = []
    for c, x0, y0, x1, y1 in boxes:
        cx, cy = (x0 + x1) / 2 / W, (y0 + y1) / 2 / H
        out.append(f"{c} {cx:.6f} {cy:.6f} {(x1 - x0) / W:.6f} {(y1 - y0) / H:.6f}")
    return "\n".join(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="training/data")
    ap.add_argument("--n", type=int, default=4000)
    ap.add_argument("--val-split", type=float, default=0.1)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    random.seed(args.seed)

    for split in ("train", "val"):
        os.makedirs(f"{args.out}/images/{split}", exist_ok=True)
        os.makedirs(f"{args.out}/labels/{split}", exist_ok=True)

    n_val = int(args.n * args.val_split)
    for i in range(args.n):
        split = "val" if i < n_val else "train"
        img, boxes = render(random_plate())
        if not boxes:
            continue
        name = f"plate_{i:06d}"
        img.save(f"{args.out}/images/{split}/{name}.jpg", quality=92)
        with open(f"{args.out}/labels/{split}/{name}.txt", "w") as f:
            f.write(to_yolo(boxes, img.width, img.height))
    print(f"Đã sinh {args.n} ảnh ({n_val} val) vào {args.out}/ — {len(CLASSES)} lớp ký tự")


if __name__ == "__main__":
    main()
