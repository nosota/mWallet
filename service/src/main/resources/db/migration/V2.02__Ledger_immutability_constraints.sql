-- V2.02: Ledger Immutability and Data Integrity Constraints
--
-- This migration adds critical constraints to ensure ledger compliance:
-- 1. Prevent UPDATE/DELETE operations (immutability)
-- 2. Add NOT NULL constraints
-- 3. Add performance indexes
-- 4. Add data validation constraints

-- ==================== IMMUTABILITY ENFORCEMENT ====================

-- Prevent UPDATEs on transaction table (immutable ledger)
CREATE OR REPLACE FUNCTION prevent_transaction_update()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Transaction records are immutable. Use reversal entries (release/cancel) instead.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_transaction_update
    BEFORE UPDATE ON transaction
    FOR EACH ROW
EXECUTE FUNCTION prevent_transaction_update();

-- Prevent DELETEs on transaction table
CREATE OR REPLACE FUNCTION prevent_transaction_delete()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Transaction records cannot be deleted. Ledger must be complete and auditable.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_transaction_delete
    BEFORE DELETE ON transaction
    FOR EACH ROW
EXECUTE FUNCTION prevent_transaction_delete();

-- Apply same rules to transaction_snapshot
CREATE TRIGGER trg_prevent_transaction_snapshot_update
    BEFORE UPDATE ON transaction_snapshot
    FOR EACH ROW
EXECUTE FUNCTION prevent_transaction_update();

CREATE TRIGGER trg_prevent_transaction_snapshot_delete
    BEFORE DELETE ON transaction_snapshot
    FOR EACH ROW
EXECUTE FUNCTION prevent_transaction_delete();

-- ==================== DATA INTEGRITY CONSTRAINTS ====================

-- Ensure critical fields are never NULL
ALTER TABLE transaction ALTER COLUMN amount SET NOT NULL;
ALTER TABLE transaction ALTER COLUMN reference_id SET NOT NULL;
ALTER TABLE transaction ALTER COLUMN wallet_id SET NOT NULL;
ALTER TABLE transaction ALTER COLUMN type SET NOT NULL;
ALTER TABLE transaction ALTER COLUMN status SET NOT NULL;

-- Ensure amount sign matches transaction type
-- DEBIT must be negative, CREDIT must be positive
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_amount_type
    CHECK (
        (type = 'DEBIT' AND amount < 0) OR
        (type = 'CREDIT' AND amount > 0) OR
        (type = 'LEDGER')  -- LEDGER can be any sign
        );

-- ==================== PERFORMANCE INDEXES ====================

-- Index for fast lookup by reference_id (transaction groups)
CREATE INDEX IF NOT EXISTS idx_transaction_reference_id ON transaction(reference_id);

-- Composite index for wallet balance queries
CREATE INDEX IF NOT EXISTS idx_transaction_wallet_status ON transaction(wallet_id, status);

-- Index for reconciliation queries
CREATE INDEX IF NOT EXISTS idx_transaction_reference_status ON transaction(reference_id, status);

-- Index for timestamp-based queries (audit reports)
CREATE INDEX IF NOT EXISTS idx_transaction_hold_timestamp ON transaction(hold_reserve_timestamp);
CREATE INDEX IF NOT EXISTS idx_transaction_confirm_timestamp ON transaction(confirm_reject_timestamp);

-- ==================== TRANSACTION GROUP CONSTRAINTS ====================

-- Ensure transaction group fields are not NULL
ALTER TABLE transaction_group ALTER COLUMN status SET NOT NULL;

-- Index for transaction group lookups
CREATE INDEX IF NOT EXISTS idx_transaction_group_status ON transaction_group(status);

-- ==================== AUDIT TRAIL ENHANCEMENTS ====================

-- Add comments for documentation
COMMENT ON TABLE transaction IS 'Immutable ledger of all financial operations. Records cannot be modified or deleted after creation.';
COMMENT ON COLUMN transaction.amount IS 'Transaction amount. Negative for DEBIT, positive for CREDIT. Must match type constraint.';
COMMENT ON COLUMN transaction.reference_id IS 'Links related transactions together. Used for double-entry accounting validation.';
COMMENT ON COLUMN transaction.status IS 'HOLD (pending), SETTLED (finalized), RELEASED (returned after dispute), CANCELLED (aborted before settlement)';

COMMENT ON TABLE transaction_group IS 'Groups related transactions for atomic settlement. Sum of amounts must be zero for valid group.';
COMMENT ON COLUMN transaction_group.status IS 'IN_PROGRESS (pending), SETTLED (completed), RELEASED (reversed), CANCELLED (aborted)';

-- ==================== VALIDATION REPORT ====================

-- Create view for quick validation of ledger integrity
CREATE OR REPLACE VIEW ledger_validation AS
SELECT
    'Total transactions' as metric,
    COUNT(*)::TEXT as value
FROM transaction
UNION ALL
SELECT
    'Zero-sum check (should be 0)',
    COALESCE(SUM(amount), 0)::TEXT
FROM transaction
WHERE status = 'SETTLED'
UNION ALL
SELECT
    'Groups with non-zero sum',
    COUNT(DISTINCT reference_id)::TEXT
FROM (
         SELECT reference_id, SUM(amount) as total
         FROM transaction
         WHERE status = 'HOLD'
         GROUP BY reference_id
         HAVING SUM(amount) != 0
     ) violations;

COMMENT ON VIEW ledger_validation IS 'Quick validation view. Zero-sum check should always be 0 for valid ledger.';
