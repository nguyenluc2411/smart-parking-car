-- V2__seed_default_rate.sql
-- A single active rate is required before any invoice can be calculated (BR-004).
-- created_by = system sentinel UUID (no admin user in billing_db; rate authored by the system).

INSERT INTO rates (id, rate_per_min, peak_multiplier, overnight_flat, min_charge,
                   effective_from, effective_to, created_by)
VALUES ('00000000-0000-0000-0000-0000000000a1',
        200.00,    -- VNĐ/phút (≈ 12.000đ/giờ)
        1.5,       -- BR-004-2 peak multiplier
        30000.00,  -- BR-004-3 overnight flat
        5000.00,   -- BR-004-4 min charge
        TIMESTAMPTZ '2025-01-01 00:00:00+07',
        NULL,      -- currently effective
        '00000000-0000-0000-0000-000000000000');

-- Peak windows per BR-004-2 (07:00–09:00, 17:00–19:00). Seeded per database-schema.md;
-- the current FeeCalculator derives peak from BR-004 windows directly (see ADR note in code).
INSERT INTO rate_schedules (rate_id, hour_start, hour_end, is_peak, day_type) VALUES
    ('00000000-0000-0000-0000-0000000000a1', 7, 9, true, 'ALL'),
    ('00000000-0000-0000-0000-0000000000a1', 17, 19, true, 'ALL');
