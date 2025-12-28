-- V2.05: Add settlement support
-- Purpose: Add tables and columns for merchant settlement operations

-- =====================================================================
-- 1. Add merchant_id to transaction_group
-- =====================================================================
-- This allows grouping transactions by merchant for settlement purposes

ALTER TABLE transaction_group
    ADD COLUMN merchant_id BIGINT;

COMMENT ON COLUMN transaction_group.merchant_id IS
    'ID of the merchant associated with this transaction group. Used for settlement operations.';

CREATE INDEX idx_transaction_group_merchant_id
    ON transaction_group(merchant_id);

-- =====================================================================
-- 2. Create settlement table
-- =====================================================================
-- Tracks merchant payout operations (transfers from ESCROW to MERCHANT)

CREATE TABLE settlement (
    id UUID PRIMARY KEY,
    merchant_id BIGINT NOT NULL,
    total_amount BIGINT NOT NULL,
    fee_amount BIGINT NOT NULL,
    net_amount BIGINT NOT NULL,
    commission_rate NUMERIC(5, 4) NOT NULL,
    group_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    settled_at TIMESTAMP,
    settlement_transaction_group_id UUID,

    CONSTRAINT chk_settlement_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    CONSTRAINT chk_settlement_amounts
        CHECK (
            total_amount >= 0
            AND fee_amount >= 0
            AND net_amount >= 0
            AND net_amount = total_amount - fee_amount
        ),

    CONSTRAINT chk_settlement_commission_rate
        CHECK (commission_rate >= 0 AND commission_rate <= 1),

    CONSTRAINT chk_settlement_group_count
        CHECK (group_count > 0),

    CONSTRAINT chk_settlement_settled_at
        CHECK (
            (status = 'COMPLETED' AND settled_at IS NOT NULL)
            OR (status != 'COMPLETED' AND settled_at IS NULL)
        )
);

COMMENT ON TABLE settlement IS
    'Merchant settlement records. Tracks payout operations from ESCROW to MERCHANT wallets.';

COMMENT ON COLUMN settlement.merchant_id IS
    'ID of the merchant receiving the settlement.';

COMMENT ON COLUMN settlement.total_amount IS
    'Total amount from all transaction groups before fees (in cents).';

COMMENT ON COLUMN settlement.fee_amount IS
    'Platform commission fee deducted from total amount (in cents).';

COMMENT ON COLUMN settlement.net_amount IS
    'Net amount transferred to merchant after fees (in cents).';

COMMENT ON COLUMN settlement.commission_rate IS
    'Commission rate applied to this settlement (e.g., 0.03 for 3%).';

COMMENT ON COLUMN settlement.group_count IS
    'Number of transaction groups included in this settlement.';

COMMENT ON COLUMN settlement.status IS
    'Current status: PENDING, COMPLETED, or FAILED.';

COMMENT ON COLUMN settlement.settlement_transaction_group_id IS
    'Transaction group ID for the settlement ledger entries (ESCROW → MERCHANT, ESCROW → SYSTEM).';

CREATE INDEX idx_settlement_merchant_id
    ON settlement(merchant_id);

CREATE INDEX idx_settlement_status
    ON settlement(status);

CREATE INDEX idx_settlement_created_at
    ON settlement(created_at);

-- =====================================================================
-- 3. Create settlement_transaction_group table
-- =====================================================================
-- Links settlements to their included transaction groups (M:N relationship)

CREATE TABLE settlement_transaction_group (
    id BIGSERIAL PRIMARY KEY,
    settlement_id UUID NOT NULL,
    transaction_group_id UUID NOT NULL,
    amount BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_settlement_id
        FOREIGN KEY (settlement_id)
        REFERENCES settlement(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_transaction_group_id
        FOREIGN KEY (transaction_group_id)
        REFERENCES transaction_group(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_settlement_tg_amount
        CHECK (amount >= 0),

    -- Prevent same transaction group from being in multiple settlements
    CONSTRAINT uq_transaction_group_id
        UNIQUE (transaction_group_id)
);

COMMENT ON TABLE settlement_transaction_group IS
    'Links settlements to their included transaction groups. Provides audit trail and prevents double settlement.';

COMMENT ON COLUMN settlement_transaction_group.settlement_id IS
    'ID of the settlement.';

COMMENT ON COLUMN settlement_transaction_group.transaction_group_id IS
    'ID of the transaction group included in the settlement.';

COMMENT ON COLUMN settlement_transaction_group.amount IS
    'Total amount from this transaction group (for audit purposes).';

CREATE INDEX idx_settlement_tg_settlement_id
    ON settlement_transaction_group(settlement_id);

CREATE INDEX idx_settlement_tg_transaction_group_id
    ON settlement_transaction_group(transaction_group_id);

CREATE INDEX idx_settlement_tg_created_at
    ON settlement_transaction_group(created_at);
