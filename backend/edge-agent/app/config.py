"""Application configuration (12-Factor: everything from the environment)."""
from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Immutable settings loaded from environment / .env."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # Auth
    edge_api_key: str = "dev-edge-key"

    # Kafka
    kafka_bootstrap_servers: str = "localhost:9092"
    topic_plate_detected: str = "parking.plate.detected"
    topic_gate_command: str = "parking.gate.command"
    topic_gate_state: str = "parking.gate.state"   # PRODUCE: physical barrier state (BR-006-2)
    consumer_group: str = "edge-agent"

    # ALPR
    alpr_mode: str = "simulate"          # simulate | real
    alpr_detector: str = "yolo"          # yolo (YOLOv8 region + EasyOCR) | ocr_only (EasyOCR full frame)
                                         #   | yolo_char (YOLOv8 region + YOLOv8 char-detection — best for VN)
    alpr_model_path: str = "/app/models/yolov8s_vn.pt"  # plate detector (yolo / yolo_char)
    alpr_char_model_path: str = "/app/models/yolov8_vn_chars.pt"  # char detector (yolo_char)
    # Text recognizer. paddle = PaddleOCR (default): more accurate AND ~5-13x faster on CPU than
    # EasyOCR on our plates (tools/bench_paddle.py). easyocr kept as a fallback engine.
    alpr_ocr_engine: str = "paddle"      # paddle | easyocr
    alpr_ocr_languages: str = "en"       # comma-separated EasyOCR langs (easyocr engine only)
    alpr_ocr_gpu: bool = False           # set true if CUDA available (easyocr engine)
    # Default OFF: on clear gate photos it LOWERS EasyOCR confidence (measured: drops some correct
    # reads below CONFIDENCE_THRESHOLD). Enable only for genuinely small/dim/night frames.
    alpr_preprocess: bool = False        # enhance image (upscale/contrast/denoise) before OCR
    # Multi-candidate OCR (raw-first, enhanced-rescue): when a raw read is invalid/None or low-confidence,
    # ALSO OCR an enhanced variant and combine — rescues small/dim/blurred plates WITHOUT regressing
    # clear ones (clear plates read confidently from raw and skip the enhanced pass). Supersedes the
    # blanket alpr_preprocess flag when on. (measured: blanket preprocess is net-zero on paddle.)
    alpr_multi_candidate: bool = True
    alpr_grammar_fix: bool = True        # re-OCR a letter-misread province head digits-only (3->B->8)
    model_version: str = "yolov8s_vn_v1.0"
    confidence_threshold: float = 0.85   # BR-001-2
    retry_attempts: int = 3              # BR-001-2

    # Barrier
    gate_auto_close_seconds: int = 10   # BR-006-2

    # Phase 1 — automatic RTSP/USB capture (disabled by default; demo uses browser webcam).
    auto_capture_enabled: bool = False
    auto_capture_entry_source: str = ""   # e.g. "0" or rtsp://... → GATE_ENTRY_01 IN
    auto_capture_exit_source: str = ""    # e.g. "1" or rtsp://... → GATE_EXIT_01 OUT
    auto_capture_interval: float = 2.0      # seconds between burst attempts when idle
    auto_capture_cooldown: float = 8.0      # seconds after publish (same as camera_agent)
    auto_capture_frames: int = 5
    auto_capture_frame_gap: float = 0.2
    auto_capture_min_votes: int = 2
    auto_capture_min_confidence: float | None = None  # None = runtime confidence_threshold

    # MinIO / object storage — lưu frame chụp xe vào/ra để truy vết. Trống endpoint = tắt (không lưu).
    minio_endpoint: str = ""             # vd "minio:9000" (nội bộ compose)
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket: str = "parking-frames"
    minio_secure: bool = False           # true nếu MinIO chạy TLS

    @property
    def image_store_enabled(self) -> bool:
        return bool(self.minio_endpoint and self.minio_access_key and self.minio_secret_key)

    # gateId -> direction role, surfaced via GET /api/v1/config
    gate_mapping: dict[str, str] = {"GATE_ENTRY_01": "entry", "GATE_EXIT_01": "exit"}


@dataclass
class RuntimeConfig:
    """Mutable subset that PUT /api/v1/config can change at runtime."""

    confidence_threshold: float
    retry_attempts: int


@lru_cache
def get_settings() -> Settings:
    return Settings()
