# Database Schema – Smart Parking System

> Đây là nguồn sự thật duy nhất cho schema DB.
> KHÔNG tạo bảng mới nếu không có trong tài liệu này.
> Mọi thay đổi schema phải qua Flyway migration.

---

## admin_db (PostgreSQL :5433)

### users
```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username    VARCHAR(50) UNIQUE NOT NULL,
    email       VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,        -- BCrypt
    role        VARCHAR(20) NOT NULL,            -- ADMIN | OPERATOR
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### refresh_tokens
```sql
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    is_revoked  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

### audit_logs
```sql
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),   -- NULL nếu system event
    action          VARCHAR(100) NOT NULL,        -- e.g. USER_LOGIN, RATE_UPDATED
    target_entity   VARCHAR(50),                 -- e.g. Session, Invoice
    target_id       VARCHAR(100),
    payload         JSONB,
    source_service  VARCHAR(50) NOT NULL,         -- service nào emit
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
```

### system_config
```sql
CREATE TABLE system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT NOT NULL,
    description VARCHAR(255),
    updated_by  UUID REFERENCES users(id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Seed data:
-- INSERT INTO system_config VALUES ('operating_hours_start', '06:00', 'Giờ mở cửa', null, NOW());
-- INSERT INTO system_config VALUES ('operating_hours_end', '22:00', 'Giờ đóng cửa', null, NOW());
-- INSERT INTO system_config VALUES ('total_slots', '50', 'Tổng số slot', null, NOW());
```

---

## Phase 2 Driver (Accepted — ADR-010)

> ✅ **Trạng thái: ACCEPTED** (ADR-010, 2026-06-22). Tạo migration đánh số `V{n}` tiếp theo của từng service.

### admin_db — drivers (PROPOSED)
```sql
CREATE TABLE drivers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone       VARCHAR(20) UNIQUE NOT NULL,      -- E.164/local normalized
    full_name   VARCHAR(100) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### admin_db — driver_vehicles (PROPOSED)
```sql
-- Liên kết tài xế ↔ biển số. verified=false khi tài xế khai; operator/admin duyệt → true.
-- Chỉ biển verified=true mới vào claim `plates` của JWT.
CREATE TABLE driver_vehicles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id       UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    plate_number    VARCHAR(20) NOT NULL,           -- Normalized: 51F-123.45
    verified        BOOLEAN NOT NULL DEFAULT false,
    verified_by     UUID REFERENCES users(id),      -- operator/admin đã duyệt; NULL khi chưa duyệt
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (driver_id, plate_number)
);
CREATE INDEX idx_driver_vehicles_driver_id ON driver_vehicles(driver_id);
CREATE INDEX idx_driver_vehicles_plate ON driver_vehicles(plate_number);
```

### admin_db — otp_codes (PROPOSED)
```sql
CREATE TABLE otp_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone       VARCHAR(20) NOT NULL,
    code_hash   VARCHAR(255) NOT NULL,              -- BCrypt, KHÔNG lưu OTP plaintext
    purpose     VARCHAR(20) NOT NULL DEFAULT 'LOGIN', -- LOGIN
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed    BOOLEAN NOT NULL DEFAULT false,
    attempts    INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_otp_codes_phone ON otp_codes(phone);
```

### admin_db — driver_refresh_tokens (PROPOSED)
```sql
-- Tách khỏi refresh_tokens (tham chiếu users) vì tài xế không nằm trong bảng users.
CREATE TABLE driver_refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id   UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    is_revoked  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_driver_refresh_tokens_driver_id ON driver_refresh_tokens(driver_id);
```

### billing_db — payments (PROPOSED ALTER, hỗ trợ thanh toán online của tài xế)
```sql
-- Thêm cột để phân biệt operator (CASH/QR tại quầy) vs driver (ONLINE qua app).
ALTER TABLE payments ALTER COLUMN received_by DROP NOT NULL;     -- NULL khi payer là DRIVER
ALTER TABLE payments ADD COLUMN payer_type   VARCHAR(20) NOT NULL DEFAULT 'OPERATOR'; -- OPERATOR | DRIVER
ALTER TABLE payments ADD COLUMN driver_id    UUID;              -- id tài xế (online); KHÔNG FK (khác DB)
ALTER TABLE payments ADD COLUMN provider_ref VARCHAR(100);      -- mã giao dịch cổng thanh toán (online)
-- method mở rộng: CASH | QR_CODE | ONLINE
-- Ràng buộc nghiệp vụ (enforce ở service): payer_type='OPERATOR' ⇒ received_by NOT NULL;
--                                          payer_type='DRIVER'   ⇒ driver_id NOT NULL, method='ONLINE'.
```

---

## parking_db (PostgreSQL :5434)

### vehicles
```sql
CREATE TABLE vehicles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number    VARCHAR(20) UNIQUE NOT NULL,  -- Normalized: 51F-123.45
    vehicle_type    VARCHAR(20) NOT NULL DEFAULT 'REGULAR', -- WHITELIST | BLACKLIST | REGULAR
    owner_name      VARCHAR(100),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_vehicles_plate_number ON vehicles(plate_number);
```

### slots
```sql
CREATE TABLE slots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_code           VARCHAR(10) UNIQUE NOT NULL, -- e.g. A01, B12
    zone                VARCHAR(5) NOT NULL DEFAULT 'A',
    status              VARCHAR(20) NOT NULL DEFAULT 'EMPTY', -- EMPTY | OCCUPIED | MAINTENANCE
    current_session_id  UUID,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_slots_status ON slots(status);
```

### gates
```sql
CREATE TABLE gates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gate_code       VARCHAR(20) UNIQUE NOT NULL, -- GATE_ENTRY_01, GATE_EXIT_01
    direction       VARCHAR(10) NOT NULL,         -- IN | OUT
    status          VARCHAR(20) NOT NULL DEFAULT 'CLOSED', -- OPEN | CLOSED | ERROR
    last_command    VARCHAR(20),
    last_command_at TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### sessions
```sql
CREATE TABLE sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number    VARCHAR(20) NOT NULL,
    slot_id         UUID REFERENCES slots(id),
    entry_gate_id   UUID REFERENCES gates(id),
    exit_gate_id    UUID REFERENCES gates(id),
    entry_time      TIMESTAMPTZ NOT NULL,
    exit_time       TIMESTAMPTZ,
    duration_seconds INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | ACTIVE | CLOSED | CANCELLED | REQUIRES_ATTENTION
    entry_image_ref VARCHAR(300),   -- object key (MinIO) ảnh chụp lúc VÀO — truy vết (V4 migration)
    exit_image_ref  VARCHAR(300),   -- object key (MinIO) ảnh chụp lúc RA  — truy vết (V4 migration)
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
    -- Partial unique index (V2 migration):
    -- UNIQUE INDEX uq_one_active_session_per_plate WHERE status='ACTIVE'
    -- Chỉ 1 session ACTIVE per plate tại một thời điểm (BR-002-4); cho phép nhiều CLOSED rows. Xem ADR-008.
);
CREATE INDEX idx_sessions_plate_number ON sessions(plate_number);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_entry_time ON sessions(entry_time DESC);
```
> **Ảnh vào/ra (truy vết):** chỉ **object key** được lưu trong DB (`entry_image_ref`/`exit_image_ref`),
> bytes ảnh nằm trong **MinIO** (bucket `parking-frames`). `edge-agent` upload frame lúc `/detect`,
> đẩy key qua event `parking.plate.detected.imageRef`; `parking-service` lưu key vào session và sinh
> **presigned URL** khi trả session detail (xem ADR image-storage trong `decisions.md`).

### gate_logs
```sql
CREATE TABLE gate_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gate_id         UUID NOT NULL REFERENCES gates(id),
    session_id      UUID REFERENCES sessions(id),
    command         VARCHAR(20) NOT NULL,    -- OPEN | CLOSE
    triggered_by    VARCHAR(50) NOT NULL,    -- SYSTEM | ADMIN:{userId}
    plate_number    VARCHAR(20),
    confidence      DECIMAL(5,4),           -- ALPR confidence khi trigger
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_gate_logs_gate_id ON gate_logs(gate_id);
CREATE INDEX idx_gate_logs_created_at ON gate_logs(created_at DESC);
```

### outbox_events (Outbox Pattern)
```sql
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,    -- e.g. Session, Gate
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,   -- e.g. parking.session.created
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PUBLISHED | FAILED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_status ON outbox_events(status) WHERE status = 'PENDING';
```

### alerts (BR-007 — business/security alerts)
```sql
CREATE TABLE alerts (
    id               UUID PRIMARY KEY,
    alert_type       VARCHAR(40)  NOT NULL,   -- DUPLICATE_ACTIVE_ENTRY | BLACKLIST_HIT | UNMATCHED_EXIT | LOW_CONFIDENCE
    severity         VARCHAR(20)  NOT NULL,   -- CRITICAL | WARNING
    plate_number     VARCHAR(20),
    gate_id          VARCHAR(20),             -- gate code (GATE_ENTRY_01, ...)
    session_id       UUID,
    image_ref        VARCHAR(300),            -- MinIO object key of the captured frame
    message          VARCHAR(500) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'NEW',   -- NEW | ACKNOWLEDGED
    acknowledged_by  UUID,
    acknowledged_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_alerts_status_created ON alerts (status, created_at DESC);
```
> Raised by the session flow on anomalies; pushed to operators in real time over SSE
> (`GET /api/v1/alerts/stream`) and listed/acknowledged via REST. Migration `V5__create_alerts.sql`.

---

## billing_db (PostgreSQL :5435)

### rates
```sql
CREATE TABLE rates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_per_min    DECIMAL(10,2) NOT NULL,         -- VNĐ/phút
    peak_multiplier DECIMAL(4,2) NOT NULL DEFAULT 1.5,
    overnight_flat  DECIMAL(10,2) NOT NULL DEFAULT 30000,  -- VNĐ flat rate 22h-6h
    min_charge      DECIMAL(10,2) NOT NULL DEFAULT 5000,   -- VNĐ tối thiểu
    effective_from  TIMESTAMPTZ NOT NULL,
    effective_to    TIMESTAMPTZ,                    -- NULL = hiện tại
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### rate_schedules
```sql
CREATE TABLE rate_schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_id     UUID NOT NULL REFERENCES rates(id),
    hour_start  INTEGER NOT NULL,   -- 0-23
    hour_end    INTEGER NOT NULL,   -- 0-23
    is_peak     BOOLEAN NOT NULL DEFAULT false,
    day_type    VARCHAR(20) NOT NULL DEFAULT 'ALL', -- ALL | WEEKDAY | WEEKEND
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Seed: 7-9h và 17-19h là peak, multiplier 1.5x
```

### invoices
```sql
CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID UNIQUE NOT NULL,       -- Idempotency: 1 session = 1 invoice
    plate_number        VARCHAR(20) NOT NULL,
    rate_id             UUID NOT NULL REFERENCES rates(id),
    entry_time          TIMESTAMPTZ NOT NULL,
    exit_time           TIMESTAMPTZ NOT NULL,
    duration_seconds    INTEGER NOT NULL,
    duration_minutes    INTEGER NOT NULL,
    rate_per_min        DECIMAL(10,2) NOT NULL,
    peak_applied        BOOLEAN NOT NULL DEFAULT false,
    overnight_applied   BOOLEAN NOT NULL DEFAULT false,
    amount              DECIMAL(12,2) NOT NULL,     -- VNĐ
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | WAIVED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_invoices_session_id ON invoices(session_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_created_at ON invoices(created_at DESC);
```

### payments
```sql
CREATE TABLE payments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id  UUID NOT NULL REFERENCES invoices(id),
    method      VARCHAR(20) NOT NULL,   -- CASH | QR_CODE
    amount_paid DECIMAL(12,2) NOT NULL,
    received_by UUID NOT NULL,          -- operator user_id
    note        TEXT,
    paid_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);
CREATE INDEX idx_payments_paid_at ON payments(paid_at DESC);
```

### outbox_events (Outbox Pattern – billing_db)
```sql
-- Cấu trúc giống hệt parking_db.outbox_events
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_status ON outbox_events(status) WHERE status = 'PENDING';
```

---

## Flyway Migration Naming Convention

```
V{version}__{description}.sql
V1__create_users_table.sql
V2__create_refresh_tokens_table.sql
V3__seed_admin_user.sql
V4__create_sessions_table.sql
```

Đặt tại: `src/main/resources/db/migration/`
