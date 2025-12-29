-- V2.06: Add refund support
-- Purpose: Add tables and transaction status for refund operations
-- Refunds return funds to buyers AFTER settlement (different from RELEASE/CANCEL which happen BEFORE settlement)

-- =====================================================================
-- 1. Add buyer_id to transaction_group
-- =====================================================================
-- This allows identifying the buyer for refund operations

ALTER TABLE transaction_group
    ADD COLUMN buyer_id BIGINT;

COMMENT ON COLUMN transaction_group.buyer_id IS
    'ID of the buyer associated with this transaction group. Used for refund operations.';

CREATE INDEX idx_transaction_group_buyer_id
    ON transaction_group(buyer_id);

-- =====================================================================
-- 2. Add REFUNDED status to transaction status constraint
-- =====================================================================
-- Refund transactions transfer funds from MERCHANT to BUYER after settlement

-- Drop existing constraints
ALTER TABLE transaction
    DROP CONSTRAINT IF EXISTS chk_transaction_status;

ALTER TABLE transaction_snapshot
    DROP CONSTRAINT IF EXISTS chk_transaction_snapshot_status;

ALTER TABLE transaction_snapshot_archive
    DROP CONSTRAINT IF EXISTS chk_transaction_snapshot_archive_status;

-- Recreate constraints with REFUNDED status
ALTER TABLE transaction
    ADD CONSTRAINT chk_transaction_status
        CHECK (status IN ('HOLD', 'SETTLED', 'RELEASED', 'CANCELLED', 'REFUNDED'));

ALTER TABLE transaction_snapshot
    ADD CONSTRAINT chk_transaction_snapshot_status
        CHECK (status IN ('HOLD', 'SETTLED', 'RELEASED', 'CANCELLED', 'REFUNDED'));

ALTER TABLE transaction_snapshot_archive
    ADD CONSTRAINT chk_transaction_snapshot_archive_status
        CHECK (status IN ('HOLD', 'SETTLED', 'RELEASED', 'CANCELLED', 'REFUNDED'));

-- Update comments to include REFUNDED status
COMMENT ON COLUMN transaction.status IS 'Transaction status: HOLD (blocked funds), SETTLED (final execution), RELEASED (returned after dispute), CANCELLED (cancelled before conditions met), REFUNDED (returned to buyer after settlement)';
COMMENT ON COLUMN transaction_snapshot.status IS 'Snapshot transaction status: HOLD (blocked), SETTLED (final), RELEASED (returned), CANCELLED (aborted), REFUNDED (post-settlement return)';

-- =====================================================================
-- 3. Create refund table
-- =====================================================================
-- Tracks refund operations (returns AFTER settlement)

CREATE TABLE refund (
    id UUID PRIMARY KEY,
    transaction_group_id UUID NOT NULL,
    settlement_id UUID,
    merchant_id BIGINT NOT NULL,
    merchant_wallet_id INTEGER NOT NULL,
    buyer_id BIGINT NOT NULL,
    buyer_wallet_id INTEGER NOT NULL,
    amount BIGINT NOT NULL,
    original_amount BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    initiator VARCHAR(20) NOT NULL,
    refund_transaction_group_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    updated_at TIMESTAMP,
    expires_at TIMESTAMP,
    notes VARCHAR(1000),

    CONSTRAINT fk_refund_transaction_group_id
        FOREIGN KEY (transaction_group_id)
        REFERENCES transaction_group(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_refund_settlement_id
        FOREIGN KEY (settlement_id)
        REFERENCES settlement(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_refund_merchant_wallet_id
        FOREIGN KEY (merchant_wallet_id)
        REFERENCES wallet(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_refund_buyer_wallet_id
        FOREIGN KEY (buyer_wallet_id)
        REFERENCES wallet(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_refund_status
        CHECK (status IN ('PENDING', 'PENDING_FUNDS', 'PROCESSING', 'COMPLETED', 'REJECTED', 'FAILED', 'EXPIRED')),

    CONSTRAINT chk_refund_initiator
        CHECK (initiator IN ('SYSTEM', 'MERCHANT', 'ADMIN')),

    CONSTRAINT chk_refund_amount
        CHECK (
            amount > 0
            AND original_amount > 0
            AND amount <= original_amount
        ),

    CONSTRAINT chk_refund_processed_at
        CHECK (
            (status = 'COMPLETED' AND processed_at IS NOT NULL)
            OR (status != 'COMPLETED' AND (processed_at IS NULL OR status = 'FAILED'))
        ),

    CONSTRAINT chk_refund_expires_at
        CHECK (
            (status = 'PENDING_FUNDS' AND expires_at IS NOT NULL)
            OR (status != 'PENDING_FUNDS')
        )
);

COMMENT ON TABLE refund IS
    'Refund records. Tracks returns of funds to buyers AFTER settlement (when merchant already received funds).';

COMMENT ON COLUMN refund.id IS
    'Unique refund identifier (UUID).';

COMMENT ON COLUMN refund.transaction_group_id IS
    'ID of the order (transaction group) being refunded.';

COMMENT ON COLUMN refund.settlement_id IS
    'ID of the settlement that paid out this order.';

COMMENT ON COLUMN refund.merchant_id IS
    'ID of the merchant returning funds.';

COMMENT ON COLUMN refund.merchant_wallet_id IS
    'Wallet ID of the merchant (funds source).';

COMMENT ON COLUMN refund.buyer_id IS
    'ID of the buyer receiving refund.';

COMMENT ON COLUMN refund.buyer_wallet_id IS
    'Wallet ID of the buyer (funds destination).';

COMMENT ON COLUMN refund.amount IS
    'Refund amount in cents (can be partial).';

COMMENT ON COLUMN refund.original_amount IS
    'Original order amount in cents (for validation).';

COMMENT ON COLUMN refund.reason IS
    'Reason for refund (e.g., "defective product", "customer cancellation").';

COMMENT ON COLUMN refund.status IS
    'Current status: PENDING (requested), PENDING_FUNDS (approved but insufficient balance), PROCESSING (executing), COMPLETED (done), REJECTED (denied), FAILED (error), EXPIRED (timeout).';

COMMENT ON COLUMN refund.initiator IS
    'Who initiated this refund: SYSTEM (automatic), MERCHANT (via API), ADMIN (manual intervention).';

COMMENT ON COLUMN refund.refund_transaction_group_id IS
    'Transaction group ID for refund ledger entries (MERCHANT → BUYER).';

COMMENT ON COLUMN refund.created_at IS
    'Timestamp when refund was created/requested.';

COMMENT ON COLUMN refund.processed_at IS
    'Timestamp when refund was successfully completed.';

COMMENT ON COLUMN refund.updated_at IS
    'Timestamp of last status update.';

COMMENT ON COLUMN refund.expires_at IS
    'Expiration timestamp for PENDING_FUNDS status (null if not applicable).';

COMMENT ON COLUMN refund.notes IS
    'Additional notes, error messages, or admin comments.';

-- =====================================================================
-- 4. Create indexes for performance
-- =====================================================================

CREATE INDEX idx_refund_transaction_group_id
    ON refund(transaction_group_id);

CREATE INDEX idx_refund_settlement_id
    ON refund(settlement_id);

CREATE INDEX idx_refund_merchant_id
    ON refund(merchant_id);

CREATE INDEX idx_refund_buyer_id
    ON refund(buyer_id);

CREATE INDEX idx_refund_status
    ON refund(status);

CREATE INDEX idx_refund_initiator
    ON refund(initiator);

CREATE INDEX idx_refund_created_at
    ON refund(created_at DESC);

CREATE INDEX idx_refund_processed_at
    ON refund(processed_at DESC) WHERE processed_at IS NOT NULL;

CREATE INDEX idx_refund_expires_at
    ON refund(expires_at) WHERE expires_at IS NOT NULL;

-- Composite index for common query: find pending funds refunds that can be processed
CREATE INDEX idx_refund_status_expires_at
    ON refund(status, expires_at) WHERE status = 'PENDING_FUNDS';
