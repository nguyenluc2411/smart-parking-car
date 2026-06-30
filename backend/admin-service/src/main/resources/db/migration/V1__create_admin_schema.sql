-- V1__create_admin_schema.sql
-- admin_db (PostgreSQL :5433)
-- Source of truth: docs/database-schema.md -- do NOT add tables/columns not listed there.

-- users ---------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(50) UNIQUE NOT NULL,
    email         VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,                       -- BCrypt
    role          VARCHAR(20) NOT NULL,                        -- ADMIN | OPERATOR
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- refresh_tokens ------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    is_revoked  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- audit_logs ----------------------------------------------------------------
CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID REFERENCES users(id),                 -- NULL if system event
    action          VARCHAR(100) NOT NULL,                     -- e.g. USER_LOGIN, RATE_UPDATED
    target_entity   VARCHAR(50),                               -- e.g. Session, Invoice
    target_id       VARCHAR(100),
    payload         JSONB,
    source_service  VARCHAR(50) NOT NULL,                      -- service that emitted
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);

-- system_config -------------------------------------------------------------
CREATE TABLE system_config (
    key         VARCHAR(100) PRIMARY KEY,
    value       TEXT NOT NULL,
    description VARCHAR(255),
    updated_by  UUID REFERENCES users(id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO system_config (key, value, description, updated_by) VALUES
    ('operating_hours_start', '06:00', 'Giờ mở cửa', NULL),
    ('operating_hours_end',   '22:00', 'Giờ đóng cửa', NULL),
    ('total_slots',           '50',    'Tổng số slot', NULL);
