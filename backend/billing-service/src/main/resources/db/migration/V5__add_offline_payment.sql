-- V5__add_offline_payment.sql
-- billing_db (PostgreSQL :5435) -- BR-005-7 power-outage cash collection.
-- Source of truth: docs/database-schema.md.

-- Serial number of the pre-printed paper voucher handed to the driver during an outage.
-- Reconciliation: every CASH_OFFLINE payment must map to exactly one voucher, so the shift's
-- voucher count has to match the cash handed in. Without this, outage cash is untraceable.
ALTER TABLE payments ADD COLUMN offline_voucher_no VARCHAR(20);

-- One voucher can only ever settle one invoice (a reused serial means a mis-keyed entry or fraud).
CREATE UNIQUE INDEX uq_payments_offline_voucher_no
    ON payments (offline_voucher_no)
    WHERE offline_voucher_no IS NOT NULL;

-- Reports split cash-in-hand from gateway money by method; index the lookup they use.
CREATE INDEX idx_payments_method_paid_at ON payments (method, paid_at DESC);
