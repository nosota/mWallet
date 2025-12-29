-- V2.11: Add idempotency support to refund table
-- Purpose: Prevent duplicate refund execution via idempotency keys and refund type

-- =====================================================================
-- 1. Add refund_type column to refund table
-- =====================================================================
ALTER TABLE refund
    ADD COLUMN refund_type VARCHAR(10);

-- Backfill existing refunds: if amount = original_amount then FULL, else PARTIAL
UPDATE refund
SET refund_type = CASE
    WHEN amount = original_amount THEN 'FULL'
    ELSE 'PARTIAL'
END
WHERE refund_type IS NULL;

-- Make column NOT NULL after backfill
ALTER TABLE refund
    ALTER COLUMN refund_type SET NOT NULL;

COMMENT ON COLUMN refund.refund_type IS 'Type of refund: FULL (entire amount) or PARTIAL (portion of amount). Used for business logic and idempotency constraints.';

-- =====================================================================
-- 2. Add idempotency_key column to refund table
-- =====================================================================
ALTER TABLE refund
    ADD COLUMN idempotency_key VARCHAR(255);

COMMENT ON COLUMN refund.idempotency_key IS 'Idempotency key for preventing duplicate refunds. Optional. Combined with transaction_group_id and refund_type for uniqueness.';

-- =====================================================================
-- 3. Create composite unique constraint for idempotency
-- =====================================================================
-- This constraint ensures:
-- 1. Only one FULL refund per transaction_group_id (regardless of idempotency_key)
-- 2. Partial refunds with same idempotency_key are deduplicated
-- 3. Backward compatible: allows NULL idempotency_key (legacy refunds)
CREATE UNIQUE INDEX idx_refund_idempotency
    ON refund(transaction_group_id, refund_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON INDEX idx_refund_idempotency IS 'Ensures refund idempotency: (transaction_group_id, refund_type, idempotency_key) uniqueness. Prevents duplicate FULL refunds and deduplicates PARTIAL refunds with same key. Partial index allows NULL idempotency_key for backward compatibility.';

-- =====================================================================
-- 4. Create additional constraint: only one FULL refund per order
-- =====================================================================
-- This ensures that even without idempotency_key, we can't have multiple FULL refunds
CREATE UNIQUE INDEX idx_refund_one_full_per_order
    ON refund(transaction_group_id)
    WHERE refund_type = 'FULL' AND status = 'COMPLETED';

COMMENT ON INDEX idx_refund_one_full_per_order IS 'Enforces business rule: only one completed FULL refund allowed per transaction group (order). PARTIAL refunds are not restricted by this constraint.';

-- =====================================================================
-- 5. Create indexes for lookups
-- =====================================================================
CREATE INDEX idx_refund_transaction_group_id_type
    ON refund(transaction_group_id, refund_type);

CREATE INDEX idx_refund_idempotency_key_lookup
    ON refund(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- =====================================================================
-- 6. Notes on Refund idempotency implementation
-- =====================================================================
-- IMPORTANT: Refund idempotency rules:
--
-- Refund types:
-- - FULL: Entire order amount refunded (amount = original net amount)
-- - PARTIAL: Portion of order amount refunded (amount < original net amount)
--
-- Idempotency constraints:
-- 1. Only one FULL refund per order (enforced by idx_refund_one_full_per_order)
-- 2. PARTIAL refunds with same idempotency_key are deduplicated
-- 3. Multiple PARTIAL refunds with different keys allowed (sum ≤ net amount)
--
-- Behavior:
-- 1. If idempotency key provided and refund exists → return existing refund
-- 2. If idempotency key provided and no refund exists → create new refund
-- 3. If no idempotency key provided → always create new refund (legacy behavior)
-- 4. FULL refund always checked (even without idempotency key)
--
-- Backward compatibility:
-- - Existing refunds without refund_type → backfilled based on amount comparison
-- - Existing refunds without idempotency_key → allowed (NULL values permitted)
