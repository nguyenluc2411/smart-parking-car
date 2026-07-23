-- V7__create_reservations.sql
-- parking_db (PostgreSQL :5434) -- driver slot booking (BR-009).
-- Source of truth: docs/database-schema.md, docs/business-rules.md.

CREATE TABLE reservations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id     UUID NOT NULL,                      -- admin_db.drivers.id; no cross-DB FK
    plate_number  VARCHAR(20) NOT NULL,               -- normalized, must be a verified plate
    slot_id       UUID NOT NULL REFERENCES slots(id),
    start_time    TIMESTAMPTZ NOT NULL,               -- when the driver said they would arrive
    hold_until    TIMESTAMPTZ NOT NULL,               -- start_time + grace; past this it expires
    status        VARCHAR(20)  NOT NULL DEFAULT 'HELD', -- HELD | FULFILLED | CANCELLED | EXPIRED
    session_id    UUID,                               -- the session that consumed the hold
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One live booking per plate (mirrors BR-002-4's one-ACTIVE-session-per-plate).
CREATE UNIQUE INDEX uq_reservation_held_plate
    ON reservations (plate_number) WHERE status = 'HELD';

-- A slot can only be held by one booking at a time. This is the constraint that makes overbooking
-- impossible even if two drivers race for the last slot.
CREATE UNIQUE INDEX uq_reservation_held_slot
    ON reservations (slot_id) WHERE status = 'HELD';

CREATE INDEX idx_reservations_driver ON reservations (driver_id, created_at DESC);
CREATE INDEX idx_reservations_plate ON reservations (plate_number);
-- The expiry sweep scans exactly this.
CREATE INDEX idx_reservations_hold_until ON reservations (hold_until) WHERE status = 'HELD';
