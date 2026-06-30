FROM python:3.11-slim

WORKDIR /app

# System libs cho OCR/OpenCV trên python:3.11-slim (thiếu sẵn). PHẢI cài TRƯỚC khi pip/import paddle/cv2:
#   libgl1 + libglib2.0-0 — OpenCV (cv2) cần khi ALPR_MODE=real
#   libgomp1              — PaddleOCR/paddlepaddle cần OpenMP (libgomp.so.1); THIẾU nó là paddle
#                           ném lỗi load lúc import -> trước đây bị nuốt thành 422 cho MỌI ảnh.
#   build-essential       — gcc/g++/make để build các wheel chỉ-có-source (vd stringzilla, dep của
#                           paddleocr); slim không có compiler -> pip install requirements-alpr fail.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 libgomp1 build-essential \
    && rm -rf /var/lib/apt/lists/*

# Base (light) deps — ALPR_MODE=simulate works with just these.
# For ALPR_MODE=real, also install requirements-alpr.txt and mount the model.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Real ALPR (YOLOv8 + EasyOCR + PaddleOCR) — kéo torch/ultralytics/easyocr/paddle, ~vài GB, build lâu lần đầu.
COPY requirements-alpr.txt .
RUN pip install --no-cache-dir -r requirements-alpr.txt

# Pre-download + smoke-test các OCR engine NGAY LÚC BUILD. Hai mục đích:
#   1) Bake model vào image -> container chạy OFFLINE được (PaddleOCR mặc định tải model từ internet
#      ở request đầu; mạng bị chặn = init lỗi = 422 cho mọi ảnh). EasyOCR (lưới an toàn) cũng được cache.
#   2) GUARANTEE: nếu paddle không import/init được trong image này thì BUILD FAIL ngay tại đây,
#      thay vì xuất xưởng image hỏng rồi mới phát hiện lúc xe vào cổng.
RUN python -c "import numpy as np; from paddleocr import PaddleOCR; PaddleOCR(use_angle_cls=True, lang='en', show_log=False).ocr(np.full((64,200,3),255,np.uint8), cls=True); import easyocr; easyocr.Reader(['en'], gpu=False); print('OCR engines ready')"

COPY app ./app

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
