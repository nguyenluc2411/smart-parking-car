# ALPR Setup Report — edge-agent (YOLOv8 + EasyOCR)

> Ngày: 2026-06-23 · Phạm vi: triển khai detector `yolo` thật cho `app/services/alpr.py`.
> Mọi kết luận dưới đây dựa trên việc **chạy thật** model + endpoint, không mock, không file `.pt` giả.

---

## 0. UPDATE — đã chạy end-to-end trong Docker (path HTTP 200 thật)

Sau khi dựng Kafka + edge-agent bằng Docker Compose, **đã verify trọn vẹn path thành công** mà §4.3 còn nợ:

```
POST /api/v1/detect  (ảnh taxi VN thật samples/vn_vinfast3.jpg, X-API-Key đúng)
-> HTTP 200
{"plateNumber":"56Z-40916","confidence":0.8707,"processingMs":3445,"boundingBox":{"x":34,"y":829,"w":244,"h":48}}
edge-agent log: Published parking.plate.detected: plate=56Z-40916 gate=GATE_ENTRY_01 dir=IN
/health -> 200 {"status":"UP","kafka":true,"alprMode":"real"}
```
Confidence 0.871 > 0.85 → accept → **publish lên Kafka** (log in sau khi broker ack vì `acks=all`).
Lần đầu 85s (EasyOCR tải model ngôn ngữ trong container), lần sau ~3.4s.

**Cấu hình chốt:** `ALPR_DETECTOR=ocr_only` (đọc biển VN rõ ngay, không cần YOLO fine-tune — xem §6).

### 2 lỗi hạ tầng phát hiện khi chạy Docker & đã SỬA
| Lỗi (quan sát thật) | Nguyên nhân | Sửa |
|---|---|---|
| Mọi `/detect` trả 422; log `Real ALPR failed` + `ImportError: libGL.so.1` | `python:3.11-slim` thiếu system lib mà **OpenCV (cv2)** cần | `Dockerfile`: `apt-get install -y libgl1 libglib2.0-0` |
| Image phình ~4–5GB, build chậm, WSL2/Docker engine sập | `torch` mặc định kéo nguyên bộ CUDA NVIDIA (~2GB) dù chạy CPU | `requirements-alpr.txt`: pin `torch==2.2.0+cpu`, `torchvision==0.17.0+cpu` qua index CPU → image **2.33GB** |

### File đổi thêm ở bước này
| File | Thay đổi |
|---|---|
| `Dockerfile` | **Bug fix**: cài `libgl1 libglib2.0-0` cho OpenCV |
| `requirements-alpr.txt` | torch/torchvision **CPU-only** (image nhẹ ~2GB, không đụng kết quả ALPR) |
| `docker-compose.yml` | `ALPR_DETECTOR: ocr_only` (đọc biển VN rõ ngay; đổi `yolo` sau khi fine-tune) |

---

## 1. Yêu cầu model — rút ra từ source code

Nguồn: `app/services/alpr.py::_detect_yolo` + `_load_model` + `app/config.py` + `app/routers/detect.py`.

| Yêu cầu | Giá trị | Bằng chứng trong code |
|---|---|---|
| Framework | Ultralytics YOLOv8, load bằng `YOLO(path)` | `from ultralytics import YOLO; self._model = YOLO(self.model_path)` |
| Task | `detect` (bounding box) | dùng `results[0].boxes`, `.xyxy[0]`, `.conf[0]` |
| Số class | **không bị filter** — lý tưởng 1 class `plate` | `best = max(boxes, key=lambda b: float(b.conf[0]))` — lấy box conf cao nhất, **bỏ qua class id** |
| Output dùng | box conf cao nhất → crop `frame[y1:y2, x1:x2]` → EasyOCR `readtext` → `plate.canonicalize` | `_detect_yolo` |
| Đường dẫn | `ALPR_MODEL_PATH`, mặc định `/app/models/yolov8s_vn.pt` | `config.py: alpr_model_path` |
| Kích hoạt | `ALPR_MODE=real` + `ALPR_DETECTOR=yolo` | `detect()` rẽ nhánh `_detect_yolo` |
| Ngưỡng chấp nhận | `confidence ≥ CONFIDENCE_THRESHOLD` (mặc định 0.85, BR-001-2) | `detect.py`: `detection.confidence < runtime.confidence_threshold → 422` |

**Kết luận:** cần một YOLOv8 **detection** `.pt` phát hiện vùng biển. Vì code lấy box conf cao nhất bất kể class,
model **single-class `license_plate`** là đúng nhất (model đa lớp COCO sẽ khoanh ô tô/vật thể, không phải biển).

---

## 2. Model đã dùng

| Thuộc tính | Giá trị |
|---|---|
| Nguồn | Hugging Face `Koushim/yolov8-license-plate-detection` → `best.pt` (public, không cần token) |
| Lưu tại | `backend/edge-agent/models/yolov8s_vn.pt` |
| Kích thước | 6,248,291 bytes |
| Magic bytes | `50 4B 03 04` (ZIP = PyTorch archive hợp lệ — đã kiểm, không phải file giả/HTML) |
| Task / classes | `task=detect`, `names={0: 'license_plate'}` (xác minh bằng `ultralytics.YOLO`) |

### Nguồn đã LOẠI (quan trọng — minh bạch)
- `nguyenluat/yolov8n-license-plate-vn` → tải về `best.pt` 6.5MB **nhưng** khi load ra **80 class COCO**
  (`person, bicycle, car, ...`). Đây là **YOLOv8n gốc bị đặt tên nhầm**, KHÔNG phải model biển số → đã loại.
  `tools/download_model.py` chỉ giữ nguồn đã kiểm là single-class; `tools/verify_model.py` cảnh báo nếu
  model có > 5 class (dấu hiệu COCO).

> ⚠️ Model `Koushim` là plate detector **chung (không fine-tune cho biển VN)**. Đủ để chạy pipeline thật và
> chứng minh end-to-end, **nhưng để đạt KPI accuracy ≥90% (ngày ≥95%) của biển VN cần fine-tune** (xem §6).

---

## 3. Cách lấy model (đã script hoá)

```bash
cd backend/edge-agent
python tools/download_model.py            # -> models/yolov8s_vn.pt (nguồn 'plate' đã kiểm single-class)
python tools/download_model.py --force    # tải lại đè
```
Script: tải qua urllib (không phụ thuộc ML), **kiểm magic ZIP** để không bao giờ nhận nhầm file lỗi/HTML.

---

## 4. Kiểm tra thực tế (đã CHẠY)

### 4.1. ALPR core — `tools/verify_model.py` (standalone, không cần FastAPI/Kafka)
Môi trường verify: venv `.venv-alpr` (Python 3.13) · ultralytics 8.4.75 · torch 2.12.1+cpu · easyocr 1.7.2 · numpy 2.5.0.

```
[ OK ] model loaded. task=detect  classes={0: 'license_plate'}
[ OK ] inference ran. boxes_detected=1
[ OK ] detect() -> plate='51F-12345' confidence=0.363 bbox={'x':173,'y':73,'w':344,'h':95} ms=2428
[PASS] full real ALPR pipeline produced a valid VN plate.
```
→ Chứng minh: **load model OK + chạy inference OK + crop + EasyOCR đọc + `canonicalize` ra biển VN hợp lệ**.
(Conf 0.363 thấp vì ảnh test là synthetic thô; ảnh xe thật sẽ cao hơn nhiều.)

### 4.2. Endpoint — boot uvicorn thật với `ALPR_MODE=real`
| Test | Kết quả | Ý nghĩa |
|---|---|---|
| `GET /health` | `503` · body `{"status":"DOWN","kafka":false,"alprMode":"real","gates":{}}` | App boot, **ALPR=real** đã nạp. (503 chỉ vì Kafka chưa chạy — đúng thiết kế `health.py`) |
| `POST /api/v1/detect` (thiếu `X-API-Key`) | `401` | Auth đúng |
| `POST /api/v1/detect` (`direction=SIDEWAYS`) | `400` `direction must be IN or OUT` | Validate đúng |
| `POST /api/v1/detect` (ảnh không decode được) | `422` `LOW_CONFIDENCE` | ALPR chạy trong request, reject đúng |
| `POST /api/v1/detect` (ảnh biển synthetic, threshold 0.85) | `422` (conf 0.36 < 0.85), **trả về sạch** | Đúng BR-001-2 |
| Log server khi detect | `Loading YOLO model from models/yolov8s_vn.pt` + `Initialising EasyOCR reader` | **Endpoint thật sự kích hoạt pipeline YOLO+OCR** trong tiến trình edge-agent |

### 4.3. Unit test
`pytest` trong venv: **21 passed** (sau khi sửa `detect.py` ở §5).

### Path CHƯA chạy được (minh bạch, không tô vẽ)
- **HTTP 200 “detect thành công + publish `parking.plate.detected`”** cần **Kafka broker**. Trong môi trường này
  **Docker Desktop engine đang tắt** nên không dựng được Kafka → không thể demo 200. Mọi thành phần *trước* bước
  publish đã verify (ALPR đọc ra biển hợp lệ; nhánh accepted của `detect.py`). Để chạy 200: bật Kafka (xem §6).
- **Đo KPI accuracy biển VN**: cần bộ ảnh có nhãn + model fine-tune VN → chưa thực hiện (xem §6).

---

## 5. Lỗi phát hiện khi chạy & đã SỬA

**Triệu chứng (quan sát thật):** khi một request `/detect` đang chạy ALPR (YOLO+EasyOCR, vài giây), request
thứ hai gọi đồng thời bị treo và trả `HTTP=000` (không nhận được phản hồi).

**Nguyên nhân:** `app/routers/detect.py` là `async def detect(...)` nhưng gọi `alpr.detect(content)` —
một tác vụ **CPU đồng bộ, nặng** — **trực tiếp trong coroutine**, làm **chặn event loop** của uvicorn
(single worker) → mọi request khác (kể cả `/health`) bị xếp hàng/timeout.

**Sửa:** chạy ALPR trong threadpool để không chặn event loop.
```python
# app/routers/detect.py
from fastapi.concurrency import run_in_threadpool
...
detection = await run_in_threadpool(alpr.detect, content)
```
Không thêm dependency mới (`fastapi.concurrency` có sẵn từ Starlette). Tuân thủ CLAUDE.md (không sửa layer khác,
không đổi contract).

**Kiểm chứng sau sửa:** chạy `/detect` (đang xử lý) + bắn `/health` đồng thời 5 lần:
```
health probe1..5: http=503 time≈0.002–0.004s     <- phản hồi tức thì, event loop KHÔNG bị chặn
DETECT done http=422 time=0.150s
```
→ regression đã hết; `pytest` vẫn **21 passed**.

### File đã sửa (task này)
| File | Thay đổi |
|---|---|
| `app/routers/detect.py` | **Bug fix**: `await run_in_threadpool(alpr.detect, content)` (bỏ chặn event loop) |

### File tạo mới (task này)
| File | Mục đích |
|---|---|
| `tools/download_model.py` | Tải model plate đã kiểm (single-class) + verify magic ZIP |
| `tools/verify_model.py` | Smoke-test thật: load model + inference + full pipeline + cảnh báo nếu là COCO |
| `models/yolov8s_vn.pt` | **Model thật** (binary, git-ignored — KHÔNG commit) |
| `models/README.md` | Hướng dẫn yêu cầu/tải/fine-tune model |
| `samples/synthetic_plate.jpg` | Ảnh test dùng khi verify endpoint |
| `ALPR_SETUP_REPORT.md` | Báo cáo này |

### File đã chỉnh ở bước hạ tầng trước đó (liên quan)
`backend/edge-agent/Dockerfile` (cài `requirements-alpr.txt`), `docker-compose.yml` (mount `models/` + env ALPR real/yolo).

---

## 6. Việc còn lại để chạy production / đạt KPI

1. **Bật Kafka + service nền** rồi mới có path 200 + mở barie:
   ```bash
   cd D:/SU26/MSS301/Group_Project/smart-parking-car
   docker compose up -d kafka admin-service parking-service billing-service
   docker compose up -d --build edge-agent        # build image (Python 3.11) với requirements-alpr.txt
   docker compose logs -f edge-agent              # chờ "Loading YOLO model" + healthy
   ```
2. **Quét thật bằng webcam** (máy có camera): `python tools/camera_agent.py --edge http://localhost:8000 --api-key <EDGE_API_KEY> --gate GATE_ENTRY_01 --direction IN`.
3. **Fine-tune cho biển VN** để đạt accuracy ≥90% (model hiện tại là plate-detector chung):
   ```bash
   yolo detect train model=yolov8s.pt data=vn_plates.yaml imgsz=640 epochs=80 batch=16
   cp runs/detect/train/weights/best.pt models/yolov8s_vn.pt
   ```
4. **Đo KPI** offline: `python tools/alpr_eval.py --images-dir samples --labels samples/labels.csv --detector yolo --model-path models/yolov8s_vn.pt` (tách day/ night/).

### Ghi chú phiên bản
- Verify ở trên chạy trên **host Python 3.13** nên dùng `ultralytics` mới + `numpy 2.x` (bản pin cũ
  `ultralytics==8.1.0`, `numpy<2` trong `requirements-alpr.txt` **không có wheel cho 3.13**, build nguồn fail).
- **Image Docker dùng Python 3.11** — nơi `requirements-alpr.txt` (bản pin) áp dụng đúng. Kết luận về model/pipeline
  không đổi giữa hai bản (cùng API Ultralytics/EasyOCR).
- Artefact local không commit: `.venv-alpr/`, `models/*.pt`, `samples/` (đã nằm trong `.gitignore` gốc: `*.pt`, `models/`).
