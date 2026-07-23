"""Smart Parking — demo web server (camera + plate + barrier + MoMo QR).

A tiny same-origin proxy so the browser page can hit edge-agent / parking / billing without
CORS and without exposing the edge API key or the admin JWT. NOT a production service — a demo
tool (like kafka-ui). Run on the host:

    cd backend/edge-agent
    ./.venv-alpr/Scripts/python.exe -m uvicorn server:app --app-dir ../../frontend/demo --port 8095

Then open http://localhost:8095 (localhost is a secure context, so the webcam works over http).

Config via env (defaults assume the docker-compose stack on localhost):
    EDGE_URL, PARKING_URL, BILLING_URL, ADMIN_URL, EDGE_API_KEY, ADMIN_USER, ADMIN_PASS
"""
from __future__ import annotations

import asyncio
import os
import re
from pathlib import Path

import httpx
from fastapi import Body, FastAPI, File, Form, UploadFile
from fastapi.responses import FileResponse, JSONResponse

EDGE_URL = os.getenv("EDGE_URL", "http://localhost:8000")
PARKING_URL = os.getenv("PARKING_URL", "http://localhost:8081")
BILLING_URL = os.getenv("BILLING_URL", "http://localhost:8082")
ADMIN_URL = os.getenv("ADMIN_URL", "http://localhost:8083")
EDGE_API_KEY = os.getenv("EDGE_API_KEY", "8f7a2d1c9e4b6f8a3d5e7c1b9a2f4d6e")
ADMIN_USER = os.getenv("ADMIN_USER", "admin")
ADMIN_PASS = os.getenv("ADMIN_PASS", "12345678!")  # khớp ADMIN_SEED_PASSWORD trong .env

INDEX = Path(__file__).with_name("index.html")
app = FastAPI(title="smart-parking-demo")

_token: str | None = None


async def _login(client: httpx.AsyncClient) -> str:
    """Login as admin and cache the JWT (operator/admin endpoints need a Bearer token)."""
    global _token
    if _token:
        return _token
    r = await client.post(f"{ADMIN_URL}/api/v1/auth/login",
                          json={"username": ADMIN_USER, "password": ADMIN_PASS})
    r.raise_for_status()
    _token = r.json()["data"]["accessToken"]
    return _token


async def _auth_get(client, url, **kw):
    """GET with Bearer; re-login once on 401 (token expiry)."""
    global _token
    token = await _login(client)
    r = await client.get(url, headers={"Authorization": f"Bearer {token}"}, **kw)
    if r.status_code == 401:
        _token = None
        token = await _login(client)
        r = await client.get(url, headers={"Authorization": f"Bearer {token}"}, **kw)
    return r


async def _auth_post(client, url, **kw):
    global _token
    token = await _login(client)
    r = await client.post(url, headers={"Authorization": f"Bearer {token}"}, **kw)
    if r.status_code == 401:
        _token = None
        token = await _login(client)
        r = await client.post(url, headers={"Authorization": f"Bearer {token}"}, **kw)
    return r


@app.get("/")
async def index() -> FileResponse:
    return FileResponse(INDEX)


@app.get("/qrcode.min.js")
async def qrcode_js() -> FileResponse:
    """Serve the QR library same-origin (no external CDN — works on phones / offline)."""
    return FileResponse(INDEX.with_name("qrcode.min.js"), media_type="application/javascript")


@app.get("/api/health")
async def health():
    """edge-agent health → barrier/gate state for the UI."""
    async with httpx.AsyncClient(timeout=10) as c:
        try:
            r = await c.get(f"{EDGE_URL}/health")
            return JSONResponse(status_code=r.status_code, content=r.json())
        except Exception as exc:  # noqa: BLE001
            return JSONResponse(status_code=502, content={"error": str(exc)})


@app.post("/api/detect")
async def detect(image: UploadFile, gate_id: str = Form(...), direction: str = Form(...)):
    """Proxy a captured frame to edge-agent /detect (injects the API key)."""
    content = await image.read()
    async with httpx.AsyncClient(timeout=180) as c:
        r = await c.post(
            f"{EDGE_URL}/api/v1/detect",
            headers={"X-API-Key": EDGE_API_KEY},
            files={"image": (image.filename or "frame.jpg", content, image.content_type or "image/jpeg")},
            data={"gate_id": gate_id, "direction": direction},
        )
    try:
        body = r.json()
    except Exception:  # noqa: BLE001
        body = {"error": r.text}
    return JSONResponse(status_code=r.status_code, content=body)


@app.post("/api/detect_burst")
async def detect_burst(images: list[UploadFile] = File(...),
                       gate_id: str = Form(...), direction: str = Form(...)):
    """Proxy nhiều khung của CÙNG một xe tới edge-agent /detect/burst (đa-khung bỏ phiếu).

    Quét nhiều khung rồi lấy biển đọc trùng nhiều nhất → chính xác hơn hẳn 1 khung (loại khung
    mờ/đọc lệch). Inject API key như /api/detect."""
    files = []
    for im in images:
        content = await im.read()
        files.append(("images", (im.filename or "frame.jpg", content, im.content_type or "image/jpeg")))
    async with httpx.AsyncClient(timeout=180) as c:
        r = await c.post(
            f"{EDGE_URL}/api/v1/detect/burst",
            headers={"X-API-Key": EDGE_API_KEY},
            files=files,
            data={"gate_id": gate_id, "direction": direction},
        )
    try:
        body = r.json()
    except Exception:  # noqa: BLE001
        body = {"error": r.text}
    return JSONResponse(status_code=r.status_code, content=body)


@app.post("/api/detect/presence")
async def detect_presence(
    image: UploadFile = File(...),
    roi_x: float = Form(0.08),
    roi_y: float = Form(0.38),
    roi_w: float = Form(0.84),
    roi_h: float = Form(0.24),
    min_confidence: float = Form(0.15),
):
    """Proxy vehicle-only presence check (full frame + guide ROI from browser)."""
    content = await image.read()
    async with httpx.AsyncClient(timeout=45) as c:
        r = await c.post(
            f"{EDGE_URL}/api/v1/detect/presence",
            headers={"X-API-Key": EDGE_API_KEY},
            files={"image": (image.filename or "frame.jpg", content, image.content_type or "image/jpeg")},
            data={
                "roi_x": str(roi_x),
                "roi_y": str(roi_y),
                "roi_w": str(roi_w),
                "roi_h": str(roi_h),
                "min_confidence": str(min_confidence),
                "vehicle_only": "true",
            },
        )
    try:
        body = r.json()
    except Exception:  # noqa: BLE001
        body = {"error": r.text, "present": False}
    if r.status_code == 404:
        body = {
            "present": False,
            "error": "edge-agent chưa có API /detect/presence — rebuild: docker compose up -d --build edge-agent",
            "detail": body.get("detail") if isinstance(body, dict) else str(body),
        }
    return JSONResponse(status_code=200 if r.status_code == 404 else r.status_code, content=body)


@app.post("/api/momo")
async def momo_create(plate: str):
    """Find the plate's latest CLOSED session and create a MoMo payment for its invoice."""
    async with httpx.AsyncClient(timeout=60) as c:
        sr = await _auth_get(c, f"{PARKING_URL}/api/v1/sessions",
                             params={"plate": plate, "status": "CLOSED", "size": 1, "page": 0})
        items = (sr.json().get("data") or {}).get("content") or []
        if not items:
            return JSONResponse(status_code=404,
                                content={"error": f"Chưa có phiên đã ĐÓNG cho biển {plate}"})
        session_id = items[0]["id"]
        mr = await _auth_post(c, f"{BILLING_URL}/api/v1/billing/sessions/{session_id}/momo")
        data = mr.json().get("data") or mr.json()
        if isinstance(data, dict):
            data["sessionId"] = session_id
        return JSONResponse(status_code=mr.status_code, content=data)


@app.get("/api/momo/status")
async def momo_status(session_id: str, order_id: str):
    """Query + reconcile MoMo payment status for a session (orderId from the create call)."""
    async with httpx.AsyncClient(timeout=30) as c:
        r = await _auth_get(c, f"{BILLING_URL}/api/v1/billing/sessions/{session_id}/momo/status",
                            params={"orderId": order_id})
        return JSONResponse(status_code=r.status_code, content=r.json().get("data") or r.json())


@app.post("/api/payos")
async def payos_create(plate: str):
    """Find the plate's latest CLOSED session and create a PayOS payment for its invoice."""
    async with httpx.AsyncClient(timeout=60) as c:
        sr = await _auth_get(c, f"{PARKING_URL}/api/v1/sessions",
                             params={"plate": plate, "status": "CLOSED", "size": 1, "page": 0})
        items = (sr.json().get("data") or {}).get("content") or []
        if not items:
            return JSONResponse(status_code=404,
                                content={"error": f"Chưa có phiên đã ĐÓNG cho biển {plate}"})
        session_id = items[0]["id"]
        pr = await _auth_post(c, f"{BILLING_URL}/api/v1/billing/sessions/{session_id}/payos")
        data = pr.json().get("data") or pr.json()
        if isinstance(data, dict):
            data["sessionId"] = session_id
        return JSONResponse(status_code=pr.status_code, content=data)


@app.get("/api/payos/status")
async def payos_status(session_id: str, order_code: str):
    """Query + reconcile PayOS payment status for a session."""
    async with httpx.AsyncClient(timeout=30) as c:
        r = await _auth_get(c, f"{BILLING_URL}/api/v1/billing/sessions/{session_id}/payos/status",
                            params={"orderCode": order_code})
        return JSONResponse(status_code=r.status_code, content=r.json().get("data") or r.json())


@app.get("/api/invoice/status")
async def invoice_status(session_id: str):
    """Invoice status only — catches webhook settlement without re-querying MoMo/PayOS."""
    async with httpx.AsyncClient(timeout=15) as c:
        r = await _auth_get(c, f"{BILLING_URL}/api/v1/billing/sessions/{session_id}")
        data = r.json().get("data") or r.json()
        return JSONResponse(status_code=r.status_code, content={
            "sessionId": session_id,
            "status": (data.get("status") or "").upper(),
            "amount": data.get("amount"),
        })


async def _login_as(client: httpx.AsyncClient, username: str, password: str) -> str:
    """Log in as a NAMED operator — not the cached shared admin. Never cached.

    Outage cash is the one flow where "who took the money" has to be true: billing stamps
    payments.received_by from the JWT subject, and that column is the only thing tying a paper
    voucher to a person. Reusing the shared admin token here would attribute every outage
    payment to `admin` and make the voucher reconciliation worthless.
    """
    r = await client.post(f"{ADMIN_URL}/api/v1/auth/login",
                          json={"username": username, "password": password})
    r.raise_for_status()
    return r.json()["data"]["accessToken"]


@app.post("/api/pay/offline")
async def pay_offline(payload: dict = Body(...)):
    """BR-005-7: key in cash that was taken by hand while the gate had no power.

    Body: {plate, voucherNo, paidAt (ISO-8601), username, password, note?}. The operator signs in
    with their OWN account so the payment is attributed to them. The amount is NOT taken from the
    client — it is read off the invoice, so a mistyped figure cannot under-settle the bill.
    """
    plate = _norm_plate(payload.get("plate"))
    voucher = (payload.get("voucherNo") or "").strip()
    paid_at = (payload.get("paidAt") or "").strip()
    username = (payload.get("username") or "").strip()
    password = payload.get("password") or ""

    missing = [k for k, v in (("plate", plate), ("voucherNo", voucher), ("paidAt", paid_at),
                              ("username", username), ("password", password)) if not v]
    if missing:
        return JSONResponse(status_code=400, content={"error": "Thiếu: " + ", ".join(missing)})

    async with httpx.AsyncClient(timeout=60) as c:
        try:
            token = await _login_as(c, username, password)
        except httpx.HTTPStatusError:
            return JSONResponse(status_code=401,
                                content={"error": f"Sai tài khoản/mật khẩu nhân viên ({username})"})
        headers = {"Authorization": f"Bearer {token}"}

        # Latest CLOSED session for the plate — same lookup the QR flows use.
        sr = await c.get(f"{PARKING_URL}/api/v1/sessions", headers=headers,
                         params={"plate": plate, "status": "CLOSED", "size": 1, "page": 0})
        items = (sr.json().get("data") or {}).get("content") or []
        if not items:
            return JSONResponse(status_code=404,
                                content={"error": f"Chưa có phiên đã ĐÓNG cho biển {plate}"})
        session_id = items[0]["id"]

        ir = await c.get(f"{BILLING_URL}/api/v1/billing/sessions/{session_id}", headers=headers)
        invoice = ir.json().get("data") or {}
        status = (invoice.get("status") or "").upper()
        if status != "PENDING":
            # Already settled (QR went through before the power cut, or keyed in twice).
            return JSONResponse(status_code=409, content={
                "sessionId": session_id, "status": status,
                "error": f"Hóa đơn đã ở trạng thái {status} — KHÔNG thu lại lần hai."})

        pr = await c.post(
            f"{BILLING_URL}/api/v1/billing/sessions/{session_id}/pay", headers=headers,
            json={"method": "CASH_OFFLINE", "amountPaid": invoice.get("amount"),
                  "offlineVoucherNo": voucher, "paidAt": paid_at,
                  "note": payload.get("note") or f"Thu tay lúc mất điện — phiếu {voucher}"})
        body = pr.json()
        data = body.get("data") or body
        if isinstance(data, dict):
            data["sessionId"] = session_id
            data["amount"] = invoice.get("amount")
            data["receivedBy"] = username
        return JSONResponse(status_code=pr.status_code, content=data)


def _norm_plate(p: str | None) -> str:
    """Mirror parking-service PlateNumbers.normalize (BR-001-4): upper + strip whitespace."""
    return re.sub(r"\s+", "", p or "").upper()


async def _availability(client) -> dict:
    try:
        r = await _auth_get(client, f"{PARKING_URL}/api/v1/slots/availability")
        return r.json().get("data") or {}
    except Exception:  # noqa: BLE001
        return {}


async def _slot_map(client, slot_code: str) -> dict | None:
    """Tọa độ lưới (BR-003-6) của slot vừa gán, để demo vẽ bản đồ MINH HỌA cho xe walk-in biết
    chỗ của mình ở đâu — không phải chọn chỗ, chỉ xem sau khi hệ thống đã tự gán (BR-003-2/3).
    Dùng GET /api/v1/slots (admin-scoped, server.py đã có sẵn token) vì đây không phải luồng
    driver — kiosk không biết tài khoản tài xế nào đang đứng trước camera."""
    try:
        r = await _auth_get(client, f"{PARKING_URL}/api/v1/slots")
        for s in (r.json().get("data") or []):
            if s.get("slotCode") == slot_code:
                return {"slotCode": slot_code, "zone": s.get("zone"),
                        "gridRow": s.get("gridRow"), "gridCol": s.get("gridCol")}
    except Exception:  # noqa: BLE001
        pass
    return None


@app.get("/api/availability")
async def availability():
    """Số chỗ trống / đang dùng để hiển thị trên demo (nguồn: parking-service)."""
    async with httpx.AsyncClient(timeout=15) as c:
        return JSONResponse(await _availability(c))


@app.get("/api/outcome")
async def outcome(plate: str, direction: str):
    """Quyết định THẬT sau khi nhận diện (parking + billing), để demo báo đúng thay vì đoán.

    Detect chỉ publish event; parking-service xử lý bất đồng bộ (Kafka + outbox ~2s) rồi mới
    mở/không mở barie. Endpoint này poll ngắn để biết kết quả thật và (với xe RA) chỉ yêu cầu
    thanh toán khi hóa đơn PENDING — xe whitelist (WAIVED) không tạo QR, tránh vòng lặp.
    """
    plate_n = _norm_plate(plate)
    async with httpx.AsyncClient(timeout=30) as c:
        if direction == "IN":
            # BR-002-1: blacklist bị từ chối vào (không mở barie).
            try:
                rb = await _auth_get(c, f"{PARKING_URL}/api/v1/vehicles/blacklist")
                blacklisted = any(_norm_plate(v.get("plateNumber")) == plate_n
                                  for v in (rb.json().get("data") or []))
            except Exception:  # noqa: BLE001
                blacklisted = False
            if blacklisted:
                return JSONResponse({
                    "result": "DENIED_BLACKLIST", "barrier": False,
                    "message": f"🚫 Biển {plate_n} trong BLACKLIST — TỪ CHỐI vào, barie KHÔNG mở.",
                    "availability": await _availability(c)})

            # Xe được nhận → parking tạo session ACTIVE. Chờ tối đa ~4s.
            for _ in range(6):
                rs = await _auth_get(c, f"{PARKING_URL}/api/v1/sessions",
                                     params={"plate": plate_n, "status": "ACTIVE", "size": 1, "page": 0})
                items = (rs.json().get("data") or {}).get("content") or []
                if items:
                    slot = items[0].get("slotCode")
                    return JSONResponse({
                        "result": "ADMITTED", "barrier": True,
                        "message": f"✅ Mở barie cho XE VÀO — {plate_n}"
                                   + (f" · chỗ {slot}" if slot else "") + ".",
                        "availability": await _availability(c),
                        "slotMap": await _slot_map(c, slot) if slot else None})
                await asyncio.sleep(0.7)

            avail = await _availability(c)
            if avail.get("emptySlots") == 0:
                return JSONResponse({
                    "result": "FULL", "barrier": False,
                    "message": "⛔ HẾT CHỖ — bãi đã đầy, barie KHÔNG mở.",
                    "availability": avail})
            return JSONResponse({
                "result": "NO_ENTRY", "barrier": False,
                "message": "ℹ️ Không mở barie — xe đã có trong bãi hoặc biển không hợp lệ.",
                "availability": avail})

        # direction OUT — cổng ra luôn mở; chỉ khác ở việc có cần thanh toán không.
        for _ in range(6):
            rs = await _auth_get(c, f"{PARKING_URL}/api/v1/sessions",
                                 params={"plate": plate_n, "status": "CLOSED", "size": 1, "page": 0})
            items = (rs.json().get("data") or {}).get("content") or []
            if items:
                sid = items[0]["id"]
                try:
                    ri = await _auth_get(c, f"{BILLING_URL}/api/v1/billing/sessions/{sid}")
                    inv = ri.json().get("data") or {}
                except Exception:  # noqa: BLE001
                    inv = {}
                st = (inv.get("status") or "").upper()
                if st == "WAIVED":
                    return JSONResponse({
                        "result": "EXIT_WAIVED", "barrier": True, "needsPayment": False,
                        "message": f"✅ Xe whitelist {plate_n} RA — MIỄN PHÍ, không cần thanh toán. Barie mở.",
                        "availability": await _availability(c)})
                if st == "PAID":
                    return JSONResponse({
                        "result": "EXIT_PAID", "barrier": True, "needsPayment": False,
                        "message": f"✅ {plate_n} đã thanh toán — barie mở cho xe RA.",
                        "availability": await _availability(c)})
                if st == "PENDING":
                    return JSONResponse({
                        "result": "EXIT_PENDING", "barrier": True, "needsPayment": True,
                        "sessionId": sid, "amount": inv.get("amount"),
                        "message": f"💳 Xe RA — {plate_n}. Cần thanh toán, quét QR MoMo.",
                        "availability": await _availability(c)})
            await asyncio.sleep(0.7)

        return JSONResponse({
            "result": "EXIT_REVIEW", "barrier": True, "needsPayment": False,
            "message": "ℹ️ Đã mở barie cho xe RA. Chưa thấy hóa đơn (xe lạ/cần đối soát) hoặc đang xử lý.",
            "availability": await _availability(c)})
