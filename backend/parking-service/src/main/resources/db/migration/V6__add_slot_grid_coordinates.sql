-- V6__add_slot_grid_coordinates.sql
-- parking_db (PostgreSQL :5434) -- slot map for drivers (BR-003-6).
-- Source of truth: docs/database-schema.md.

-- A slot_code like "A05" says which slot but not WHERE it is, so nothing can draw a map. Store a
-- grid position per zone; the client lays the zone out as rows x columns and highlights the
-- driver's slot. Grid coordinates only — no metres, no rotation: a lot map is read at a glance,
-- and a to-scale floor plan is a different (and much larger) problem.
ALTER TABLE slots ADD COLUMN grid_row INTEGER;
ALTER TABLE slots ADD COLUMN grid_col INTEGER;

-- Backfill existing slots so the map works on already-seeded lots instead of rendering blank.
-- Row-major within each zone, 10 per row, ordered by slot_code — which is how the seeded codes
-- (A01, A02, ...) were laid out anyway.
WITH ordered AS (
    SELECT id,
           (ROW_NUMBER() OVER (PARTITION BY zone ORDER BY slot_code) - 1) AS seq
    FROM slots
)
UPDATE slots s
SET grid_row = ordered.seq / 10,
    grid_col = ordered.seq % 10
FROM ordered
WHERE s.id = ordered.id;

-- Two slots cannot occupy the same cell of the same zone.
CREATE UNIQUE INDEX uq_slots_zone_grid ON slots (zone, grid_row, grid_col)
    WHERE grid_row IS NOT NULL AND grid_col IS NOT NULL;
