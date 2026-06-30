# Triển khai ALPR thật bằng YOLO (yolov8s_vn.pt) — Quick Deploy

> Mục tiêu: bật detector `yolo` (YOLOv8 khoanh vùng biển + EasyOCR đọc) thay cho `simulate`/`ocr_only`.
> Code đã sẵn (`app/services/alpr.py::_detect_yolo`) — chỉ thiếu **file model** + 2 chỉnh hạ tầng.
> Tài liệu nền: [`RESEARCH.md`](./RESEARCH.md). Hợp đồng API/kiến trúc: `../../docs/`, `../../CLAUDE.md`.

## TL;DR — checklist 5 việc
1. Có file model `yolov8s_vn.pt` → đặt vào `backend/edge-agent/models/`.
2. Sửa `Dockerfile`: cài thêm `requirements-alpr.txt` (deps ML).
3. Sửa `docker-compose.yml` (service `edge-agent`): mount `models/` + set env ALPR real/yolo.
4. `docker compose up -d --build edge-agent` → chờ `/health` UP.
5. Chạy `tools/camera_agent.py` trên máy có webcam → quét thật.

Lưu ý: cả 3 service `admin/parking/billing` + `kafka` phải đang chạy (xem `../../CLAUDE.md` §11).
`EDGE_API_KEY` lấy từ `.env` gốc (hiện là placeholder `change_me_edge_api_key_min_32_chars` — nên đổi).

---

## Bước 1 — Lấy model `yolov8s_vn.pt`
Repo KHÔNG kèm model (binary nặng + cần train cho biển VN). Chọn 1 trong 2:

- **(a) Tải pretrained** "YOLOv8 license plate detection" (Roboflow Universe / HuggingFace / GitHub).
  Đổi tên file `.pt` thành `yolov8s_vn.pt`.
- **(b) Fine-tune** YOLOv8 trên dataset biển số VN (gắn nhãn bbox 1 lớp `plate`):
  ```bash
  pip install ultralytics
  yolo detect train model=yolov8s.pt data=vn_plates.yaml imgs=640 epochs=80
  # kết quả: runs/detect/train/weights/best.pt  -> đổi tên thành yolov8s_vn.pt
  ```

Đặt model vào (tạo thư mục nếu chưa có):
```bash
mkdir -p backend/edge-agent/models
cp <đường-dẫn-model>.pt backend/edge-agent/models/yolov8s_vn.pt
```
> `models/` nên được .gitignore (đừng commit binary). Kiểm tra `backend/edge-agent/.dockerignore`/`.gitignore`.

## Bước 2 — Cài deps ML vào image edge-agent
Sửa `backend/edge-agent/Dockerfile`, thêm NGAY SAU dòng `RUN pip install ... requirements.txt`:
```dockerfile
# Real ALPR (YOLOv8 + EasyOCR) — kéo torch/ultralytics/easyocr, ~vài GB, build lâu lần đầu.
COPY requirements-alpr.txt .
RUN pip install --no-cache-dir -r requirements-alpr.txt
```
(Model KHÔNG copy vào image — nạp qua volume ở Bước 3 để tách binary khỏi image.)

## Bước 3 — Mount model + bật env (docker-compose.yml, service `edge-agent`)
Trong block `edge-agent:` thêm `volumes` + `environment` (giữ nguyên `build/ports/depends_on/...`):
```yaml
  edge-agent:
    # ...giữ nguyên build, image, container_name, ports, depends_on, healthcheck, networks...
    volumes:
      - ./backend/edge-agent/models:/app/models:ro
    environment:
      ALPR_MODE: "real"
      ALPR_DETECTOR: "yolo"                         # yolo = YOLOv8 vùng biển + OCR
      ALPR_MODEL_PATH: "/app/models/yolov8s_vn.pt"
      ALPR_OCR_LANGUAGES: "en"
      ALPR_OCR_GPU: "false"                         # "true" nếu container có CUDA
      CONFIDENCE_THRESHOLD: "0.85"                  # BR-001-2
```
> `edge-agent` đã có `env_file: [.env]`; có thể đặt các biến trên trong `.env` gốc thay vì `environment:`
> (cùng tác dụng — config.py đọc qua biến môi trường, không phân biệt nguồn).
> Các key này khớp `Settings` trong `app/config.py` (alpr_mode, alpr_detector, alpr_model_path, ...).

## Bước 4 — Build & chạy
```bash
cd D:/SU26/MSS301/Group_Project/smart-parking-car
docker compose up -d --build edge-agent
docker compose logs -f edge-agent          # chờ healthy; lần detect đầu EasyOCR tải model ngôn ngữ
# verify cấu hình đã thật:
curl -s localhost:8000/api/v1/config -H "X-API-Key: change_me_edge_api_key_min_32_chars"
```
Trong log khi detect lần đầu phải thấy `Loading YOLO model from /app/models/yolov8s_vn.pt`
và `Initialising EasyOCR reader`. Nếu thấy biển giả (51F-12345…) nghĩa là vẫn ở `simulate` → kiểm tra env.

## Bước 5 — Quét thật bằng webcam (chạy trên MÁY có camera, không trong container)
```bash
cd backend/edge-agent
pip install opencv-python requests
python tools/camera_agent.py ^
  --edge http://localhost:8000 ^
  --api-key change_me_edge_api_key_min_32_chars ^
  --gate GATE_ENTRY_01 --direction IN
#   SPACE = chụp & gửi · q = thoát · --interval 3 = tự chụp mỗi 3s · --expect 51F-123.45 = chấm accuracy
#   --source path/to/video.mp4  -> chạy lại trên video đã quay
```
Luồng: chụp frame → `POST /api/v1/detect` → edge publish `parking.plate.detected`
→ parking tạo session + publish `parking.gate.command` → edge mở barie.
Client in `plate / confidence / camera→gate latency` và ghi `camera_agent_log.csv`.

## Verify nhanh end-to-end
```bash
# session vừa tạo (cần token OPERATOR/ADMIN từ admin-service):
TOKEN=$(curl -s -X POST localhost:8083/api/v1/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"ChangeMe123!"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -s "localhost:8081/api/v1/sessions?size=5" -H "Authorization: Bearer $TOKEN"
docker compose logs edge-agent --since 2m | grep -E "GATE|plate"
```

## Đo KPI (theo CLAUDE.md §2)
- **Latency camera→barie ≤ 3s**: cột `cam_to_gate_s` trong `camera_agent_log.csv`.
- **Accuracy ≥ 90% (ngày ≥95% / đêm ≥90%)**: đo hàng loạt offline, không cần barie:
  ```bash
  # samples/labels.csv:  image_filename,true_plate   (vd: 51f_day_01.jpg,51F-123.45)
  python tools/alpr_eval.py --images-dir samples --labels samples/labels.csv --detector yolo \
    --model models/yolov8s_vn.pt
  # tách thư mục day/ và night/ + 2 file labels để đo riêng KPI ngày/đêm.
  ```

## Cách nhanh (không Docker) để lặp ML — tuỳ chọn
Image mặc định là bản light; muốn thử ML nhanh không build lại image, chạy edge-agent ngoài Docker
(vẫn cần Kafka + parking-service đang chạy qua docker compose):
```bash
cd backend/edge-agent
pip install -r requirements.txt -r requirements-alpr.txt
set ALPR_MODE=real&& set ALPR_DETECTOR=yolo&& set ALPR_MODEL_PATH=models/yolov8s_vn.pt&& set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
uvicorn app.main:app --port 8000
```

## Khắc phục sự cố
| Triệu chứng | Nguyên nhân / xử lý |
|---|---|
| Trả biển giả `51F-12345…` | Vẫn `ALPR_MODE=simulate` → kiểm tra env đã vào container (`docker compose exec edge-agent env | grep ALPR`) |
| Log `Real ALPR failed` / không ra biển | Model lỗi/đường dẫn sai, hoặc thiếu deps ML (chưa cài `requirements-alpr.txt`) |
| `/api/v1/detect` trả **422** | OCR không đọc được biển hợp lệ VN regex → ảnh mờ/nghiêng, hoặc biển ngoại giao (dùng `POST /sessions/manual-entry`) |
| Build rất lâu / hết dung lượng | `requirements-alpr.txt` kéo torch nặng — cần mạng + đĩa trống vài GB |
| Accuracy thấp | Model chưa fine-tune cho biển VN → train lại (Bước 1b); kiểm `app/plate.py::canonicalize` cho sửa ký tự nhầm |

---
*Ghi chú: `tools/` chỉ phục vụ nghiên cứu/đo đạc, không ảnh hưởng service production. `ocr_only` (không cần model)
là đường chạy-ngay nếu chưa có YOLO — xem `RESEARCH.md`.*
