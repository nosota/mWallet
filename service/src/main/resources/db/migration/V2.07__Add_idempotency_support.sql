-- V2.07: Add idempotency support for transaction groups
-- Purpose: Enable duplicate detection and prevention for transaction group creation

-- =====================================================================
-- 1. Add idempotency_key column to transaction_group
-- =====================================================================
-- This allows clients to provide an idempotency key to prevent duplicate
-- transaction groups from being created in case of retries

ALTER TABLE transaction_group
    ADD COLUMN idempotency_key VARCHAR(255);

COMMENT ON COLUMN transaction_group.idempotency_key IS 'Idempotency key provided by the client for duplicate detection. If a request is retried with the same key, the existing transaction group is returned instead of creating a new one. Unique constraint prevents duplicates.';

-- Create unique index for idempotency key
CREATE UNIQUE INDEX idx_transaction_group_idempotency_key
    ON transaction_group(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Note: Using partial unique index (WHERE idempotency_key IS NOT NULL)
-- allows multiple NULL values (backward compatibility with existing groups)
-- while enforcing uniqueness for non-NULL keys
