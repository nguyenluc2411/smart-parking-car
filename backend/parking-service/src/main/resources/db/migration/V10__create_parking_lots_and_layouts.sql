CREATE TABLE parking_lots (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_code    VARCHAR(30) UNIQUE NOT NULL,
    name        VARCHAR(120) NOT NULL,
    address     VARCHAR(255),
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE parking_layouts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parking_lot_id      UUID UNIQUE NOT NULL REFERENCES parking_lots(id) ON DELETE CASCADE,
    canvas_width        INTEGER NOT NULL DEFAULT 1200,
    canvas_height       INTEGER NOT NULL DEFAULT 720,
    draft_elements      JSONB NOT NULL DEFAULT '[]'::jsonb,
    published_elements  JSONB NOT NULL DEFAULT '[]'::jsonb,
    draft_version       INTEGER NOT NULL DEFAULT 1,
    published_version   INTEGER NOT NULL DEFAULT 0,
    published_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO parking_lots (lot_code, name, address)
VALUES ('DEFAULT', 'Bãi xe mặc định', 'Chưa cấu hình địa chỉ')
ON CONFLICT (lot_code) DO NOTHING;

INSERT INTO parking_layouts (parking_lot_id)
SELECT id FROM parking_lots WHERE lot_code = 'DEFAULT'
ON CONFLICT (parking_lot_id) DO NOTHING;
