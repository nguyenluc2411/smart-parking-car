-- V2__create_driver_schema.sql
-- admin_db (PostgreSQL :5433) -- Phase 2 driver flow (ADR-010)
-- Source of truth: docs/database-schema.md -- do NOT add tables/columns not listed there.

-- drivers -------------------------------------------------------------------
CREATE TABLE drivers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone       VARCHAR(20) UNIQUE NOT NULL,                 -- normalized
    full_name   VARCHAR(100) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- driver_vehicles -----------------------------------------------------------
-- verified=false when the driver claims a plate; an operator/admin approves -> true.
-- Only verified=true plates enter the JWT `plates` claim.
CREATE TABLE driver_vehicles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id       UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    plate_number    VARCHAR(20) NOT NULL,                    -- Normalized: 51F-123.45
    verified        BOOLEAN NOT NULL DEFAULT false,
    verified_by     UUID REFERENCES users(id),               -- operator/admin who approved; NULL until then
    verified_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (driver_id, plate_number)
);
CREATE INDEX idx_driver_vehicles_driver_id ON driver_vehicles(driver_id);
CREATE INDEX idx_driver_vehicles_plate ON driver_vehicles(plate_number);

-- otp_codes -----------------------------------------------------------------
CREATE TABLE otp_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone       VARCHAR(20) NOT NULL,
    code_hash   VARCHAR(255) NOT NULL,                       -- BCrypt, never store OTP plaintext
    purpose     VARCHAR(20) NOT NULL DEFAULT 'LOGIN',        -- LOGIN
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed    BOOLEAN NOT NULL DEFAULT false,
    attempts    INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_otp_codes_phone ON otp_codes(phone);

-- driver_refresh_tokens -----------------------------------------------------
-- Separate from refresh_tokens (which references users) because drivers are not users.
CREATE TABLE driver_refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id   UUID NOT NULL REFERENCES drivers(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) UNIQUE NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    is_revoked  BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_driver_refresh_tokens_driver_id ON driver_refresh_tokens(driver_id);
