-- V1__create_parking_schema.sql
-- parking_db (PostgreSQL :5434)
-- Source of truth: docs/database-schema.md  -- do NOT add tables/columns not listed there.

-- vehicles ------------------------------------------------------------------
CREATE TABLE vehicles (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number    VARCHAR(20) UNIQUE NOT NULL,             -- Normalized: 51F-123.45
    vehicle_type    VARCHAR(20) NOT NULL DEFAULT 'REGULAR',  -- WHITELIST | BLACKLIST | REGULAR
    owner_name      VARCHAR(100),
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_vehicles_plate_number ON vehicles(plate_number);

-- slots ---------------------------------------------------------------------
CREATE TABLE slots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_code           VARCHAR(10) UNIQUE NOT NULL,            -- e.g. A01, B12
    zone                VARCHAR(5) NOT NULL DEFAULT 'A',
    status              VARCHAR(20) NOT NULL DEFAULT 'EMPTY',   -- EMPTY | OCCUPIED | MAINTENANCE
    current_session_id  UUID,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_slots_status ON slots(status);

-- gates ---------------------------------------------------------------------
CREATE TABLE gates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gate_code       VARCHAR(20) UNIQUE NOT NULL,               -- GATE_ENTRY_01, GATE_EXIT_01
    direction       VARCHAR(10) NOT NULL,                      -- IN | OUT
    status          VARCHAR(20) NOT NULL DEFAULT 'CLOSED',     -- OPEN | CLOSED | ERROR
    last_command    VARCHAR(20),
    last_command_at TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- sessions ------------------------------------------------------------------
CREATE TABLE sessions (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plate_number     VARCHAR(20) NOT NULL,
    slot_id          UUID REFERENCES slots(id),
    entry_gate_id    UUID REFERENCES gates(id),
    exit_gate_id     UUID REFERENCES gates(id),
    entry_time       TIMESTAMPTZ NOT NULL,
    exit_time        TIMESTAMPTZ,
    duration_seconds INTEGER,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- PENDING | ACTIVE | CLOSED | CANCELLED
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- BR-002-4: only one ACTIVE session per plate at any time
    CONSTRAINT uq_plate_active UNIQUE NULLS NOT DISTINCT (plate_number, status)
);
CREATE INDEX idx_sessions_plate_number ON sessions(plate_number);
CREATE INDEX idx_sessions_status ON sessions(status);
CREATE INDEX idx_sessions_entry_time ON sessions(entry_time DESC);

-- gate_logs -----------------------------------------------------------------
CREATE TABLE gate_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gate_id         UUID NOT NULL REFERENCES gates(id),
    session_id      UUID REFERENCES sessions(id),
    command         VARCHAR(20) NOT NULL,                      -- OPEN | CLOSE
    triggered_by    VARCHAR(50) NOT NULL,                      -- SYSTEM | ADMIN:{userId}
    plate_number    VARCHAR(20),
    confidence      DECIMAL(5,4),                              -- ALPR confidence at trigger
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_gate_logs_gate_id ON gate_logs(gate_id);
CREATE INDEX idx_gate_logs_created_at ON gate_logs(created_at DESC);

-- outbox_events (Outbox Pattern) -------------------------------------------
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,                      -- e.g. Session, Gate
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,                     -- e.g. parking.session.created
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',    -- PENDING | PUBLISHED | FAILED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_status ON outbox_events(status) WHERE status = 'PENDING';
