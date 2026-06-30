# PROJECT_CONTEXT.md

> File này ghi lại context hiện tại của sprint đang làm.
> Cập nhật sau mỗi session làm việc.

---

## Trạng thái hiện tại (06/2026)

### Phase: Backend + Frontend + Deploy — chạy thông end-to-end ✅

### Đã hoàn thành
- [x] Docs (architecture, schema, business-rules, api-contracts, decisions) — đã sync với code
- [x] **parking-service** (8081): create/close session qua Kafka + REST (sessions/slots/gates/whitelist/resolve/manual-entry/exit) + JWT + outbox + Flyway V1–V3
- [x] **billing-service** (8082): invoice qua Kafka (BR-004 **Split Block**) + payment REST + rates GET/PUT + reports daily/monthly + JWT + outbox + Flyway V1–V2
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

### Mobile-app (Flutter) — bước tiếp theo
- [ ] **Phase 2 — flavor `driver`**: BỊ CHẶN, cần backend mở rộng. Trình tự: soạn đề xuất → cập nhật `docs/api-contracts.md` + `docs/database-schema.md` + ADR → duyệt → code. Cần: tài khoản tài xế, auth riêng (OTP/SĐT), endpoint my-sessions/my-invoices/pay, gắn biển số với tài khoản.
- [ ] (tùy chọn Phase 1) Android product flavors thật (appId riêng operator/driver) — hiện flavor mới ở mức Dart entrypoint

### Chưa làm (nice-to-have / out of scope)
- [ ] admin-dashboard đóng vào docker-compose (hiện chạy `npm run dev`)
- [ ] BR-005-2 Phase 2: auto-pay QR (ngoài scope RBL)
- [ ] billing `/report/export` CSV
- [ ] Monitoring mở rộng: alertmanager, node-exporter, kafka-exporter
- [ ] Integration tests (Testcontainers)

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
2026-06-23 — Rebrand UI cả 2 frontend (Indigo–Cyan) chuẩn bị demo; web build 14/14 pass,
mobile analyze sạch. Trước đó: edge real-ALPR (canonicalize + best_plate_from_ocr), BR-004-7
