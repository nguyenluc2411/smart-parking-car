CREATE TABLE parking_floors (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parking_lot_id  UUID NOT NULL REFERENCES parking_lots(id) ON DELETE CASCADE,
    floor_code      VARCHAR(20) NOT NULL,
    name            VARCHAR(80) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (parking_lot_id, floor_code)
);

CREATE TABLE parking_zones (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    floor_id    UUID NOT NULL REFERENCES parking_floors(id) ON DELETE CASCADE,
    zone_code   VARCHAR(10) NOT NULL,
    name        VARCHAR(80) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (floor_id, zone_code)
);

INSERT INTO parking_floors (parking_lot_id, floor_code, name, sort_order)
SELECT id, 'GROUND', 'Mặt đất', 0 FROM parking_lots
ON CONFLICT (parking_lot_id, floor_code) DO NOTHING;

INSERT INTO parking_zones (floor_id, zone_code, name)
SELECT f.id, s.zone, 'Khu ' || s.zone
FROM parking_floors f
JOIN parking_lots l ON l.id = f.parking_lot_id AND l.lot_code = 'DEFAULT'
CROSS JOIN (SELECT DISTINCT zone FROM slots) s
WHERE f.floor_code = 'GROUND'
ON CONFLICT (floor_id, zone_code) DO NOTHING;

ALTER TABLE slots ADD COLUMN zone_id UUID REFERENCES parking_zones(id);
UPDATE slots s SET zone_id = z.id
FROM parking_zones z
JOIN parking_floors f ON f.id = z.floor_id
JOIN parking_lots l ON l.id = f.parking_lot_id
WHERE l.lot_code = 'DEFAULT' AND z.zone_code = s.zone;

ALTER TABLE gates
    ADD COLUMN parking_lot_id UUID REFERENCES parking_lots(id),
    ADD COLUMN floor_id UUID REFERENCES parking_floors(id);
UPDATE gates g SET parking_lot_id = l.id, floor_id = f.id
FROM parking_lots l JOIN parking_floors f ON f.parking_lot_id = l.id
WHERE l.lot_code = 'DEFAULT' AND f.floor_code = 'GROUND';

ALTER TABLE parking_layouts ADD COLUMN floor_id UUID REFERENCES parking_floors(id);
UPDATE parking_layouts pl SET floor_id = f.id
FROM parking_floors f WHERE f.parking_lot_id = pl.parking_lot_id AND f.floor_code = 'GROUND';
ALTER TABLE parking_layouts ALTER COLUMN floor_id SET NOT NULL;
ALTER TABLE parking_layouts DROP CONSTRAINT parking_layouts_parking_lot_id_key;
ALTER TABLE parking_layouts ADD CONSTRAINT uq_parking_layout_floor UNIQUE (floor_id);

ALTER TABLE slots DROP CONSTRAINT slots_slot_code_key;
CREATE UNIQUE INDEX uq_slots_zone_code ON slots(zone_id, slot_code) WHERE zone_id IS NOT NULL;

CREATE INDEX idx_slots_zone_id ON slots(zone_id);
CREATE INDEX idx_gates_parking_lot_id ON gates(parking_lot_id);
