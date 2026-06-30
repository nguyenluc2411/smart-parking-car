-- V3__alter_payments_driver.sql
-- billing_db (PostgreSQL :5435) -- Phase 2 driver online payment (ADR-010)
-- Source of truth: docs/database-schema.md ("Phase 2 Driver").

-- Distinguish operator (CASH/QR at the gate) from driver (ONLINE via app) payments.
ALTER TABLE payments ALTER COLUMN received_by DROP NOT NULL;          -- NULL when payer is DRIVER
ALTER TABLE payments ADD COLUMN payer_type   VARCHAR(20) NOT NULL DEFAULT 'OPERATOR'; -- OPERATOR | DRIVER
ALTER TABLE payments ADD COLUMN driver_id    UUID;                    -- driver id (online); no cross-DB FK
ALTER TABLE payments ADD COLUMN provider_ref VARCHAR(100);            -- payment-gateway transaction ref
