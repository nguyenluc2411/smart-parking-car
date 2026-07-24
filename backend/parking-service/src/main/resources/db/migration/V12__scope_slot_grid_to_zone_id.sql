-- The legacy grid uniqueness used the display code ("A"), which collides across parking lots.
-- Scope geometry to the real zone entity so every lot/floor may have its own Khu A.
DROP INDEX IF EXISTS uq_slots_zone_grid;

CREATE UNIQUE INDEX uq_slots_zone_id_grid
    ON slots(zone_id, grid_row, grid_col)
    WHERE zone_id IS NOT NULL AND grid_row IS NOT NULL AND grid_col IS NOT NULL;
