-- V2.10: Add idempotency support to settlement table
-- Purpose: Prevent duplicate settlement execution via idempotency keys

-- =====================================================================
-- 1. Add idempotency_key column to settlement table
-- =====================================================================
ALTER TABLE settlement
    ADD COLUMN idempotency_key VARCHAR(255);

COMMENT ON COLUMN settlement.idempotency_key IS 'Idempotency key for preventing duplicate settlements. Format: merchant_{merchantId}_settlement_{date}. If provided, prevents duplicate settlement execution.';

-- =====================================================================
-- 2. Create partial unique index (allows NULLs for backward compatibility)
-- =====================================================================
-- This index ensures uniqueness only for non-NULL values
-- Settlements without idempotency_key can still be created (legacy support)
CREATE UNIQUE INDEX idx_settlement_idempotency_key
    ON settlement(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON INDEX idx_settlement_idempotency_key IS 'Ensures idempotency key uniqueness. Partial index allows NULL values (backward compatibility).';

-- =====================================================================
-- 3. Create index for idempotency key lookups
-- =====================================================================
CREATE INDEX idx_settlement_idempotency_key_lookup
    ON settlement(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- =====================================================================
-- 4. Notes on Settlement idempotency implementation
-- =====================================================================
-- IMPORTANT: Idempotency key format and usage rules:
--
-- Key format:
-- - "merchant_{merchantId}_settlement_{date}"
-- - Example: "merchant_123_settlement_2024-03-15"
--
-- Behavior:
-- 1. If idempotency key provided and settlement exists → return existing settlement
-- 2. If idempotency key provided and no settlement exists → create new settlement
-- 3. If no idempotency key provided → always create new settlement (legacy behavior)
--
-- Usage:
-- - Prevents duplicate settlements when same request is retried
-- - Allows safe retry of settlement operations
-- - Backward compatible: existing API calls without key still work
