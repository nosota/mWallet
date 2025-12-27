-- V2.03: Adjust Immutability for Archiving Architecture
--
-- This migration adjusts immutability constraints to support the three-tier archiving architecture:
-- 1. transaction (hot data) - can be archived to snapshot
-- 2. transaction_snapshot (warm data) - can be archived to archive
-- 3. transaction_snapshot_archive (cold data) - IMMUTABLE final storage
--
-- Philosophy:
-- - UPDATE is NEVER allowed (data integrity)
-- - DELETE is allowed for archiving (performance optimization)
-- - Archive table is the immutable ledger (compliance)

-- ==================== REMOVE DELETE TRIGGERS (Allow Archiving) ====================

-- Remove DELETE trigger from transaction table (allow snapshot archiving)
DROP TRIGGER IF EXISTS trg_prevent_transaction_delete ON transaction;

-- Remove DELETE trigger from transaction_snapshot table (allow archive migration)
DROP TRIGGER IF EXISTS trg_prevent_transaction_snapshot_delete ON transaction_snapshot;

-- Note: UPDATE triggers remain in place - records still cannot be modified

-- ==================== ADD IMMUTABILITY TO ARCHIVE (Final Storage) ====================

-- Archive is the IMMUTABLE final storage - enforce strict immutability

-- Prevent UPDATE on archive (data integrity)
CREATE OR REPLACE FUNCTION prevent_archive_update()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Archive records are immutable and cannot be modified. Archive table is the final ledger storage.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_archive_update
    BEFORE UPDATE ON transaction_snapshot_archive
    FOR EACH ROW
EXECUTE FUNCTION prevent_archive_update();

-- Prevent DELETE on archive (compliance and audit trail)
CREATE OR REPLACE FUNCTION prevent_archive_delete()
    RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Archive records cannot be deleted. Archive table is the immutable ledger for compliance and audit.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_archive_delete
    BEFORE DELETE ON transaction_snapshot_archive
    FOR EACH ROW
EXECUTE FUNCTION prevent_archive_delete();

-- ==================== DOCUMENTATION ====================

COMMENT ON TABLE transaction IS
'Hot data - operational transactions. Records can be archived to transaction_snapshot but cannot be modified (UPDATE blocked).';

COMMENT ON TABLE transaction_snapshot IS
'Warm data - recent snapshots and LEDGER checkpoints. Records can be archived to transaction_snapshot_archive but cannot be modified (UPDATE blocked).';

COMMENT ON TABLE transaction_snapshot_archive IS
'Cold data - immutable final storage. Records CANNOT be modified or deleted. This is the permanent ledger for compliance and audit trail.';

-- ==================== VALIDATION VIEW UPDATE ====================

-- Update validation view to check archive immutability
DROP VIEW IF EXISTS ledger_validation;

CREATE OR REPLACE VIEW ledger_validation AS
SELECT
    'Total transactions (active)' as metric,
    COUNT(*)::TEXT as value
FROM transaction
UNION ALL
SELECT
    'Total snapshots (warm)',
    COUNT(*)::TEXT
FROM transaction_snapshot
UNION ALL
SELECT
    'Total archived (cold)',
    COUNT(*)::TEXT
FROM transaction_snapshot_archive
UNION ALL
SELECT
    'Zero-sum check (active SETTLED)',
    COALESCE(SUM(amount), 0)::TEXT
FROM transaction
WHERE status = 'SETTLED'
UNION ALL
SELECT
    'Zero-sum check (snapshot SETTLED)',
    COALESCE(SUM(amount), 0)::TEXT
FROM transaction_snapshot
WHERE status = 'SETTLED'
UNION ALL
SELECT
    'Zero-sum check (archive)',
    COALESCE(SUM(amount), 0)::TEXT
FROM transaction_snapshot_archive
UNION ALL
SELECT
    'Groups with non-zero sum (active)',
    COUNT(DISTINCT reference_id)::TEXT
FROM (
    SELECT reference_id, SUM(amount) as total
    FROM transaction
    WHERE status = 'HOLD'
    GROUP BY reference_id
    HAVING SUM(amount) != 0
) violations;

COMMENT ON VIEW ledger_validation IS
'Validation view for three-tier architecture. Monitors active transactions, snapshots, and immutable archive.';

-- ==================== SUMMARY ====================

-- Architecture after V2.03:
--
-- transaction:
--   ✅ UPDATE blocked (immutable records)
--   ✅ DELETE allowed (for snapshot archiving)
--
-- transaction_snapshot:
--   ✅ UPDATE blocked (immutable records)
--   ✅ DELETE allowed (for archive migration)
--
-- transaction_snapshot_archive:
--   ✅ UPDATE blocked (immutable records)
--   ✅ DELETE blocked (permanent ledger storage)
--
-- Full audit trail = transaction + transaction_snapshot + transaction_snapshot_archive
-- Performance optimized through archiving while maintaining complete history
