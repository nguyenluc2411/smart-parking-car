-- PayOS gate self-pay: map PayOS orderCode (long) back to invoice for webhook/poll settlement.
ALTER TABLE invoices ADD COLUMN payos_order_code BIGINT;

CREATE UNIQUE INDEX idx_invoices_payos_order_code
    ON invoices(payos_order_code)
    WHERE payos_order_code IS NOT NULL;
