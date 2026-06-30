from app.services.storage import FrameStorage


class _FakeClient:
    def __init__(self, fail=False):
        self.fail = fail
        self.calls = []

    def put_object(self, bucket, key, data, length, content_type):
        if self.fail:
            raise RuntimeError("minio down")
        self.calls.append((bucket, key, length, content_type))


def _configured(client=None, **kw):
    s = FrameStorage("minio:9000", "ak", "sk", "parking-frames", **kw)
    if client is not None:
        s._client = client      # bypass the lazy minio import
    return s


def test_disabled_without_config():
    s = FrameStorage("", "", "", "parking-frames")
    assert s.enabled is False
    assert s.put_frame(b"jpegbytes", "GATE_ENTRY_01", "IN") is None


def test_object_key_scheme():
    key = FrameStorage._object_key("GATE_ENTRY_01", "IN")
    assert key.startswith("frames/")
    assert "GATE_ENTRY_01" in key
    assert "/IN_" in key
    assert key.endswith(".jpg")


def test_object_key_sanitizes_slashes():
    key = FrameStorage._object_key("A/B", "I/N")
    # gate/direction slashes must not create extra path segments
    assert "A_B" in key and "I_N_" in key


def test_put_frame_uploads_and_returns_key():
    client = _FakeClient()
    s = _configured(client)
    key = s.put_frame(b"jpegbytes", "GATE_ENTRY_01", "IN")
    assert key is not None and key.endswith(".jpg")
    assert client.calls == [("parking-frames", key, len(b"jpegbytes"), "image/jpeg")]


def test_put_frame_empty_bytes_returns_none():
    s = _configured(_FakeClient())
    assert s.put_frame(b"", "GATE_ENTRY_01", "IN") is None


def test_put_frame_swallows_storage_failure():
    s = _configured(_FakeClient(fail=True))
    assert s.put_frame(b"jpegbytes", "GATE_ENTRY_01", "IN") is None


def test_plate_key_derivation():
    assert FrameStorage._plate_key("frames/2026/IN_x.jpg") == "frames/2026/IN_x.plate.jpg"
    assert FrameStorage._plate_key("frames/2026/IN_x") == "frames/2026/IN_x.plate.jpg"


def _jpeg(width=320, height=240):
    """A real decodable JPEG so annotate_and_crop produces output."""
    import cv2
    import numpy as np
    ok, buf = cv2.imencode(".jpg", np.full((height, width, 3), 255, np.uint8))
    return buf.tobytes()


def test_put_detection_stores_full_and_crop():
    client = _FakeClient()
    s = _configured(client)
    bbox = {"x": 100, "y": 80, "w": 120, "h": 50}
    key = s.put_detection(_jpeg(), bbox, "51F-12345", 0.93, "GATE_ENTRY_01", "IN")
    # primary (annotated full) + sibling close-up crop -> two uploads; primary key returned
    assert key is not None and key.endswith(".jpg") and not key.endswith(".plate.jpg")
    stored_keys = [c[1] for c in client.calls]
    assert key in stored_keys
    assert FrameStorage._plate_key(key) in stored_keys
    assert len(client.calls) == 2


def test_put_detection_falls_back_to_raw_when_annotation_fails():
    client = _FakeClient()
    s = _configured(client)
    # undecodable bytes -> annotate returns (None, None) -> store raw frame, no crop, still return key
    key = s.put_detection(b"not-a-jpeg", {"x": 0, "y": 0, "w": 1, "h": 1},
                          "51F-12345", 0.9, "GATE_ENTRY_01", "IN")
    assert key is not None
    assert len(client.calls) == 1            # only the primary (raw) frame, no .plate.jpg
    assert client.calls[0][2] == len(b"not-a-jpeg")


def test_put_detection_disabled_returns_none():
    s = FrameStorage("", "", "", "parking-frames")
    assert s.put_detection(_jpeg(), {"x": 0, "y": 0, "w": 1, "h": 1},
                           "51F-12345", 0.9, "GATE_ENTRY_01", "IN") is None
