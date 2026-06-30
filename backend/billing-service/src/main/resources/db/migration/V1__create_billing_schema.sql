-- V1__create_billing_schema.sql
-- billing_db (PostgreSQL :5435)
-- Source of truth: docs/database-schema.md -- do NOT add tables/columns not listed there.

-- rates ---------------------------------------------------------------------
CREATE TABLE rates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_per_min    DECIMAL(10,2) NOT NULL,                       -- VNĐ/phút
    peak_multiplier DECIMAL(4,2) NOT NULL DEFAULT 1.5,
    overnight_flat  DECIMAL(10,2) NOT NULL DEFAULT 30000,         -- VNĐ flat 22h-6h
    min_charge      DECIMAL(10,2) NOT NULL DEFAULT 5000,          -- VNĐ tối thiểu
    effective_from  TIMESTAMPTZ NOT NULL,
    effective_to    TIMESTAMPTZ,                                  -- NULL = hiện tại
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- rate_schedules ------------------------------------------------------------
CREATE TABLE rate_schedules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rate_id     UUID NOT NULL REFERENCES rates(id),
    hour_start  INTEGER NOT NULL,                                 -- 0-23
    hour_end    INTEGER NOT NULL,                                 -- 0-23
    is_peak     BOOLEAN NOT NULL DEFAULT false,
    day_type    VARCHAR(20) NOT NULL DEFAULT 'ALL',               -- ALL | WEEKDAY | WEEKEND
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- invoices ------------------------------------------------------------------
CREATE TABLE invoices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID UNIQUE NOT NULL,                     -- Idempotency: 1 session = 1 invoice
    plate_number        VARCHAR(20) NOT NULL,
    rate_id             UUID NOT NULL REFERENCES rates(id),
    entry_time          TIMESTAMPTZ NOT NULL,
    exit_time           TIMESTAMPTZ NOT NULL,
    duration_seconds    INTEGER NOT NULL,
    duration_minutes    INTEGER NOT NULL,
    rate_per_min        DECIMAL(10,2) NOT NULL,
    peak_applied        BOOLEAN NOT NULL DEFAULT false,
    overnight_applied   BOOLEAN NOT NULL DEFAULT false,
    amount              DECIMAL(12,2) NOT NULL,                   -- VNĐ
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING | PAID | WAIVED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_invoices_session_id ON invoices(session_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_created_at ON invoices(created_at DESC);

-- payments ------------------------------------------------------------------
CREATE TABLE payments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id  UUID NOT NULL REFERENCES invoices(id),
    method      VARCHAR(20) NOT NULL,                             -- CASH | QR_CODE
    amount_paid DECIMAL(12,2) NOT NULL,
    received_by UUID NOT NULL,                                    -- operator user_id
    note        TEXT,
    paid_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_payments_invoice_id ON payments(invoice_id);
CREATE INDEX idx_payments_paid_at ON payments(paid_at DESC);

-- outbox_events (Outbox Pattern) -------------------------------------------
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
