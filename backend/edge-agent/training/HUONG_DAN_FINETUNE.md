# Hướng dẫn chi tiết: Fine-tune model nhận-ký-tự biển VN (cải thiện OCR)

> Tài liệu này hướng dẫn bạn **tự chạy** toàn bộ: cài môi trường → tạo data → train trên RTX 4060 →
> đo → deploy. Đã tính sẵn vụ **ổ C: gần đầy** (đẩy mọi thứ sang D) và **torch đang là bản CPU**.
>
> Pipeline edge **đã tích hợp sẵn** detector `yolo_char` — train xong chỉ việc bỏ file model vào là chạy.

Mọi lệnh chạy trong **PowerShell**, tại thư mục:
```powershell
cd D:\SU26\MSS301\Group_Project\smart-parking-car\backend\edge-agent
```
Python dùng là của venv: `.venv-alpr\Scripts\python.exe`

---

## Bối cảnh & điều kiện (đọc 1 lần)
| Mục | Hiện trạng | Việc cần |
|---|---|---|
| GPU | RTX 4060 8GB ✅ | đủ để train |
| PyTorch | **bản CPU** (`cuda.is_available()=False`) | **phải cài lại bản CUDA** (Bước 1) |
| Ổ C: | **~3.5GB trống** ⚠️ | **đẩy cache/temp sang D** (Bước 0) |
| Ổ D: | ~53GB ✅ | nơi chứa torch + data + runs |
| Ảnh thật có nhãn | gần như chưa có | **mấu chốt để thắng EasyOCR** (Bước 3) |

> ⚠️ **Quan trọng nhất:** model train **toàn ảnh tổng hợp** thường **KHÔNG thắng** EasyOCR (hiện 86%)
> trên ảnh chụp thật, vì *domain gap*. Muốn ăn thật thì **phải có ảnh biển thật** để fine-tune + đo.
> Vì vậy Bước 3 (ảnh thật) là quan trọng nhất, đừng bỏ.

---

## Bước 0 — Ép mọi thứ sang ổ D (vì C: sắp đầy)
Chạy khối này **mỗi lần mở PowerShell mới** trước khi cài/train (set cho phiên hiện tại):
```powershell
# Thư mục cache/temp trên D
New-Item -ItemType Directory -Force D:\ml-cache\pip, D:\ml-cache\tmp, D:\ml-cache\torch | Out-Null

$env:PIP_CACHE_DIR = "D:\ml-cache\pip"
$env:TMP           = "D:\ml-cache\tmp"
$env:TEMP          = "D:\ml-cache\tmp"
$env:TORCH_HOME    = "D:\ml-cache\torch"
# ultralytics cũng ghi config/cache theo HOME; trỏ về D cho chắc:
$env:YOLO_CONFIG_DIR = "D:\ml-cache\ultralytics"
```
> Làm vậy thì ~2.5GB torch + file tạm **không** rơi vào C:. Kiểm tra nhanh: `echo $env:TMP` → phải ra `D:\ml-cache\tmp`.

---

## Bước 1 — Cài PyTorch bản CUDA (vào venv trên D)
```powershell
.\.venv-alpr\Scripts\python.exe -m pip install --no-cache-dir `
    --index-url https://download.pytorch.org/whl/cu121 `
    torch torchvision --upgrade
```
- `--no-cache-dir` + `PIP_CACHE_DIR` (Bước 0) → không phình C:.
- Tải ~2.5GB, hơi lâu. Nó sẽ gỡ torch CPU cũ và cài bản CUDA.

**Kiểm tra (bắt buộc thấy True):**
```powershell
.\.venv-alpr\Scripts\python.exe -c "import torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))"
```
Nếu ra `False`: cập nhật **driver NVIDIA** mới nhất, hoặc đổi `cu121` → `cu124` cho khớp CUDA máy, cài lại.

---

## Bước 2 — Sinh dữ liệu tổng hợp (tự động có nhãn)
```powershell
.\.venv-alpr\Scripts\python.exe training\gen_synthetic.py --out training\data --n 5000 --val-split 0.1
```
- Ra 5000 ảnh biển + nhãn YOLO trong `training\data\images\{train,val}` và `labels\{...}`.
- Muốn xem thử: mở vài ảnh trong `training\data\images\train`.

---

## Bước 3 — Gom ẢNH THẬT (quan trọng nhất để thắng EasyOCR)
Mục tiêu: vài chục → vài trăm ảnh biển **chụp thật** (kiểu cam cổng: gần, chính diện, đủ sáng).

**Gán nhãn nhanh (đỡ cực):**
1. Auto pre-label bằng pipeline hiện tại để có nhãn nháp, rồi chỉ sửa chỗ sai:
   - Bỏ ảnh thật vào `training\real_raw\`
   - (Tôi có thể viết script auto-label nếu bạn cần — nhắn mình.)
2. Hoặc gán tay bằng **Roboflow / LabelImg / CVAT**, **export YOLO format**, thứ tự lớp **đúng như** `training\dataset.yaml` (0–9 rồi A,B,C,D,E,F,G,H,K,L,M,N,P,S,T,U,V,X,Y,Z).
3. Đổ ảnh+nhãn thật vào chung `training\data\images\{train,val}` + `labels\{...}` (trộn với data tổng hợp).

Đồng thời **giữ riêng ~20–50 ảnh thật làm tập ĐO** (kèm `labels.csv` dạng `tên_file,biển_đúng`) — để Bước 5.

> Không có ảnh thật vẫn train được (chỉ data tổng hợp), nhưng đừng kỳ vọng vượt EasyOCR.

---

## Bước 4 — Train trên GPU (RTX 4060)
```powershell
.\.venv-alpr\Scripts\python.exe training\train_chars.py --data training\data `
    --epochs 80 --imgsz 416 --batch 32 --device 0 --name vn_chars
```
- Weights tốt nhất: `runs\detect\vn_chars\weights\best.pt`
- **Thời gian** (ước lượng 4060, 5000 ảnh): ~20–45 phút.
- **Hết VRAM (CUDA out of memory)?** giảm `--batch 16` (hoặc 8) hoặc `--imgsz 320`.
- `yolov8n` nhanh; muốn chính xác hơn dùng `--base yolov8s.pt` (chậm hơn).
- Train tiếp từ model cũ: `--base runs\detect\vn_chars\weights\best.pt`.

---

## Bước 5 — Đo so với EasyOCR (trên ẢNH THẬT)
```powershell
.\.venv-alpr\Scripts\python.exe training\eval_chars.py `
    --model runs\detect\vn_chars\weights\best.pt `
    --images-dir <thư_mục_ảnh_thật> --labels <thư_mục_ảnh_thật>\labels.csv
```
- In bảng so sánh **EasyOCR vs yolo_char** + % accuracy mỗi bên.
- **CHỈ deploy nếu `yolo_char` CAO HƠN EasyOCR** trên tập ảnh thật này. Nếu thấp hơn → cần thêm/đúng ảnh thật, hoặc tăng epoch/đổi yolov8s.

---

## Bước 6 — Deploy (khi model đã thắng)
```powershell
Copy-Item runs\detect\vn_chars\weights\best.pt models\yolov8_vn_chars.pt -Force
```
Trong `docker-compose.yml`, mục `edge-agent`, đổi:
```yaml
      ALPR_DETECTOR: "yolo_char"
```
Rồi rebuild + chạy lại edge:
```powershell
cd D:\SU26\MSS301\Group_Project\smart-parking-car
docker compose build edge-agent
docker compose up -d edge-agent
```
Kiểm tra: `curl http://localhost:8000/health` thấy `"alprMode":"real"`. Quét thử biển.
Muốn quay lại EasyOCR: đổi `ALPR_DETECTOR: "yolo"` rồi rebuild.

---

## Xử lý sự cố
| Triệu chứng | Nguyên nhân | Cách xử |
|---|---|---|
| Cài torch lỗi "No space left" / C: đầy | quên Bước 0 | set lại `PIP_CACHE_DIR`/`TMP`/`TEMP` về D, dùng `--no-cache-dir` |
| `cuda.is_available()=False` | torch CPU / driver cũ | cài lại bản `cu121`/`cu124`, cập nhật driver NVIDIA |
| `CUDA out of memory` lúc train | batch/imgsz lớn | `--batch 16` hoặc `8`, hoặc `--imgsz 320` |
| Train xong nhưng `eval` ≤ EasyOCR | thiếu ảnh thật (domain gap) | thêm ảnh thật vào train (Bước 3), tăng epoch, thử `yolov8s.pt` |
| ultralytics báo không thấy data | sai path | `train_chars.py` tự dựng path tuyệt đối; đảm bảo có `training\data\images\train` |

## Ghi nhớ
- **Đừng deploy mù** — luôn chạy Bước 5 và chỉ đổi sang `yolo_char` khi nó cao hơn EasyOCR thật sự.
- Yếu tố lớn nhất quyết định accuracy là **chất lượng ảnh đầu vào (camera gần, chính diện, sáng)** — không model nào bù hết.
- C: nên dọn còn ≥ 10–15GB để Windows + Docker chạy mượt (không riêng vụ train).
