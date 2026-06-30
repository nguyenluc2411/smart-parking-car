-- V2__fix_plate_active_constraint.sql
-- parking_db (PostgreSQL :5434)
--
-- Problem: V1 created uq_plate_active as UNIQUE NULLS NOT DISTINCT (plate_number, status).
-- That enforces uniqueness across ALL statuses, so a 2nd visit (which leaves an old CLOSED row
-- for the same plate) collides on (plate_number, 'CLOSED'). BR-002-4 only requires uniqueness
-- of the ACTIVE session, not of CLOSED history.
--
-- Fix: replace it with a PARTIAL unique index that only enforces uniqueness while status = ACTIVE.
-- Multiple CLOSED rows per plate become valid. See ADR-008.

-- V1 created it as a table CONSTRAINT; drop that. (DROP INDEX form kept for safety if it was
-- ever materialised as a standalone index instead.)
ALTER TABLE sessions DROP CONSTRAINT IF EXISTS uq_plate_active;
DROP INDEX IF EXISTS uq_plate_active;

-- Partial unique index: only one ACTIVE session per plate at a time (BR-002-4).
CREATE UNIQUE INDEX uq_one_active_session_per_plate
    ON sessions (plate_number)
    WHERE status = 'ACTIVE';
