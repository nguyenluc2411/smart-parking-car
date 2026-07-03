# API Contracts – Smart Parking System

> Đây là source of truth cho tất cả REST API.
> KHÔNG tạo endpoint mới ngoài tài liệu này mà không cập nhật đây trước.

---

## Quy ước chung

**Base URL:** `http://{service-host}:{port}/api/v1`

**Authentication:** `Authorization: Bearer {JWT}` (trừ public endpoints)

**Response format chuẩn:**
```json
{
  "success": true,
  "data": { ... },
  "message": "OK",
  "timestamp": "2025-06-15T08:32:11Z"
}
```

**Error format chuẩn:**
```json
{
  "success": false,
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "Session với id abc không tồn tại",
    "field": null
  },
  "timestamp": "2025-06-15T08:32:11Z"
}
```

**HTTP Status Codes:**
- 200 OK — thành công (GET, PUT)
- 201 Created — tạo mới thành công (POST)
- 204 No Content — xóa thành công (DELETE)
- 400 Bad Request — validation lỗi
- 401 Unauthorized — thiếu hoặc sai token
- 403 Forbidden — đúng token nhưng không đủ quyền
- 404 Not Found — resource không tồn tại
- 409 Conflict — vi phạm business constraint
- 503 Service Unavailable — downstream service lỗi

---

## edge-agent API (:8000)

### POST /api/v1/detect
**Auth:** X-API-Key  
**Request:** multipart/form-data
```
image: <file>       # JPEG/PNG frame
gate_id: string     # GATE_ENTRY_01 | GATE_EXIT_01
direction: string   # IN | OUT
```
**Response 200:**
```json
{
  "plateNumber": "51F-123.45",
  "confidence": 0.94,
  "processingMs": 412,
  "boundingBox": { "x": 120, "y": 80, "w": 340, "h": 90 }
}
```
**Response 422 (không nhận diện được):**
```json
{ "plateNumber": null, "confidence": 0.0, "reason": "LOW_CONFIDENCE" }
```
> Khi nhận diện thành công, edge-agent **lưu frame vào object storage (MinIO)** rồi đặt object key vào
> `imageRef` của event `parking.plate.detected` → parking-service gắn key vào session (ảnh vào/ra để
> truy vết). Lưu ảnh là best-effort: lỗi storage không chặn detection (`imageRef` fallback tên file).
> **Ảnh lưu được annotate:** `imageRef` trỏ tới frame đầy đủ đã **vẽ khung quanh biển + in biển đã đọc**
> (để operator/admin nhìn ra ngay, tránh "ảnh mất biển"); một **ảnh crop cận cảnh biển** được lưu kèm ở
> key chị-em `<imageRef bỏ .jpg>.plate.jpg` (cùng bucket) cho nhu cầu xem phóng to.

### POST /api/v1/detect/burst
Đa-frame consensus: nhận nhiều frame của **cùng một xe**, OCR từng frame và cho chúng **vote**; chỉ
biển **thắng (đủ vote + đủ confidence trung bình)** được publish **đúng 1** event `parking.plate.detected`,
và chỉ **frame tốt nhất** của biển đó được lưu MinIO (1 burst → tối đa 1 event + 1 ảnh, hoặc 0 nếu không
đạt consensus). Burst dừng sớm ngay khi đạt consensus để giữ latency camera→barie ≤ 3s.

**Auth:** X-API-Key  
**Request:** multipart/form-data
```
images: <file>...     # nhiều phần "images" (frame JPEG/PNG, cũ → mới)
gate_id: string       # GATE_ENTRY_01 | GATE_EXIT_01
direction: string     # IN | OUT
min_votes: int        # optional, mặc định 2 — số frame phải đồng thuận
min_confidence: float # optional, mặc định = ngưỡng runtime — confidence TB tối thiểu để chấp nhận
```
**Response 200 (đạt consensus → đã publish):**
```json
{
  "accepted": true, "published": true,
  "plateNumber": "51F-123.45", "confidence": 0.91,
  "votes": 3, "framesRead": 4, "framesSubmitted": 5, "processingMs": 870,
  "imageRef": "frames/2025/06/25/GATE_ENTRY_01/IN_...jpg",
  "candidates": [ { "plateNumber": "51F-123.45", "votes": 3, "meanConfidence": 0.91 } ]
}
```
**Response 202 (không đạt consensus → KHÔNG publish, KHÔNG lưu ảnh):**
```json
{
  "accepted": false, "published": false,
  "plateNumber": "51F-123.45", "confidence": 0.62,
  "votes": 1, "framesRead": 2, "framesSubmitted": 5, "processingMs": 1100,
  "imageRef": null,
  "candidates": [ { "plateNumber": "51F-123.45", "votes": 1, "meanConfidence": 0.62 } ]
}
```
> Event publish giống hệt `/detect` (cùng topic `parking.plate.detected`, cùng schema) — parking-service
> **không cần thay đổi**. `confidence` của event là confidence TB của các frame bầu cho biển thắng.

### POST /api/v1/simulate/trigger
**Auth:** X-API-Key  
**Request:** application/json
```json
{
  "gate_id": "GATE_ENTRY_01",
  "plate_number": "51F-123.45",
  "direction": "IN",
  "simulate_confidence": 0.95
}
```
**Response 200:**
```json
{ "eventId": "uuid", "published": true, "topic": "parking.plate.detected" }
```

### GET /api/v1/config
**Auth:** X-API-Key  
**Response 200:**
```json
{
  "confidenceThreshold": 0.85,
  "retryAttempts": 3,
  "gateMapping": { "GATE_ENTRY_01": "entry", "GATE_EXIT_01": "exit" },
  "modelVersion": "yolov8s_vn_v1.0"
}
```

### PUT /api/v1/config
**Auth:** X-API-Key  
**Request:** application/json
```json
{ "confidenceThreshold": 0.90 }
```

---

## parking-service API (:8081)

### GET /api/v1/sessions
**Auth:** OPERATOR, ADMIN  
**Query params:** `status`, `date` (YYYY-MM-DD), `plate`, `page` (default 0), `size` (default 20)  
**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "plateNumber": "51F-123.45",
        "slotCode": "A01",
        "entryTime": "2025-06-15T08:32:11Z",
        "exitTime": null,
        "durationSeconds": null,
        "status": "ACTIVE"
      }
    ],
    "totalElements": 42,
    "totalPages": 3,
    "page": 0,
    "size": 20
  }
}
```

### GET /api/v1/sessions/{id}
**Response 200:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "plateNumber": "51F-123.45",
    "slot": { "id": "uuid", "slotCode": "A01", "zone": "A" },
    "entryGate": { "id": "uuid", "gateCode": "GATE_ENTRY_01" },
    "exitGate": null,
    "entryTime": "2025-06-15T08:32:11Z",
    "exitTime": null,
    "durationSeconds": null,
    "status": "ACTIVE",
    "entryImageUrl": "http://localhost:9000/parking-frames/frames/2025/06/15/GATE_ENTRY_01/IN_...jpg?X-Amz-...",
    "exitImageUrl": null,
    "entryPlateImageUrl": "http://localhost:9000/parking-frames/frames/2025/06/15/GATE_ENTRY_01/IN_....plate.jpg?X-Amz-...",
    "exitPlateImageUrl": null
  }
}
```
> `entryImageUrl`/`exitImageUrl`: **presigned URL** (MinIO, hết hạn ~15 phút) tới ảnh **full** chụp lúc xe
> vào/ra (đã vẽ khung quanh biển + in biển đã đọc); `null` khi chưa có ảnh hoặc object storage tắt.
> `entryPlateImageUrl`/`exitPlateImageUrl`: presigned URL tới ảnh **crop cận cảnh biển** lưu kèm
> (key `<...>.plate.jpg`); `null` tương ứng. Operator/Admin xem mọi phiên; tài xế chỉ nhận URL cho
> **xe của mình** (qua `GET /driver/sessions/{id}`, đã kiểm tra ownership). Ảnh nằm trong bucket private,
> chỉ truy cập qua presigned URL.

### GET /api/v1/slots/availability
**Response 200:**
```json
{
  "success": true,
  "data": {
    "totalSlots": 50,
    "occupiedSlots": 32,
    "emptySlots": 18,
    "maintenanceSlots": 0,
    "occupancyRate": 0.64
  }
}
```

### POST /api/v1/slots/resync
**Auth:** ADMIN  
**Mô tả:** BR-003-4 — đồng bộ lại trạng thái slot theo các session `ACTIVE` (ground truth).  
**Response 200:**
```json
{
  "success": true,
  "data": {
    "totalSlots": 50,
    "occupiedSlots": 32,
    "emptySlots": 18,
    "maintenanceSlots": 0,
    "correctedSlots": 3
  }
}
```

### POST /api/v1/slots
**Auth:** ADMIN  
**Mô tả:** Tạo 1 slot. Sức chứa bãi = số dòng bảng `slots`.  
**Request:**
```json
{ "slotCode": "A11", "zone": "A" }
```
**Response 201:** slot vừa tạo (shape như item của `GET /slots`). 409 nếu `slotCode` đã tồn tại.

### DELETE /api/v1/slots/{id}
**Auth:** ADMIN  
**Response 204:** No Content. 409 nếu slot đang `OCCUPIED` (có xe); 404 nếu không tồn tại.

### PATCH /api/v1/slots/{id}/status
**Auth:** ADMIN  
**Mô tả:** Đặt slot `EMPTY` hoặc `MAINTENANCE` (bảo trì). Không cho đặt `OCCUPIED` thủ công, không đổi slot đang có xe.  
**Request:**
```json
{ "status": "MAINTENANCE" }
```
**Response 200:** slot sau cập nhật. 409 nếu slot đang `OCCUPIED` hoặc `status` không hợp lệ.

### POST /api/v1/slots/provision
**Auth:** ADMIN  
**Mô tả:** Cấu hình nhanh một khu (zone): đặt khu có **đúng** `count` slot. Hệ thống tự sinh mã `{zone}01..{zone}NN`, tạo phần thiếu và xóa phần dư. **Không xóa slot đang có xe** — nếu việc giảm chạm vào slot `OCCUPIED` thì trả 409 và không đổi gì.  
**Request:**
```json
{ "zone": "A", "count": 50 }
```
**Response 200:**
```json
{
  "success": true,
  "data": {
    "zone": "A",
    "zoneTotal": 50,
    "created": 40,
    "removed": 0,
    "total": 80
  }
}
```

### POST /api/v1/sessions/manual-entry
**Auth:** OPERATOR, ADMIN  
**Mô tả:** BR-001 bypass — Operator cho xe có biển **không khớp regex** (NG/NN ngoại giao, xe nước ngoài, biển hỏng) hoặc ALPR đọc lỗi vào bãi. Bỏ qua kiểm tra định dạng (operator xác nhận) nhưng vẫn áp BR-002 (blacklist/duplicate/đầy bãi).  
**Request:**
```json
{ "plateNumber": "NG-123-45", "gateId": "GATE_ENTRY_01", "note": "Biển ngoại giao" }
```
**Response 201:** session detail vừa tạo. 409 nếu blacklist/đã có session/đầy bãi; 404 nếu gate không tồn tại.

### POST /api/v1/sessions/manual-exit
**Auth:** OPERATOR, ADMIN  
**Mô tả:** Operator cho xe ra theo biển (bypass regex). Đóng session ACTIVE nếu có; nếu không → tạo `REQUIRES_ATTENTION` + mở barie (BR-006-5).  
**Request:** giống `manual-entry` (`gateId` = cổng ra).  
**Response 200:** session detail (CLOSED hoặc REQUIRES_ATTENTION).

### POST /api/v1/sessions/{id}/resolve
**Auth:** OPERATOR, ADMIN  
**Mô tả:** BR-006-5 — Operator đối soát session `REQUIRES_ATTENTION` (xe ra không khớp session).  
**Request:**
```json
{ "status": "CLOSED", "note": "Đã kiểm tra camera, xe có vào lúc 21:05" }
```
`status` ∈ `CLOSED | CANCELLED`. **Response 200:** session detail đã cập nhật. 409 nếu session không ở trạng thái `REQUIRES_ATTENTION`; 404 nếu không tồn tại.
> Liệt kê việc cần xử lý: `GET /api/v1/sessions?status=REQUIRES_ATTENTION`.

### GET /api/v1/slots
**Auth:** OPERATOR, ADMIN  
**Response 200:** `data` is an array
```json
{
  "success": true,
  "data": [
    { "id": "uuid", "slotCode": "A01", "zone": "A", "status": "EMPTY", "currentSessionId": null }
  ]
}
```

### GET /api/v1/gates
**Auth:** OPERATOR, ADMIN  
**Response 200:** `data` is an array
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "gateCode": "GATE_ENTRY_01",
      "direction": "IN",
      "status": "CLOSED",
      "lastCommand": "OPEN",
      "lastCommandAt": "2025-06-15T08:32:12Z"
    }
  ]
}
```

### POST /api/v1/vehicles/whitelist
**Auth:** ADMIN  
**Request:**
```json
{
  "plateNumber": "51F-123.45",
  "ownerName": "Nguyễn Văn A",
  "note": "Xe của giám đốc",
  "force": false
}
```
> `force` (optional, mặc định `false`): chỉ cần khi biển **đang nằm ở danh sách đối diện** (blacklist).
> Cùng type = cập nhật idempotent. Khác type mà `force=false` → **409** (xem dưới); `force=true` → chuyển danh sách.

**Response 201:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "plateNumber": "51F-123.45",
    "vehicleType": "WHITELIST",
    "ownerName": "Nguyễn Văn A",
    "note": "Xe của giám đốc",
    "createdAt": "2025-06-15T08:00:00Z",
    "reclassifiedFrom": null
  }
}
```
> `reclassifiedFrom`: `null` cho thêm mới / cập nhật cùng loại; bằng loại cũ (vd `"BLACKLIST"`) khi `force=true` đã **chuyển** biển từ danh sách kia sang.

**Response 409 (cần xác nhận chuyển danh sách):** khi biển đã ở blacklist và `force=false`.
```json
{
  "success": false,
  "error": {
    "code": "CONFLICT",
    "message": "RECLASSIFY:BLACKLIST:Biển 51F-123.45 đang ở BLACKLIST — xác nhận để chuyển sang WHITELIST",
    "field": null
  }
}
```
> `message` có dạng `RECLASSIFY:{loạiHiệnTại}:{môTả}` để client dựng hộp xác nhận, rồi gọi lại với `force=true`.

### GET /api/v1/vehicles/whitelist
**Auth:** OPERATOR, ADMIN  
**Response 200:** `data` is an array of vehicles (same shape as the POST response `data`).

### DELETE /api/v1/vehicles/whitelist/{plate}
**Auth:** ADMIN  
**Response 204:** No Content. 404 if the plate is not whitelisted.

### POST /api/v1/vehicles/blacklist
**Auth:** ADMIN  
**Mô tả:** BR-002-1 — xe trong blacklist bị **từ chối vào** (không mở barie, ghi log). Idempotent trên biển số: thêm lại cùng loại = cập nhật. Nếu biển **đang ở whitelist**, cần `force=true` để chuyển (giống whitelist, đảo chiều) — nếu không trả **409 `RECLASSIFY:WHITELIST:...`**.
**Request:**
```json
{
  "plateNumber": "51H-BAD.00",
  "ownerName": null,
  "note": "Xe vi phạm ngày 2025-05-10",
  "force": false
}
```
**Response 201:** giống whitelist nhưng `vehicleType = "BLACKLIST"` (kèm `reclassifiedFrom`). **409** khi cần xác nhận chuyển từ whitelist.

### GET /api/v1/vehicles/blacklist
**Auth:** OPERATOR, ADMIN  
**Response 200:** `data` là mảng vehicle (cùng shape POST), chỉ gồm xe `BLACKLIST`.

### DELETE /api/v1/vehicles/blacklist/{plate}
**Auth:** ADMIN  
**Response 204:** No Content. 404 nếu biển không nằm trong blacklist.

---

## Alerts (BR-007 — cảnh báo nghiệp vụ/an ninh, parking-service)

> Phát sinh tự động khi luồng session gặp bất thường: trùng biển đang trong bãi (nghi clone), trúng blacklist, ra không khớp session, đọc biển độ tin cậy thấp. Đẩy **real-time qua SSE** cho operator + lưu để đối soát.

### GET /api/v1/alerts
**Auth:** OPERATOR, ADMIN  
**Query:** `status` (NEW|ACKNOWLEDGED, optional), `page` (0), `size` (20)  
**Response 200:** `data` là trang cảnh báo (cùng shape phân trang như `/sessions`), mỗi item:
```json
{
  "id": "uuid",
  "alertType": "DUPLICATE_ACTIVE_ENTRY",
  "severity": "CRITICAL",
  "plateNumber": "51F-123.45",
  "gateId": "GATE_ENTRY_01",
  "sessionId": null,
  "imageUrl": "https://minio/...(presigned)",
  "message": "Biển đang trong bãi lại quét vào — nghi biển giả/clone",
  "status": "NEW",
  "acknowledgedBy": null,
  "acknowledgedAt": null,
  "createdAt": "2026-06-27T08:00:00Z"
}
```
> `alertType` ∈ `DUPLICATE_ACTIVE_ENTRY | BLACKLIST_HIT | UNMATCHED_EXIT | LOW_CONFIDENCE`; `severity` ∈ `CRITICAL | WARNING`.

### POST /api/v1/alerts/{id}/ack
**Auth:** OPERATOR, ADMIN  
**Mô tả:** Đánh dấu cảnh báo đã xử lý (idempotent). Ghi `acknowledgedBy` = user hiện tại.  
**Response 200:** alert sau cập nhật (`status = "ACKNOWLEDGED"`). 404 nếu không tồn tại.

### GET /api/v1/alerts/stream
**Auth:** OPERATOR, ADMIN (JWT qua header `Authorization`)  
**Mô tả:** Luồng **Server-Sent Events** (`text/event-stream`). Mỗi cảnh báo mới phát 1 event `alert` với `data` = object alert (shape như trên). Client (web/mobile) gửi JWT qua header — không dùng `EventSource` thuần (không gắn được header) mà đọc stream bằng fetch/dio.

### POST /api/v1/gates/{id}/override
**Auth:** OPERATOR, ADMIN  
**Request:**
```json
{
  "command": "OPEN",
  "reason": "Xe bị kẹt, cần mở thủ công"
}
```
**Response 200:** `data` is the gate (same shape as a `GET /gates` item).

---

## billing-service API (:8082)

### GET /api/v1/billing/invoices
**Auth:** OPERATOR, ADMIN  
**Mô tả:** Danh sách hóa đơn có phân trang + lọc, mới nhất trước (thay cho tra cứu thủ công theo id).  
**Query params:** `status` (PENDING|PAID|WAIVED), `plate` (khớp một phần, không phân biệt hoa thường), `date` (YYYY-MM-DD, theo giờ ra), `page` (default 0), `size` (default 20)  
**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "invoiceId": "uuid",
        "sessionId": "uuid",
        "plateNumber": "51F-123.45",
        "entryTime": "2025-06-15T08:32:11Z",
        "exitTime": "2025-06-15T11:47:03Z",
        "durationMinutes": 195,
        "ratePerMin": 166.67,
        "peakApplied": true,
        "overnightApplied": false,
        "amount": 50000,
        "status": "PENDING"
      }
    ],
    "totalElements": 42,
    "totalPages": 3,
    "page": 0,
    "size": 20
  }
}
```

### GET /api/v1/billing/sessions/{sessionId}
**Response 200:**
```json
{
  "success": true,
  "data": {
    "invoiceId": "uuid",
    "sessionId": "uuid",
    "plateNumber": "51F-123.45",
    "entryTime": "2025-06-15T08:32:11Z",
    "exitTime": "2025-06-15T11:47:03Z",
    "durationMinutes": 195,
    "ratePerMin": 166.67,
    "peakApplied": true,
    "overnightApplied": false,
    "amount": 50000,
    "status": "PENDING"
  }
}
```

### POST /api/v1/billing/sessions/{sessionId}/pay
**Auth:** OPERATOR, ADMIN  
**Request:**
```json
{
  "method": "CASH",
  "amountPaid": 50000,
  "note": "Khách trả tròn"
}
```
**Response 200:**
```json
{
  "success": true,
  "data": { "invoiceId": "uuid", "status": "PAID", "paidAt": "..." }
}
```

### POST /api/v1/billing/sessions/{sessionId}/momo
**Auth:** OPERATOR, ADMIN  
**Mô tả:** Tạo phiên thanh toán MoMo (gate self-pay) cho hóa đơn PENDING của session. Trả về
`payUrl`/`qrCodeUrl` (render QR tại cổng ra cho khách quét). KHÔNG đổi trạng thái hóa đơn —
xác nhận qua endpoint `/momo/status`. Không tạo topic/bảng mới: khi `status` xác nhận PAID sẽ
ghi `payment` (method=ONLINE, provider_ref=MoMo transId) và phát lại event `billing.payment.completed`.
**Response 200:**
```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "invoiceId": "uuid",
    "amount": 12000,
    "orderId": "uuid",
    "payUrl": "https://test-payment.momo.vn/...",
    "qrCodeUrl": "https://test-payment.momo.vn/...",
    "deeplink": "momo://...",
    "status": "PENDING",
    "message": "Successful."
  }
}
```
**Lỗi:** 409 `INVALID_PAYMENT` (hóa đơn không PENDING) · 404 `INVOICE_NOT_FOUND` · 502 `PAYMENT_GATEWAY_ERROR`.

### GET /api/v1/billing/sessions/{sessionId}/momo/status?orderId={orderId}
**Auth:** OPERATOR, ADMIN  
**Query:** `orderId` — giá trị trả về từ endpoint tạo MoMo ở trên.  
**Mô tả:** Query MoMo và reconcile. Khi MoMo báo đã trả → đánh dấu hóa đơn PAID + phát
`billing.payment.completed`. Idempotent (gọi lại sau khi PAID là no-op).
**Response 200:**
```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "invoiceId": "uuid",
    "amount": 12000,
    "orderId": "uuid",
    "status": "PAID",
    "message": "PAID"
  }
}
```

### POST /api/v1/billing/sessions/{sessionId}/payos
**Auth:** OPERATOR, ADMIN  
**Mô tả:** Tạo phiên thanh toán PayOS (gate self-pay) cho hóa đơn PENDING — song song MoMo.
Trả về `checkoutUrl` + `qrCode`. Xác nhận qua `/payos/status` hoặc webhook `/payos/webhook`.
**Response 200:**
```json
{
  "success": true,
  "data": {
    "sessionId": "uuid",
    "invoiceId": "uuid",
    "amount": 12000,
    "orderCode": "1720000000",
    "checkoutUrl": "https://pay.payos.vn/web/...",
    "qrCode": "000201010212...",
    "status": "PENDING",
    "message": "PayOS payment link created"
  }
}
```
**Lỗi:** 409 `INVALID_PAYMENT` · 404 `INVOICE_NOT_FOUND` · 502 `PAYMENT_GATEWAY_ERROR`.

### GET /api/v1/billing/sessions/{sessionId}/payos/status?orderCode={orderCode}
**Auth:** OPERATOR, ADMIN  
**Query:** `orderCode` — giá trị trả về từ endpoint tạo PayOS.  
**Mô tả:** Query PayOS và reconcile → PAID + `billing.payment.completed` khi đã thanh toán.

### POST /api/v1/billing/payos/webhook
**Auth:** PUBLIC (PayOS checksum)  
**Mô tả:** Webhook PayOS — settle invoice PAID khi `code == "00"`.

### GET /api/v1/billing/rates
**Auth:** OPERATOR, ADMIN  
**Response 200:** the currently effective rate + its peak schedules
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "ratePerMin": 200,
    "peakMultiplier": 1.5,
    "overnightFlat": 30000,
    "minCharge": 5000,
    "effectiveFrom": "2025-01-01T00:00:00Z",
    "effectiveTo": null,
    "schedules": [
      { "hourStart": 7, "hourEnd": 9, "isPeak": true, "dayType": "ALL" },
      { "hourStart": 17, "hourEnd": 19, "isPeak": true, "dayType": "ALL" }
    ]
  }
}
```

### PUT /api/v1/billing/rates
**Auth:** ADMIN  
**Request:**
```json
{ "ratePerMin": 220, "peakMultiplier": 1.5, "overnightFlat": 30000, "minCharge": 5000 }
```
**Response 200:** the new effective rate (same shape as `GET /billing/rates`). Supersedes the previous
version (closes its `effectiveTo`) and carries the schedules over.

### GET /api/v1/billing/report/daily
**Auth:** ADMIN  
**Query:** `date` (YYYY-MM-DD, default today)  
**Response 200:**
```json
{
  "success": true,
  "data": {
    "date": "2025-06-15",
    "totalSessions": 87,
    "totalRevenue": 4350000,
    "peakSessions": 23,
    "avgDurationMinutes": 142,
    "revenueByHour": [ { "hour": 8, "revenue": 450000, "sessions": 12 } ]
  }
}
```

### GET /api/v1/billing/report/monthly
**Auth:** ADMIN  
**Query:** `month` (YYYY-MM, default current month)  
**Response 200:**
```json
{
  "success": true,
  "data": {
    "month": "2025-06",
    "totalSessions": 1820,
    "totalRevenue": 91000000,
    "prevMonthRevenue": 84000000,
    "growthRate": 0.0833,
    "avgDailyRevenue": 3033333,
    "revenueByDay": [ { "date": "2025-06-01", "revenue": 2900000 } ]
  }
}
```

---

## admin-service API (:8083)

### POST /api/v1/auth/login
**Auth:** PUBLIC  
**Request:**
```json
{ "username": "admin", "password": "secret" }
```
**Response 200:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 28800,
    "role": "ADMIN"
  }
}
```

### POST /api/v1/auth/refresh
**Auth:** PUBLIC (validates the refresh token)  
**Request:**
```json
{ "refreshToken": "..." }
```
**Response 200:** new access token (same shape as `POST /auth/login`; the same refresh token is returned).

### POST /api/v1/auth/logout
**Auth:** PUBLIC  
**Request (optional):**
```json
{ "refreshToken": "..." }
```
**Response 200:** `{ "success": true, "data": null }`. If a refresh token is supplied it is revoked;
with no body it is a client-side logout (no-op server-side).

### GET /api/v1/users
**Auth:** ADMIN  
**Query:** `page` (default 0), `size` (default 20)  
**Response 200:** `data` is a `Page` of users
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "username": "operator01",
        "email": "op01@parking.vn",
        "role": "OPERATOR",
        "isActive": true,
        "createdAt": "2025-06-15T08:00:00Z"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "page": 0,
    "size": 20
  }
}
```

### POST /api/v1/users
**Auth:** ADMIN  
**Request:**
```json
{
  "username": "operator01",
  "email": "op01@parking.vn",
  "password": "SecurePass123!",
  "role": "OPERATOR"
}
```
**Response 201:** `data` is the created user (same shape as a `GET /users` item). 409 if username/email exists.

### PUT /api/v1/users/{id}/role
**Auth:** ADMIN  
**Request:**
```json
{ "role": "ADMIN" }
```
**Response 200:** the updated user. 404 if the user does not exist.

### PUT /api/v1/users/{id}/activate
**Auth:** ADMIN  
**Mô tả:** Bật lại tài khoản đã bị vô hiệu hóa (`isActive=true`) để đăng nhập lại được.  
**Response 200:** the updated user. 404 if the user does not exist.

### DELETE /api/v1/users/{id}
**Auth:** ADMIN  
**Response 204:** No Content. Deactivates the user (`isActive=false`). 404 if not found.

### GET /api/v1/audit-logs
**Auth:** ADMIN  
**Query:** `action`, `userId`, `from`, `to`, `page`, `size`  
**Response 200:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "uuid",
        "userId": "uuid",
        "username": "admin",
        "action": "GATE_OVERRIDE",
        "targetEntity": "Gate",
        "targetId": "uuid",
        "payload": { "command": "OPEN", "reason": "..." },
        "sourceService": "parking-service",
        "createdAt": "2025-06-15T10:22:00Z"
      }
    ],
    "totalElements": 156
  }
}
```

---

## Phase 2 — Driver (Accepted — ADR-010)

> ✅ **Trạng thái: ACCEPTED** (ADR-010, 2026-06-22). Các endpoint dưới đây đã chốt để triển khai.
>
> **Nguyên tắc kiến trúc:** Driver auth nằm trong **admin-service** (không tạo microservice mới).
> Access token của tài xế mang claim `role: DRIVER` và `plates: [...]` (danh sách biển số **đã được duyệt**
> `verified=true` của tài xế đó). `parking-service` & `billing-service` đọc claim `plates` để lọc dữ liệu —
> **KHÔNG gọi chéo DB** sang admin_db. Khi danh sách biển số đổi/được duyệt → cấp lại token.
> Role `DRIVER` chỉ truy cập các endpoint `/driver/**`; KHÔNG được dùng API operator/admin.

### admin-service (:8083) — Driver auth & vehicles

#### POST /api/v1/driver/auth/request-otp
**Auth:** PUBLIC (rate-limited theo SĐT/IP)
**Request:**
```json
{ "phone": "0901234567" }
```
**Response 200:** (không tiết lộ SĐT đã đăng ký hay chưa)
```json
{ "success": true, "data": { "channel": "SMS", "expiresIn": 300, "resendAfter": 60 } }
```

#### POST /api/v1/driver/auth/verify-otp
**Auth:** PUBLIC
**Mô tả:** Xác thực OTP. Nếu `phone` chưa tồn tại → tạo tài khoản tài xế mới (đăng ký). `fullName` bắt buộc ở lần đăng ký đầu.
**Request:**
```json
{ "phone": "0901234567", "code": "123456", "fullName": "Trần Văn B" }
```
**Response 200:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "expiresIn": 28800,
    "role": "DRIVER"
  }
}
```
400 nếu OTP sai/hết hạn; 429 nếu vượt số lần thử.

#### POST /api/v1/driver/auth/refresh
**Auth:** PUBLIC (validate refresh token)
**Request:** `{ "refreshToken": "..." }`
**Response 200:** access token mới (cùng shape `verify-otp`).

#### POST /api/v1/driver/auth/logout
**Auth:** DRIVER
**Request (optional):** `{ "refreshToken": "..." }`
**Response 200:** `{ "success": true, "data": null }` (revoke refresh token nếu có).

#### GET /api/v1/driver/me
**Auth:** DRIVER
**Response 200:**
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "phone": "0901234567",
    "fullName": "Trần Văn B",
    "vehicles": [
      { "id": "uuid", "plateNumber": "51F-123.45", "verified": true,  "createdAt": "2026-06-22T08:00:00Z" },
      { "id": "uuid", "plateNumber": "51G-678.90", "verified": false, "createdAt": "2026-06-22T09:00:00Z" }
    ]
  }
}
```

#### POST /api/v1/driver/me/vehicles
**Auth:** DRIVER
**Mô tả:** Tài xế khai báo biển số. Tạo với `verified=false`, **chờ operator/admin duyệt** mới có hiệu lực (mới vào claim `plates`).
**Request:** `{ "plateNumber": "51G-678.90" }`
**Response 201:** vehicle vừa tạo (shape như item trong `GET /driver/me`). 409 nếu tài xế đã khai biển này.

#### DELETE /api/v1/driver/me/vehicles/{plate}
**Auth:** DRIVER
**Response 204:** No Content. 404 nếu tài xế không sở hữu biển này.

#### GET /api/v1/driver-vehicles
**Auth:** OPERATOR, ADMIN
**Mô tả:** Danh sách yêu cầu gắn biển của tài xế để duyệt.
**Query:** `verified` (true|false, mặc định false = pending), `page`, `size`
**Response 200:** `Page` of
```json
{
  "id": "uuid",
  "driverId": "uuid",
  "driverPhone": "0901234567",
  "driverName": "Trần Văn B",
  "plateNumber": "51G-678.90",
  "verified": false,
  "createdAt": "2026-06-22T09:00:00Z"
}
```

#### POST /api/v1/driver-vehicles/{id}/verify
**Auth:** OPERATOR, ADMIN
**Mô tả:** Duyệt/từ chối yêu cầu gắn biển. `approved=true` → `verified=true` (biển vào claim ở token sau). `approved=false` → xóa yêu cầu.
**Request:** `{ "approved": true }`
**Response 200:** driver-vehicle đã cập nhật (shape như `GET /driver-vehicles` item). 404 nếu không tồn tại.

### parking-service (:8081) — Driver sessions

#### GET /api/v1/driver/sessions
**Auth:** DRIVER
**Mô tả:** Phiên gửi xe của các biển trong claim `plates`. Server lọc theo claim, KHÔNG nhận `plate` từ client.
**Query:** `status`, `page` (default 0), `size` (default 20)
**Response 200:** cùng shape `Page<SessionDTO>` của `GET /sessions`.

#### GET /api/v1/driver/sessions/{id}
**Auth:** DRIVER
**Response 200:** session detail (shape như `GET /sessions/{id}`). **403** nếu `plateNumber` của session không thuộc claim `plates`; 404 nếu không tồn tại.

### billing-service (:8082) — Driver invoices & online payment

#### GET /api/v1/driver/invoices
**Auth:** DRIVER
**Mô tả:** Hóa đơn của các biển trong claim `plates`.
**Query:** `status` (PENDING|PAID), `page`, `size`
**Response 200:** `Page` of invoice (shape như `data` của `GET /billing/sessions/{sessionId}`).

#### GET /api/v1/driver/invoices/{invoiceId}
**Auth:** DRIVER
**Response 200:** invoice detail (shape như `GET /billing/sessions/{sessionId}`). 403 nếu biển không thuộc claim; 404 nếu không tồn tại.

#### POST /api/v1/driver/invoices/{invoiceId}/pay
**Auth:** DRIVER
**Mô tả:** Tài xế tự thanh toán online (QR). Tạo `payment` với `payer_type=DRIVER`, `method=ONLINE`,
`received_by=NULL`, `driver_id` = tài xế. Tích hợp cổng thanh toán thật **ngoài phạm vi Phase 2** —
mock trả `PAID` ngay; thật sẽ trả intent `PENDING` + `qrData/payUrl` rồi xác nhận qua webhook.
Khi `PAID` → reuse Kafka event `billing.payment.completed` (KHÔNG tạo topic mới).
**Request:** `{ "method": "ONLINE" }`
**Response 200:**
```json
{
  "success": true,
  "data": {
    "paymentId": "uuid",
    "invoiceId": "uuid",
    "status": "PAID",
    "method": "ONLINE",
    "amountPaid": 50000,
    "qrData": null,
    "paidAt": "2026-06-22T11:50:00Z"
  }
}
```
403 nếu hóa đơn không thuộc claim; 409 nếu hóa đơn đã `PAID`.

---

## Kafka Event Payloads

### parking.plate.detected
```json
{
  "eventId": "uuid",
  "plateNumber": "51F-123.45",
  "confidence": 0.94,
  "gateId": "GATE_ENTRY_01",
  "direction": "IN",
  "timestamp": "2025-06-15T08:32:11.432Z",
  "imageRef": "frames/20250615/083211_entry.jpg",
  "processingMs": 412
}
```

### parking.gate.command
```json
{
  "eventId": "uuid",
  "gateId": "GATE_ENTRY_01",
  "command": "OPEN",
  "sessionId": "uuid",
  "triggeredAt": "2025-06-15T08:32:12.100Z"
}
```

### parking.gate.state
> Producer: **edge-agent** · Consumer: **parking-service** (đồng bộ `gates.status`), **admin-service** (audit). Key = `gateId`.
> Edge-agent phát event này mỗi khi trạng thái vật lý của barie đổi: mở theo lệnh, **auto-đóng sau `GATE_AUTO_CLOSE_SECONDS` (BR-006-2)**, đóng theo lệnh, hoặc reset CLOSED lúc edge khởi động. Đây là nguồn sự thật để `parking_db.gates.status` (hiển thị trên web/mobile) không bị kẹt ở OPEN.
```json
{
  "eventId": "uuid",
  "gateId": "GATE_ENTRY_01",
  "status": "CLOSED",
  "reason": "auto",
  "timestamp": "2025-06-15T08:32:22.110Z"
}
```
> `status`: `OPEN` | `CLOSED`. `reason`: `command` (do lệnh OPEN/CLOSE) | `auto` (timer BR-006-2) | `startup` (edge khởi động → reset CLOSED).

### parking.session.created
```json
{
  "eventId": "uuid",
  "sessionId": "uuid",
  "plateNumber": "51F-123.45",
  "slotId": "uuid",
  "slotCode": "A01",
  "entryTime": "2025-06-15T08:32:11Z",
  "gateId": "GATE_ENTRY_01"
}
```

### parking.session.closed
```json
{
  "eventId": "uuid",
  "sessionId": "uuid",
  "plateNumber": "51F-123.45",
  "entryTime": "2025-06-15T08:32:11Z",
  "exitTime": "2025-06-15T11:47:03Z",
  "durationSeconds": 11692,
  "whitelisted": false
}
```
> `whitelisted`: BR-005-4 — `true` nếu biển nằm trong whitelist. billing-service khi đó tạo invoice
> với `amount = 0` và `status = WAIVED` (miễn phí), thay vì `PENDING`. Duration/rate vẫn được lưu để truy vết.

### billing.invoice.calculated
```json
{
  "eventId": "uuid",
  "invoiceId": "uuid",
  "sessionId": "uuid",
  "plateNumber": "51F-123.45",
  "amount": 50000,
  "peakApplied": true,
  "status": "PENDING"
}
```

### billing.payment.completed
```json
{
  "eventId": "uuid",
  "paymentId": "uuid",
  "invoiceId": "uuid",
  "sessionId": "uuid",
  "plateNumber": "51F-123.45",
  "amountPaid": 50000,
  "method": "CASH",
  "status": "PAID",
  "paidAt": "2025-06-15T11:50:00Z"
}
```
