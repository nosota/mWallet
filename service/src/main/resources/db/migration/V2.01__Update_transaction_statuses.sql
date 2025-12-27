-- V2.01: Ledger Compliance Phase 1 - Update Transaction Status Values
--
-- This migration updates the allowed status values to comply with banking ledger standards:
--
-- TransactionStatus changes:
--   REMOVED: RESERVE, CONFIRMED, REJECTED
--   KEPT:    HOLD
--   ADDED:   SETTLED, RELEASED, CANCELLED
--
-- TransactionGroupStatus changes:
--   REMOVED: CONFIRMED, REJECTED
--   KEPT:    IN_PROGRESS
--   ADDED:   SETTLED, RELEASED, CANCELLED
--
-- Note: Database is empty, so no data migration needed.
-- Adding CHECK constraints to enforce valid status values.

-- Add CHECK constraint for transaction.status
-- Valid values: HOLD, SETTLED, RELEASED, CANCELLED
ALTER TABLE transaction
    ADD CONSTRAINT chk_transaction_status
        CHECK (status IN ('HOLD', 'SETTLED', 'RELEASED', 'CANCELLED'));

-- Add CHECK constraint for transaction_group.status
-- Valid values: IN_PROGRESS, SETTLED, RELEASED, CANCELLED
ALTER TABLE transaction_group
    ADD CONSTRAINT chk_transaction_group_status
        CHECK (status IN ('IN_PROGRESS', 'SETTLED', 'RELEASED', 'CANCELLED'));

-- Add CHECK constraint for transaction_snapshot.status
-- Valid values: HOLD, SETTLED, RELEASED, CANCELLED
ALTER TABLE transaction_snapshot
    ADD CONSTRAINT chk_transaction_snapshot_status
        CHECK (status IN ('HOLD', 'SETTLED', 'RELEASED', 'CANCELLED'));

-- Add CHECK constraint for transaction_snapshot_archive.status (if exists)
-- Note: V1.04 created this table
ALTER TABLE transaction_snapshot_archive
    ADD CONSTRAINT chk_transaction_snapshot_archive_status
        CHECK (status IN ('HOLD', 'SETTLED', 'RELEASED', 'CANCELLED'));

-- Add comments documenting the status meanings
COMMENT ON COLUMN transaction.status IS 'Transaction status: HOLD (blocked funds), SETTLED (final execution), RELEASED (returned after dispute), CANCELLED (cancelled before conditions met)';
COMMENT ON COLUMN transaction_group.status IS 'Transaction group status: IN_PROGRESS (pending), SETTLED (completed successfully), RELEASED (returned to sender), CANCELLED (aborted)';
COMMENT ON COLUMN transaction_snapshot.status IS 'Snapshot transaction status: HOLD (blocked), SETTLED (final), RELEASED (returned), CANCELLED (aborted)';
