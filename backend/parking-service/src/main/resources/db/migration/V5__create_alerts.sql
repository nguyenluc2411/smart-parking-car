-- BR-007 (business/security alerts): anomalies surfaced to operators in real time (SSE) — blacklist
-- hit, a plate already inside re-entering (possible clone), unmatched exit, low ALPR confidence.
CREATE TABLE alerts (
    id               UUID PRIMARY KEY,
    alert_type       VARCHAR(40)  NOT NULL,   -- DUPLICATE_ACTIVE_ENTRY | BLACKLIST_HIT | UNMATCHED_EXIT | LOW_CONFIDENCE
    severity         VARCHAR(20)  NOT NULL,   -- CRITICAL | WARNING
    plate_number     VARCHAR(20),
    gate_id          VARCHAR(20),             -- gate code (GATE_ENTRY_01, ...)
    session_id       UUID,
    image_ref        VARCHAR(300),            -- MinIO object key of the captured frame
    message          VARCHAR(500) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'NEW',   -- NEW | ACKNOWLEDGED
    acknowledged_by  UUID,
    acknowledged_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- The dashboard lists newest-first and filters the open (NEW) worklist.
CREATE INDEX idx_alerts_status_created ON alerts (status, created_at DESC);
