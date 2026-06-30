# Edge-agent — Research / real-ALPR guide

Mục tiêu RBL: **AI tự động hoá toàn bộ luồng** (camera → nhận diện → mở barie), con người chỉ can
thiệp ngoại lệ (manual-bypass / REQUIRES_ATTENTION). Tài liệu này hướng dẫn chạy **ALPR thật** +
**đo KPI thật**.

## KPI cần chứng minh (CLAUDE.md §2)
- Nhận diện biển số ≥ **90%** (ngày ≥ 95%, đêm ≥ 90%) → đo bằng `tools/alpr_eval.py`.
- Latency camera → barie mở ≤ **3 giây** → đo bằng `tools/camera_agent.py` (cột `cam_to_gate_s`).
- Uptime ≥ 99% → Prometheus/Grafana.

## 1. Bật ALPR thật
`.env` (hoặc env của container edge-agent):
```
ALPR_MODE=real
ALPR_DETECTOR=ocr_only          # ocr_only (không cần model YOLO) | yolo (chính xác hơn)
ALPR_MODEL_PATH=/app/models/yolov8s_vn.pt   # chỉ dùng khi ALPR_DETECTOR=yolo
ALPR_OCR_LANGUAGES=en
ALPR_OCR_GPU=false              # true nếu có CUDA
CONFIDENCE_THRESHOLD=0.85
```
Cài deps ML (nặng, vài GB; lần đầu EasyOCR tải model ngôn ngữ):
```
pip install -r requirements-alpr.txt
```
> **`ocr_only`**: chạy được NGAY, chỉ cần `easyocr` (không cần file model YOLO). Độ chính xác thấp
> hơn vì OCR cả khung. Dùng để bắt đầu đo, rồi nâng lên `yolo` khi có model.
>
> **`yolo`**: cần file model phát hiện biển tại `ALPR_MODEL_PATH`. Lấy model bằng cách (a) tải một
> YOLOv8 license-plate pretrained, hoặc (b) fine-tune trên dataset biển VN. Repo CHƯA kèm model.

Chạy edge-agent ngoài Docker (vì image mặc định là bản light, không có ML):
```
cd backend/edge-agent
pip install -r requirements.txt -r requirements-alpr.txt
uvicorn app.main:app --port 8000     # cần Kafka + parking-service đang chạy
```

## 2. Camera tự động (laptop webcam / video) — đo latency end-to-end
`camera_agent.py` mô phỏng camera thật: chụp frame → POST `/api/v1/detect` → edge publish event →
parking mở barie; agent đo thời gian **camera → barie OPEN** bằng cách poll `/health`.
```
pip install opencv-python requests
python tools/camera_agent.py --api-key <EDGE_API_KEY> --gate GATE_ENTRY_01 --direction IN
#   SPACE = chụp & gửi, q = thoát.  --interval 2 = tự chụp mỗi 2s.  --expect 51F-12345 = chấm accuracy.
#   --source path/to/video.mp4 để chạy lại trên video đã quay.
```
Kết quả ghi `camera_agent_log.csv`: `plate, confidence, votes, frames, accepted, alpr_ms, cam_to_gate_s, expected, correct`.
Điện thoại: dùng app IP-webcam phát RTSP/HTTP rồi `--source <url>`, hoặc quay video rồi `--source file`.

**Multi-frame voting (tăng accuracy):** mỗi lần trigger, agent chụp 1 *burst* nhiều frame và cho
chúng "bỏ phiếu" thay vì tin 1 frame nhiễu — biển xuất hiện trên nhiều frame đáng tin hơn 1 frame
may mắn confidence cao. Vì `plate.canonicalize()` ép mọi frame về cùng dạng `51F-12345`, các frame
đọc đúng trùng nhau còn frame nhiễu tản mát → chọn biển có điểm `votes × confidence` cao nhất.
Logic vote thuần ở `app/services/aggregate.py` (`tally`/`decide`, có unit test), wiring client-side
trong `camera_agent.py` (edge-agent giữ stateless, KHÔNG đổi API).
```
--frames 5        # số frame chụp & vote mỗi trigger (mặc định 5)
--frame-gap 0.12  # giãn cách giữa các frame trong burst (giây)
--min-votes 2     # số frame phải đồng thuận mới chấp nhận biển
--min-conf 0.80   # confidence trung bình tối thiểu để chấp nhận
```
Đạt đồng thuận sớm → dừng burst ngay (giữ latency camera→barie ≤ 3s). Cột `accepted=False` =
near-miss (chưa đủ phiếu) nhưng vẫn ghi log để phân tích KPI.

## 3. Đo accuracy hàng loạt (offline, không cần barie)
`alpr_eval.py` chạy ALPR in-process trên tập ảnh có nhãn → báo cáo accuracy + latency.
```
# samples/labels.csv:   image_filename,true_plate[,group_id]
#   51f_day_01.jpg,51F-123.45
python tools/alpr_eval.py --images-dir samples --labels samples/labels.csv --detector ocr_only
# Tách thư mục day/ và night/ + 2 file labels để đo riêng KPI ngày/đêm.
```

**Multi-frame (`--multi-frame`):** đo thêm accuracy *có vote nhiều frame*, in cạnh accuracy
single-frame trong cùng 1 lần chạy để thấy mức cải thiện. Gom frame của cùng 1 xe theo cột thứ 3
`group_id` (vd `51f_day_01.jpg,51F-123.45,carA`); nếu không có cột này thì gom theo biển
ground-truth (mỗi xe = 1 burst). Dùng chung `decide`/`tally` ở `app/services/aggregate.py`.
```
python tools/alpr_eval.py --images-dir samples --labels samples/labels.csv \
       --multi-frame --min-votes 2 --min-conf 0.80
# Báo cáo: "Consensus correct" (accuracy theo xe) + "Reached consensus" (số xe đủ phiếu mở barie).
```

## 4. Quan sát / dashboard
- Prometheus `:9090`, Grafana `:3001` (datasource đã provision). Metric edge: `edge_detection_processing_ms`,
  `edge_plate_detected_total`, `edge_gate_commands_total`, `edge_gate_state`.
- Barie: log `🚧 GATE ... OPEN/CLOSED`, `GET /health` field `gates`.

## Ghi chú
- **Chuẩn hoá biển (canonicalize):** OCR thật hầu như không trả dấu `-` (và thường mất dấu `.`)
  và hay nhầm ký tự nhìn giống nhau, nên `app/plate.py::canonicalize()` dựng lại biển theo vị trí
  (2 số + 1-2 chữ + 3-5 số): ép từng vị trí về đúng lớp ký tự để sửa O/0, I/1, Z/2, S/5, G/6, B/8…
  (`51F12345`→`51F-12345`, `3OA-I2345`→`30A-12345`). Cả `yolo` và `ocr_only` đều dùng hàm này.
- **Biển bị OCR tách nhiều mảnh:** `alpr.py::best_plate_from_ocr()` sắp các box theo thứ tự đọc
  (theo dòng rồi trái→phải) và thử mọi cửa sổ box liền kề ghép lại → tái tạo biển bị tách
  (`"51F"`+`"12345"`→`51F-12345`), bỏ qua text nhiễu (biển hiệu…). Chọn cửa sổ hợp lệ có confidence
  trung bình cao nhất.
- `tools/` chỉ phục vụ nghiên cứu/đo đạc, KHÔNG ảnh hưởng service production.
- Biển không khớp regex VN (NG/NN ngoại giao) → ALPR trả null → dùng `POST /sessions/manual-entry`
  (đã có) như cơ chế can thiệp ngoại lệ.
