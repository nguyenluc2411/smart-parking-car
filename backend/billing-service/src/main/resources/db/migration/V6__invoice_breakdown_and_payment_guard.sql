-- V6__invoice_breakdown_and_payment_guard.sql
-- billing_db (PostgreSQL :5435)
-- Source of truth: docs/database-schema.md, docs/business-rules.md (BR-004, BR-005).

-- 1. Invoice breakdown (BR-004) ------------------------------------------------------------
-- peak_applied/overnight_applied are booleans: they say a peak block existed, not how many, so a
-- receipt cannot show the driver how the amount was reached. Snapshot the block counts and the
-- tariff used, so an issued invoice stays re-derivable after the rate table changes.
ALTER TABLE invoices ADD COLUMN block_minutes      INTEGER;
ALTER TABLE invoices ADD COLUMN normal_blocks      BIGINT;
ALTER TABLE invoices ADD COLUMN peak_blocks        BIGINT;
ALTER TABLE invoices ADD COLUMN overnight_nights   BIGINT;
ALTER TABLE invoices ADD COLUMN peak_multiplier    DECIMAL(4,2);
ALTER TABLE invoices ADD COLUMN overnight_flat     DECIMAL(10,2);
ALTER TABLE invoices ADD COLUMN min_charge_applied BOOLEAN;
-- Nullable on purpose: invoices issued before this migration have no breakdown and must not be
-- back-filled with guesses. Clients render the detailed lines only when block_minutes IS NOT NULL.

-- 2. Double-payment backstop (BR-005-2) ----------------------------------------------------
-- The service only pays a PENDING invoice, but that read-then-write is not atomic: a MoMo IPN and
-- an operator taking cash can both see PENDING and both insert. The status check is now behind a
-- SELECT ... FOR UPDATE; this constraint is the backstop that makes a second payment impossible
-- even if some future path forgets the lock. One invoice is settled by exactly one payment.

-- 2a. Quarantine any invoice that was ALREADY paid twice before the guard existed.
-- This is not hypothetical: the dev database had one (two ONLINE payments 3.4s apart on the same
-- invoice) — precisely the race this index closes. The index cannot be created while they sit in
-- the table, but these rows are records of money that really moved: a customer may have been
-- charged twice and be owed a refund. So they are MOVED, never deleted — deleting them would make
-- the double charge undiscoverable, which is the one outcome worse than the duplicate itself.
CREATE TABLE payment_duplicates (
    LIKE payments INCLUDING DEFAULTS,
    quarantined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason         TEXT        NOT NULL
);
COMMENT ON TABLE payment_duplicates IS
    'Payments beyond the first for one invoice, moved aside when uq_payments_invoice_id was added '
    '(BR-005-2). Each row means an invoice was settled more than once — reconcile against the '
    'gateway and refund the payer if the money really was taken twice. Not written at runtime.';

WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY invoice_id ORDER BY paid_at, id) AS seq
    FROM payments
),
-- Keep the EARLIEST payment per invoice: that is the one that actually settled it; anything after
-- it is the duplicate to be refunded.
moved AS (
    DELETE FROM payments p
    USING ranked r
    WHERE p.id = r.id AND r.seq > 1
    RETURNING p.*
)
INSERT INTO payment_duplicates
SELECT moved.*, NOW(),
       'duplicate payment for the same invoice, quarantined by V6 (BR-005-2)'
FROM moved;

CREATE UNIQUE INDEX uq_payments_invoice_id ON payments (invoice_id);
