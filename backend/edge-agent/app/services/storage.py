"""Frame storage — persist the captured frame to MinIO (S3) so a session's entry/exit photo can be
traced later by an operator/driver.

The edge-agent is otherwise stateless; the image lives in **external** object storage, not a local DB,
so this does not violate the stateless principle. Storage is best-effort: any failure (MinIO down,
bad config) is swallowed and ``put_frame`` returns ``None`` so a detection is never lost because the
photo could not be saved.
"""
from __future__ import annotations

import io
import logging
from datetime import datetime, timezone
from uuid import uuid4

logger = logging.getLogger(__name__)


class FrameStorage:
    """Thin wrapper over the MinIO client. Disabled (no-op) when not configured."""

    def __init__(self, endpoint: str, access_key: str, secret_key: str,
                 bucket: str, secure: bool = False) -> None:
        self._bucket = bucket
        self._endpoint = endpoint
        self._access_key = access_key
        self._secret_key = secret_key
        self._secure = secure
        self.enabled = bool(endpoint and access_key and secret_key)
        self._client = None  # lazily created on first use

    def _ensure_client(self):
        if self._client is None:
            from minio import Minio  # noqa: WPS433 (lazy import — base install stays light)
            self._client = Minio(self._endpoint, access_key=self._access_key,
                                 secret_key=self._secret_key, secure=self._secure)
        return self._client

    @staticmethod
    def _object_key(gate_id: str, direction: str) -> str:
        now = datetime.now(timezone.utc)
        safe_gate = (gate_id or "UNKNOWN").replace("/", "_")
        safe_dir = (direction or "NA").replace("/", "_")
        return (f"frames/{now:%Y/%m/%d}/{safe_gate}/"
                f"{safe_dir}_{now:%H%M%S}_{uuid4().hex}.jpg")

    def put_frame(self, image_bytes: bytes, gate_id: str, direction: str) -> str | None:
        """Upload a JPEG frame; return its object key, or ``None`` if storage is off/failed."""
        if not self.enabled or not image_bytes:
            return None
        key = self._object_key(gate_id, direction)
        return self._put(key, image_bytes)

    @staticmethod
    def _plate_key(key: str) -> str:
        """Sibling key for the plate close-up: ``frames/.../IN_xxx.jpg`` -> ``..._xxx.plate.jpg``."""
        base = key[:-len(".jpg")] if key.endswith(".jpg") else key
        return f"{base}.plate.jpg"

    def _put(self, key: str, image_bytes: bytes) -> str | None:
        try:
            client = self._ensure_client()
            client.put_object(self._bucket, key, io.BytesIO(image_bytes),
                              length=len(image_bytes), content_type="image/jpeg")
            return key
        except Exception:  # never let a storage failure break detection
            logger.exception("Failed to store frame to MinIO (bucket=%s, key=%s)", self._bucket, key)
            return None

    def put_detection(self, image_bytes: bytes, bbox: dict, plate_number: str,
                      confidence: float, gate_id: str, direction: str) -> str | None:
        """Store the entry/exit photo with the plate made visible to an operator.

        Saves TWO objects from the same frame (the 'ảnh lưu mất biển' fix): the primary key holds the
        full frame with a box + the recognized plate text drawn on it (so it's self-explanatory in the
        dashboard), and a sibling ``*.plate.jpg`` holds a padded, upscaled close-up of the plate.
        Returns the PRIMARY key (annotated full) — the sibling crop key is derivable via
        :meth:`_plate_key`. Best-effort throughout: if annotation fails we store the raw frame so a
        detection is never lost because the photo couldn't be decorated. ``None`` if storage is off."""
        if not self.enabled or not image_bytes:
            return None
        annotated = crop = None
        try:
            from app.services.annotate import annotate_and_crop  # noqa: WPS433 (lazy: pulls in cv2)
            annotated, crop = annotate_and_crop(image_bytes, bbox, plate_number, confidence)
        except Exception:  # annotation must never break storage/detection
            logger.exception("Failed to annotate frame; storing raw frame instead")

        key = self._object_key(gate_id, direction)
        primary = self._put(key, annotated or image_bytes)  # raw fallback keeps the trace photo
        if primary is not None and crop is not None:
            self._put(self._plate_key(primary), crop)  # best-effort; primary key still returned
        return primary
