# Fine-tune model nhận-ký-tự biển VN (cải thiện OCR)

Mục tiêu: train model **YOLOv8 nhận diện từng ký tự** trên biển số VN để thay EasyOCR — dập các lỗi
nhầm look-alike (`3↔8`, `0↔O`...) mà OCR chuỗi hay mắc. Pipeline edge **đã sẵn sàng nhận model này**
(`AlprService` detector `yolo_char`); ở đây chỉ lo phần **tạo data + train + đo**.

## Vì sao hướng này
- Mỗi ký tự là 1 object, ràng buộc đúng bộ ký tự biển VN → chính xác hơn OCR tổng quát.
- Model off-the-shelf đã thử (MagicXuanTung) **tệ hơn EasyOCR (14% vs 86%)** → phải tự train cho hợp data VN.
- Máy có **RTX 4060 8GB** → đủ sức train.

## Nút thắt & cách gỡ: DỮ LIỆU
Train cần ảnh có nhãn **bbox từng ký tự** — gán tay rất tốn. Hai nguồn:
1. **Tổng hợp (gen_synthetic.py)** — sinh hàng nghìn ảnh biển + nhãn YOLO **tự động**, miễn phí, ngay lập tức. Gỡ cold-start.
2. **Ảnh thật** (vài trăm ảnh kiểu cam cổng) — để **fine-tune + ĐO**. Quan trọng vì có *domain gap*: model train toàn ảnh tổng hợp thường chưa khớp ảnh chụp thật.

> Khuyến nghị: train nền bằng data tổng hợp (nhiều), rồi fine-tune/đo trên ảnh thật. Không có ảnh thật thì không biết model có tốt hơn EasyOCR hay không.

## ⚠️ Trước tiên: cài PyTorch bản CUDA (bắt buộc để dùng RTX 4060)
`.venv-alpr` hiện đang là **torch CPU** (`torch.cuda.is_available() == False`) → train sẽ chạy CPU, rất chậm.
Cài bản CUDA (vd CUDA 12.1) vào venv:
```bash
.venv-alpr/Scripts/python.exe -m pip install --index-url https://download.pytorch.org/whl/cu121 \
    torch torchvision --upgrade
# kiểm tra:
.venv-alpr/Scripts/python.exe -c "import torch;print(torch.cuda.is_available())"   # phải True
```
> Nếu vẫn `False`: cập nhật driver NVIDIA, hoặc đổi `cu121` cho khớp CUDA máy.

## 4 bước
```bash
cd backend/edge-agent

# 1) Sinh data tổng hợp (vd 5000 ảnh)
.venv-alpr/Scripts/python.exe training/gen_synthetic.py --out training/data --n 5000 --val-split 0.1

# 2) Train trên GPU (RTX 4060). yolov8n nhanh; đổi --base yolov8s.pt nếu cần chính xác hơn
.venv-alpr/Scripts/python.exe training/train_chars.py --data training/data \
    --epochs 80 --imgsz 416 --batch 32 --device 0

# 3) Đo end-to-end trên ẢNH THẬT có nhãn, so với EasyOCR
.venv-alpr/Scripts/python.exe training/eval_chars.py \
    --model runs/detect/vn_chars/weights/best.pt \
    --images-dir samples/real --labels samples/real/labels.csv

# 4) CHỈ KHI eval cao hơn EasyOCR -> triển khai:
cp runs/detect/vn_chars/weights/best.pt models/yolov8_vn_chars.pt
#   rồi đặt ALPR_DETECTOR: "yolo_char" trong docker-compose.yml, rebuild edge-agent
```

## Fine-tune bằng ảnh thật (khi đã có)
- Gán nhãn bbox ký tự bằng Roboflow / LabelImg / CVAT, **export YOLO format**, đúng thứ tự lớp như `dataset.yaml`.
- Đổ vào `training/data/images/{train,val}` + `training/data/labels/{train,val}` (trộn chung với data tổng hợp), rồi chạy lại bước 2.
- Hoặc train tiếp từ best.pt: `--base runs/detect/vn_chars/weights/best.pt`.

## Lưu ý thật
- `gen_synthetic.py` sinh ảnh TRỤC THẲNG, bbox chính xác; xoay/mờ/sáng để YOLO tự augment lúc train.
- Bộ lớp ký tự: `0-9` + `ABCDEFGHKLMNPSTUVXYZ` (bỏ I,J,O,Q,R,W — biển VN không dùng). Khớp `gen_synthetic.CLASSES` và pipeline.
- Model tổng-hợp-thuần có thể CHƯA thắng EasyOCR trên ảnh thật → cần ảnh thật để fine-tune. Đừng deploy nếu `eval_chars.py` không cao hơn.
- Chất lượng **camera** (gần, chính diện, đủ sáng) vẫn là yếu tố lớn nhất, không model nào bù hết được.
