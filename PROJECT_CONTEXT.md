# PROJECT_CONTEXT.md

> File này ghi lại context hiện tại của sprint đang làm.
> Cập nhật sau mỗi session làm việc.

---

## Trạng thái hiện tại (07/2026)

### Phase: Backend + Frontend + Deploy — chạy thông end-to-end ✅

### Đã hoàn thành
- [x] Docs (architecture, schema, business-rules, api-contracts, decisions) — đã sync với code
- [x] **parking-service** (8081): create/close session qua Kafka + REST (sessions/slots/gates/whitelist/resolve/manual-entry/exit) + JWT + outbox + Flyway V1–V7
- [x] **billing-service** (8082): invoice qua Kafka (BR-004 **Split Block**) + payment REST + rates GET/PUT + reports daily/monthly + JWT + outbox + Flyway V1–V6
- [x] **admin-service** (8083): audit-all (Kafka, mọi topic + DLT) + auth/JWT issuer (login/refresh/logout) + users CRUD + audit-logs REST + Flyway V1
- [x] **edge-agent** (8000, Python/FastAPI): ALPR simulate|real + barrier simulator (auto-close 10s) + simulate/trigger + detect + config + health/metrics
- [x] **admin-dashboard** (Next.js 14): scaffold + all pages, nối backend thật (CORS + USE_MOCK=false), build pass. **UI rebrand Indigo–Cyan** (font Inter, gradient logo/statcard/gauge, sidebar+nav polish, login hào quang) — build 14/14 pass
- [x] **mobile-app** (Flutter): Phase 1 flavor `operator` — clean architecture (Riverpod/dio/go_router/fl_chart), đủ màn hình theo api-contracts, mock layer, analyze sạch + build web pass. **UI rebrand Indigo–Cyan** (theme seed indigo + tertiary cyan, drawer gradient, login logo gradient, input/button bo góc) — analyze sạch. Flavor `driver` mới là placeholder (Phase 2, chờ backend mở rộng). Xem ADR-009
- [x] **docker-compose.yml** tổng (3 db + kafka KRaft + 4 service + prometheus + grafana) — `up --build` chạy thật, e2e verified
- [x] CORS cho 3 service Java; JWT validation cross-service
- [x] Review 5 business-rule gaps — xem [memory: business-rules-review-2026-06]

### Đang làm
- [x] BR-004-7: FeeCalculator đọc khung peak từ `rate_schedules` (DONE — 15 billing test pass)
- [x] edge real-ALPR: tooling (RESEARCH.md, alpr_eval.py, camera_agent.py) + `plate.canonicalize()`
      (dựng dấu `-` + sửa nhầm ký tự O/0, I/1… theo vị trí) + `best_plate_from_ocr()` (ghép biển bị
      OCR tách nhiều mảnh) — 16 test pass
- [x] **BR-005-7 thu tiền mặt lúc mất điện**: `CASH_OFFLINE` + `offlineVoucherNo`/`paidAt` (billing V5),
      guard "chỉ hóa đơn PENDING mới trả được" (`SELECT … FOR UPDATE` + UNIQUE `payments.invoice_id`),
      màn nhập bù ở `frontend/demo` (đăng nhập bằng tài khoản **của chính nhân viên** để `received_by` đúng người)
- [x] **Invoice breakdown + collection summary** (billing V6): hóa đơn kèm các dòng normal/peak/overnight
      (bản chụp giá lúc phát hành, `null` với hóa đơn cũ); report daily/monthly thêm `collected`
      (`cashTotal`/`gatewayTotal`/`byMethod`) — **đã tính tiền** vs **đã thu** là hai số khác nhau, chênh lệch là thứ cần soi
- [x] **BR-003-6 bản đồ bãi**: `slots.grid_row/grid_col` (parking V6) + re-flow theo `slot_code` khi
      zone thêm/xóa slot; `GET /slots` trả tọa độ
- [x] **BR-009 đặt chỗ trước cho tài xế** (parking V7): `reservations` + `slots.RESERVED`,
      `POST|GET|DELETE /api/v1/driver/reservations`, job quét hết hạn 60s, chống overbooking bằng
      **unique index từng phần** (1 lượt HELD/biển, 1 lượt HELD/slot), xe có đặt chỗ vào bãi được ưu
      tiên trước cả kiểm tra "bãi đầy", khóa quyền đặt sau 3 lần bỏ hẹn/30 ngày — 12 test
- [x] `RESERVED` tính là **đã dùng** ở `availability`/`occupancyRate` + resync **không** giải phóng slot
      đang được giữ (BR-003-4b) — lỗ hổng này sẽ trao chỗ đã hứa cho xe vãng lai kế tiếp
- [x] Docs sync: BR-003-4b/BR-003-6/BR-009 + endpoint reservations + schema `reservations`/grid cột
- [x] **E2E với DB thật (2026-07-22)** — script nằm ở `backend/e2e/`, chạy `python backend/e2e/run_all.py`:
      `docker compose up -d --build`, 4 migration mới migrate sạch; 40 check pass (đặt chỗ + tọa độ
      lưới + resync không giải phóng slot đang giữ + guard BR-009-3b + `collected`/breakdown/thu tiền
      mặt lúc mất điện/chặn thu hai lần). Đã chạy lại sau khi merge `payment-method-exclusivity`.
      **Ba thứ chỉ e2e mới lộ ra:**
      1. billing V6 **gãy trên DB thật**: đã tồn tại 1 hóa đơn bị thu **hai lần** (2 payment `ONLINE`
         cách nhau 3,4s) nên `uq_payments_invoice_id` không tạo được. V6 nay giữ payment sớm nhất và
         **dời** bản trùng sang `payment_duplicates` (không xóa — đó là tiền đã chuyển thật).
         → **Cần đối soát với cổng thanh toán và hoàn tiền nếu khách bị trừ 2 lần.**
      2. `PATCH /slots/{id}/status`, `DELETE /slots/{id}`, `provision` thu nhỏ zone đều sửa được slot
         đang bị giữ → bỏ rơi lượt đặt. Đã chặn 409 (BR-009-3b).
      3. `node-exporter` mount `/:/host:ro,rslave` làm **gãy cả `docker compose up`** trên Docker
         Desktop Windows → đổi sang `ro`.

### Mobile-app (Flutter) — bước tiếp theo
- [ ] **Phase 2 — flavor `driver`**: BỊ CHẶN, cần backend mở rộng. Trình tự: soạn đề xuất → cập nhật `docs/api-contracts.md` + `docs/database-schema.md` + ADR → duyệt → code. Cần: tài khoản tài xế, auth riêng (OTP/SĐT), endpoint my-sessions/my-invoices/pay, gắn biển số với tài khoản.
- [ ] (tùy chọn Phase 1) Android product flavors thật (appId riêng operator/driver) — hiện flavor mới ở mức Dart entrypoint

### Chưa làm (nice-to-have / out of scope)
- [ ] admin-dashboard đóng vào docker-compose (hiện chạy `npm run dev`)
- [ ] BR-005-2 Phase 2: auto-pay QR (ngoài scope RBL)
- [ ] billing `/report/export` CSV
- [ ] Monitoring mở rộng: alertmanager, node-exporter, kafka-exporter
- [x] ~~Integration tests~~ → có **e2e trên stack thật** ở `backend/e2e/` (`python backend/e2e/run_all.py`,
      40 check, stdlib thuần). Testcontainers vẫn chưa làm — e2e này cần `docker compose up` sẵn.

---

## Quyết định chưa resolve
*(Không có)*

---

## Cách chạy nhanh
```bash
cp .env.example .env            # đổi secret change_me_* trước khi dùng thật
docker compose up -d --build    # 3 db + kafka + 4 service + prometheus/grafana
# Dashboard: cd frontend/admin-dashboard && npm run dev  (http://localhost:3000)
# Smoke e2e: xem backend/edge-agent/README.md
```
> Lưu ý: `.env` hiện đặt `PARKING_OPERATING_START=00:00/END=23:59` (demo ngoài giờ) — xóa nếu muốn BR-008 thật (06:00–22:00). Seed admin: `admin / ChangeMe123!`.

---

## Last updated
2026-07-22 — BR-009 đặt chỗ trước (parking V7) + BR-003-6 bản đồ lưới (V6); BR-005-7 thu tiền mặt
lúc mất điện + invoice breakdown/collected (billing V5–V6). Sửa 2 chỗ `RESERVED` bị bỏ sót:
`availability` không đếm, và resync giải phóng nhầm slot đang giữ. Docs đã sync; parking + billing
test xanh (58 + 32), dashboard build 14/14 pass, **e2e với DB thật đã chạy** (21 check pass).
**Còn nợ: đối soát 1 hóa đơn bị thu 2 lần đang nằm trong `billing_db.payment_duplicates`.**
Trước đó: rebrand UI Indigo–Cyan, edge real-ALPR (canonicalize + best_plate_from_ocr), BR-004-7
