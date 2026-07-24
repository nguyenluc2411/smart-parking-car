-- Emergency operation: barrierless auxiliary gates and idempotent offline event replay.

ALTER TABLE gates
    ADD COLUMN has_barrier BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO gates (gate_code, direction, status, has_barrier) VALUES
    ('GATE_AUX_ENTRY', 'IN',  'OPEN', FALSE),
    ('GATE_AUX_EXIT',  'OUT', 'OPEN', FALSE)
ON CONFLICT (gate_code) DO UPDATE SET has_barrier = FALSE;

ALTER TABLE sessions
    ADD COLUMN outage_entry_event_id UUID,
    ADD COLUMN outage_exit_event_id UUID;

CREATE UNIQUE INDEX uq_sessions_outage_entry_event
    ON sessions(outage_entry_event_id)
    WHERE outage_entry_event_id IS NOT NULL;

CREATE UNIQUE INDEX uq_sessions_outage_exit_event
    ON sessions(outage_exit_event_id)
    WHERE outage_exit_event_id IS NOT NULL;
