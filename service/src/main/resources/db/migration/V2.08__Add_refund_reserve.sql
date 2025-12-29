-- V2.08: Add refund reserve support
-- Purpose: Enable physical reservation of funds for potential refunds

-- =====================================================================
-- 1. Create refund_reserve table
-- =====================================================================
-- Tracks reserved funds that are held for potential refunds.
-- Reserve is implemented as HOLD transactions on a reserve wallet.

CREATE TABLE refund_reserve (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL,
    merchant_id BIGINT NOT NULL,
    merchant_wallet_id INTEGER NOT NULL,
    reserved_amount BIGINT NOT NULL,
    used_amount BIGINT NOT NULL DEFAULT 0,
    available_amount BIGINT NOT NULL,
    reserve_transaction_group_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',

    CONSTRAINT fk_refund_reserve_settlement
        FOREIGN KEY (settlement_id)
        REFERENCES settlement(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_refund_reserve_merchant_wallet
        FOREIGN KEY (merchant_wallet_id)
        REFERENCES wallet(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_refund_reserve_transaction_group
        FOREIGN KEY (reserve_transaction_group_id)
        REFERENCES transaction_group(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_refund_reserve_status
        CHECK (status IN ('ACTIVE', 'PARTIALLY_USED', 'FULLY_USED', 'RELEASED')),

    CONSTRAINT chk_refund_reserve_amounts
        CHECK (
            reserved_amount >= 0
            AND used_amount >= 0
            AND available_amount >= 0
            AND used_amount <= reserved_amount
            AND available_amount = reserved_amount - used_amount
        ),

    CONSTRAINT chk_refund_reserve_released_at
        CHECK (
            (status = 'RELEASED' AND released_at IS NOT NULL)
            OR (status != 'RELEASED')
        )
);

COMMENT ON TABLE refund_reserve IS
    'Refund reserve records. Tracks funds physically reserved (HOLD) for potential refunds.';

COMMENT ON COLUMN refund_reserve.settlement_id IS
    'ID of the settlement that created this reserve.';

COMMENT ON COLUMN refund_reserve.merchant_id IS
    'ID of the merchant whose funds are reserved.';

COMMENT ON COLUMN refund_reserve.reserved_amount IS
    'Total reserved amount in cents (percentage of settlement net amount).';

COMMENT ON COLUMN refund_reserve.used_amount IS
    'Amount already consumed by refunds in cents.';

COMMENT ON COLUMN refund_reserve.available_amount IS
    'Remaining available amount for refunds (reserved - used).';

COMMENT ON COLUMN refund_reserve.reserve_transaction_group_id IS
    'Transaction group ID for reserve HOLD transactions (ESCROW → RESERVE_WALLET).';

COMMENT ON COLUMN refund_reserve.status IS
    'Current status: ACTIVE, PARTIALLY_USED, FULLY_USED, or RELEASED.';

COMMENT ON COLUMN refund_reserve.expires_at IS
    'Expiration timestamp. After this, reserve should be released to merchant.';

COMMENT ON COLUMN refund_reserve.currency IS
    'Currency of reserved amount (ISO 4217 code, e.g., USD, EUR, RUB).';

-- Create indexes for efficient queries
CREATE INDEX idx_refund_reserve_settlement_id
    ON refund_reserve(settlement_id);

CREATE INDEX idx_refund_reserve_merchant_id
    ON refund_reserve(merchant_id);

CREATE INDEX idx_refund_reserve_status
    ON refund_reserve(status);

CREATE INDEX idx_refund_reserve_expires_at
    ON refund_reserve(expires_at)
    WHERE status IN ('ACTIVE', 'PARTIALLY_USED');

-- =====================================================================
-- 2. Add configuration for refund reserve
-- =====================================================================
-- Note: These are stored in application.yml, not in database
-- settlement:
--   refund-reserve-rate: 0.10      # 10% of net amount
--   refund-reserve-hold-days: 30   # hold for 30 days
