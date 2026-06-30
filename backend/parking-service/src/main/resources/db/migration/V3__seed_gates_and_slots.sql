-- V3__seed_gates_and_slots.sql
-- Operational reference data so the entry/exit flow can run: one entry + one exit gate,
-- and a set of EMPTY slots. Idempotent (safe to re-run).

INSERT INTO gates (gate_code, direction, status) VALUES
    ('GATE_ENTRY_01', 'IN',  'CLOSED'),
    ('GATE_EXIT_01',  'OUT', 'CLOSED')
ON CONFLICT (gate_code) DO NOTHING;

-- 10 slots in zone A (A01..A10), all EMPTY.
INSERT INTO slots (slot_code, zone, status)
SELECT 'A' || LPAD(g::text, 2, '0'), 'A', 'EMPTY'
FROM generate_series(1, 10) AS g
ON CONFLICT (slot_code) DO NOTHING;
