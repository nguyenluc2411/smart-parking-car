# Architecture Decision Records (ADR)

## ADR-001: Event-Driven với Apache Kafka

**Status:** Accepted  
**Date:** 06/2025

**Context:** Cần giao tiếp bất đồng bộ giữa edge-agent, parking-service và billing-service đảm bảo không mất event.

**Decision:** Dùng Apache Kafka KRaft mode (không ZooKeeper). Outbox Pattern đảm bảo at-least-once.

**Consequences:** Kafka là dependency bắt buộc. Consumer lag phải được monitor liên tục.

---

## ADR-002: Database Per Service

**Status:** Accepted

**Decision:** Mỗi service có PostgreSQL riêng. Cross-service data access qua event hoặc API.

**Consequences:** Không thể JOIN cross-service. Eventual consistency được chấp nhận.

---

## ADR-003: Choreography Saga (không Orchestrator)

**Status:** Accepted

**Decision:** Mỗi service tự xử lý compensating action. Không có central orchestrator.

**Consequences:** Logic phân tán hơn nhưng không có SPOF. Phù hợp 4 service.

---

## ADR-004: YOLOv8-s cho ALPR

**Status:** Accepted

**Decision:** YOLOv8-s (small) — mAP 99.3% @ 30+ FPS trên CPU. Fine-tune trên dataset biển số VN.

**Consequences:** Cần dataset biển số VN để fine-tune. Giai đoạn RBL dùng simulate/trigger.

---

## ADR-005: Constructor Injection (không Field Injection)

**Status:** Accepted

**Decision:** Bắt buộc dùng Constructor Injection với @RequiredArgsConstructor.

**Consequences:** Code dễ test hơn (mock dễ hơn). Không thể tạo circular dependency ngầm.

---

## ADR-006: Outbox Pattern cho Kafka Publish

**Status:** Accepted

**Decision:** Ghi business data VÀ outbox event trong cùng DB transaction. Background thread publish lên Kafka.

**Consequences:** Thêm bảng outbox_events. Thêm background thread. Đổi lại: zero message loss.

---

## ADR-007: Flyway cho DB Migration

**Status:** Accepted

**Decision:** Dùng Flyway. Không dùng Hibernate auto DDL trong production (spring.jpa.hibernate.ddl-auto=validate).

**Consequences:** Mọi schema change phải qua migration file. Đổi lại: schema có version rõ ràng, rollback được.

---

## ADR-008: Partial unique index thay vì table constraint cho sessions

**Status:** Accepted

**Context:** V1 dùng `CONSTRAINT uq_plate_active UNIQUE NULLS NOT DISTINCT (plate_number, status)`. Constraint này enforce unique trên mọi status, nên xe vào bãi lần 2 (đã có row CLOSED cũ cùng `plate_number`) bị block — vi phạm sai BR-002-4 (rule chỉ yêu cầu duy nhất session ACTIVE).

**Decision:** Thay table constraint bằng PARTIAL unique index (V2 migration): `CREATE UNIQUE INDEX uq_one_active_session_per_plate ON sessions(plate_number) WHERE status='ACTIVE'`.

**Consequences:** Chỉ enforce unique khi `status='ACTIVE'`. Nhiều CLOSED sessions cùng `plate_number` là hợp lệ → enforce đúng BR-002-4. Không sửa application code (`SessionServiceImpl` giữ nguyên).

---

## ADR-009: Flutter mobile app (2 flavor) bổ sung cho frontend

**Status:** Accepted
**Date:** 2026-06

**Context:** Ngoài admin-dashboard (Next.js), dự án cần ứng dụng mobile cho (1) nhân viên vận hành/quản trị thao tác tại chỗ và (2) tài xế tra cứu phiên/hóa đơn/thanh toán. Stack frontend gốc trong CLAUDE.md chỉ định nghĩa Next.js.

**Decision:** Bổ sung một ứng dụng **Flutter** (Dart) tại `frontend/mobile-app`, dùng **2 flavor** trong cùng codebase:
- `operator` — Phase 1: tiêu thụ các REST API hiện có (`docs/api-contracts.md`), KHÔNG sửa backend.
- `driver` — Phase 2: cần backend mở rộng (tài khoản tài xế, auth riêng, endpoint `my-sessions/my-invoices/pay`). Mọi endpoint/bảng mới PHẢI cập nhật `api-contracts.md` + `database-schema.md` trước khi code.

Kiến trúc: clean architecture (core / data / domain-repo / presentation). State: **Riverpod**. HTTP: **dio** (interceptor gắn JWT). Charts: **fl_chart**. Mock: dio interceptor đọc `assets/mock/api-mock.json` (bản sao của `frontend/mock/api-mock.json`) khi `USE_MOCK=true`, tương đương MSW của web.

**Consequences:** Thêm Flutter vào toolchain frontend. `docs/api-contracts.md` trở thành nguồn sự thật chung cho cả web lẫn mobile. Phase 1 độc lập backend; Phase 2 bị chặn bởi việc bổ sung contract cho tài xế.

---

## ADR-010: Contract backend cho luồng tài xế (Phase 2 — flavor `driver`)

**Status:** Accepted
**Date:** 2026-06-22

**Context:** ADR-009 chặn Phase 2 cho tới khi có contract tài xế. Cần: (1) tài khoản & auth riêng cho tài xế; (2) tài xế tra cứu phiên/hóa đơn của **xe của mình**; (3) tự thanh toán. Ràng buộc bất biến của hệ thống: KHÔNG tạo microservice mới (chỉ 4 service), Database-Per-Service (không gọi chéo DB), KHÔNG tạo Kafka topic mới ngoài danh sách.

**Decision (chờ duyệt):**
1. **Auth tài xế bằng OTP qua SĐT**, đặt trong **admin-service** (không tạo service mới). Thêm bảng `drivers`, `otp_codes`, `driver_refresh_tokens` trong `admin_db`. Role mới `DRIVER` chỉ truy cập các endpoint `/driver/**`.
2. **Liên kết tài xế ↔ biển số cần duyệt:** bảng `driver_vehicles` với `verified` (operator/admin duyệt). Chỉ biển `verified=true` mới có hiệu lực.
3. **JWT mang claim `plates`** (danh sách biển đã duyệt). `parking-service` & `billing-service` lọc `my-sessions`/`my-invoices` theo claim — **không gọi chéo admin_db**, giữ Database-Per-Service & stateless. Token cấp lại khi danh sách biển đổi.
4. **Thanh toán online (QR)** của tài xế: mở rộng bảng `payments` (`payer_type`, `driver_id`, `provider_ref`, `received_by` nullable, `method=ONLINE`). Tích hợp cổng thanh toán thật **ngoài phạm vi RBL** — mock trả `PAID`. Khi `PAID` reuse event `billing.payment.completed` (KHÔNG tạo topic mới).

**Consequences:** Thêm role `DRIVER` + bộ endpoint `/driver/**` xuyên 3 service, nhưng không phá ràng buộc kiến trúc (không service/topic mới, không gọi chéo DB). Phụ thuộc tin cậy vào claim `plates` trong JWT → bảo mật phụ thuộc việc ký/verify token bằng `JWT_SECRET` chung. Chi tiết endpoint ở mục "Phase 2 — Driver (PROPOSED)" trong `api-contracts.md`; chi tiết bảng ở mục "PROPOSED — Phase 2 Driver" trong `database-schema.md`. **Khi duyệt:** đổi Status các mục PROPOSED → Accepted rồi mới code (Entity → migration → repo → service → controller per-service, sau đó DriverApp Flutter).

---

## ADR-011: Lưu ảnh chụp xe vào/ra (truy vết) bằng MinIO + presigned URL

**Status:** Accepted
**Date:** 2026-06-25

**Context:** Operator/nhân viên cần xem lại **ảnh chụp lúc xe vào và ra** của mỗi phiên để truy vết tranh chấp. Hiện edge-agent vứt bytes ảnh đi (chỉ đẩy tên file vào `imageRef`), bảng `sessions` không có cột ảnh. Ràng buộc: KHÔNG tạo microservice mới, KHÔNG tạo Kafka topic mới, edge-agent stateless.

**Decision:**
1. **Object storage = MinIO (S3-compatible)**, thêm 1 container hạ tầng (không phải microservice nghiệp vụ). Bucket private `parking-frames`. Không lưu bytes trong DB (tránh phình parking_db).
2. **edge-agent upload frame** lúc `/detect` → object key, đẩy key vào `imageRef` của event `parking.plate.detected` (**field đã có sẵn — không đổi schema event, không topic mới**). Ảnh là external state nên edge-agent vẫn "stateless" (không có DB cục bộ). Upload best-effort: lỗi không chặn detection.
3. **parking-service** lưu key vào cột mới `sessions.entry_image_ref`/`exit_image_ref` (migration `V4`), và sinh **presigned URL** (hết hạn ~15 phút) khi trả session detail.
4. **Cơ chế URL = presigned** (không proxy qua service): vì thẻ `<img>`/`Image.network` không gắn được header `Authorization`. Kiểm soát "ai nhận URL" đã do endpoint session-detail đảm nhiệm (operator `getById`; tài xế `getByIdForDriver` kiểm tra ownership theo claim `plates`). Client ký theo **public-endpoint** của MinIO để trình duyệt/mobile truy cập được.

**Consequences:** Thêm dependency `minio` (edge-agent + parking-service) và 2 cột DB — đã ghi vào `database-schema.md`/`api-contracts.md`. MinIO phải expose port cho client; presigned URL hết hạn ngắn nên ai có link vẫn xem được trong cửa sổ đó (chấp nhận với RBL). **Retention/cleanup ảnh và mã hoá at-rest: ngoài phạm vi RBL Phase 1.**
