-- V7__create_reservation_fees.sql
-- billing_db (PostgreSQL :5435) -- booking fee for driver reservations (BR-009-11).
-- Source of truth: docs/database-schema.md.

-- Deliberately its own table, NOT a row in `payments`: `payments.invoice_id` is NOT NULL (every
-- payment there settles a real parking invoice), and a reservation is not a session/invoice yet.
-- Bolting this onto `payments` would mean nullable invoice_id everywhere and touching the
-- already-working invoice reconciliation logic for no benefit.
--
-- `reservation_id` is a loose cross-service reference (parking-service owns the reservation
-- itself, Database Per Service) -- no FK, just an opaque UUID the client supplies and we trust
-- because it's scoped to the same DRIVER JWT both services validate independently.
CREATE TABLE reservation_fees (
    id                  UUID PRIMARY KEY,
    reservation_id      UUID NOT NULL,
    driver_id           UUID NOT NULL,
    plate_number        VARCHAR(15) NOT NULL,
    reservation_start_time TIMESTAMPTZ NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    status              VARCHAR(20) NOT NULL,   -- PENDING | PAID | REFUNDED | FORFEITED
    provider            VARCHAR(20) NOT NULL,   -- PAYOS
    payos_order_code    BIGINT,
    provider_ref        VARCHAR(100),
    paid_at             TIMESTAMPTZ,
    refunded_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One live (PENDING/PAID) fee per reservation -- a driver cannot spin up two payment links for
-- the same booking.
CREATE UNIQUE INDEX uq_reservation_fees_reservation_live ON reservation_fees (reservation_id)
    WHERE status IN ('PENDING', 'PAID');

-- PayOS order codes must be globally unique per merchant account -- shared uniqueness check
-- against this table happens in application code alongside invoices' own order codes.
CREATE UNIQUE INDEX uq_reservation_fees_payos_order_code ON reservation_fees (payos_order_code)
    WHERE payos_order_code IS NOT NULL;

CREATE INDEX idx_reservation_fees_driver ON reservation_fees (driver_id);
