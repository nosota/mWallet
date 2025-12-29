-- V2.09: Add multi-currency support
-- Purpose: Enable multiple currencies (USD, EUR, RUB) without exchange

-- =====================================================================
-- 1. Add currency column to wallet table
-- =====================================================================
ALTER TABLE wallet
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD';

COMMENT ON COLUMN wallet.currency IS 'Currency of the wallet (ISO 4217 code: USD, EUR, RUB, etc.). All transactions on this wallet must use the same currency. Transfers between wallets with different currencies are forbidden.';

CREATE INDEX idx_wallet_currency
    ON wallet(currency);

-- =====================================================================
-- 2. Add currency column to transaction table
-- =====================================================================
ALTER TABLE transaction
    ADD COLUMN currency VARCHAR(3);

-- Backfill existing transactions with USD (default currency)
UPDATE transaction
SET currency = 'USD'
WHERE currency IS NULL;

-- Make column NOT NULL after backfill
ALTER TABLE transaction
    ALTER COLUMN currency SET NOT NULL;

COMMENT ON COLUMN transaction.currency IS 'Currency of the transaction (ISO 4217 code). Must match the wallet currency. Inherited from wallet at creation time.';

CREATE INDEX idx_transaction_currency
    ON transaction(currency);

-- =====================================================================
-- 3. Add currency column to transaction_snapshot table
-- =====================================================================
ALTER TABLE transaction_snapshot
    ADD COLUMN currency VARCHAR(3);

-- Backfill existing snapshots with USD
UPDATE transaction_snapshot
SET currency = 'USD'
WHERE currency IS NULL;

-- Make column NOT NULL after backfill
ALTER TABLE transaction_snapshot
    ALTER COLUMN currency SET NOT NULL;

COMMENT ON COLUMN transaction_snapshot.currency IS
    'Currency of the snapshot transaction (ISO 4217 code).';

CREATE INDEX idx_transaction_snapshot_currency
    ON transaction_snapshot(currency);

-- =====================================================================
-- 4. Add currency column to settlement table
-- =====================================================================
ALTER TABLE settlement
    ADD COLUMN currency VARCHAR(3);

-- Backfill existing settlements with USD
UPDATE settlement
SET currency = 'USD'
WHERE currency IS NULL;

-- Make column NOT NULL after backfill
ALTER TABLE settlement
    ALTER COLUMN currency SET NOT NULL;

COMMENT ON COLUMN settlement.currency IS 'Currency of the settlement (ISO 4217 code). All transaction groups in this settlement must use the same currency.';

CREATE INDEX idx_settlement_currency
    ON settlement(currency);

-- =====================================================================
-- 5. Add currency column to refund table
-- =====================================================================
ALTER TABLE refund
    ADD COLUMN currency VARCHAR(3);

-- Backfill existing refunds with USD
UPDATE refund
SET currency = 'USD'
WHERE currency IS NULL;

-- Make column NOT NULL after backfill
ALTER TABLE refund
    ALTER COLUMN currency SET NOT NULL;

COMMENT ON COLUMN refund.currency IS 'Currency of the refund (ISO 4217 code). Must match the original transaction currency.';

CREATE INDEX idx_refund_currency
    ON refund(currency);

-- =====================================================================
-- 6. Notes on multi-currency implementation
-- =====================================================================
-- IMPORTANT: This migration adds currency support but does NOT enable currency exchange.
--
-- Rules:
-- 1. Each wallet has a fixed currency (cannot be changed)
-- 2. Transactions can only happen between wallets with the same currency
-- 3. Settlement groups transactions by currency (one settlement per currency)
-- 4. Refunds must use the same currency as the original transaction
--
-- To transfer between currencies, users must:
-- 1. Withdraw from wallet A (currency X)
-- 2. Use external exchange service
-- 3. Deposit to wallet B (currency Y)
--
-- This approach is intentional - currency exchange requires:
-- - Real-time exchange rates
-- - Regulatory compliance (forex licenses)
-- - Complex pricing and fee structures
-- - Risk management for rate fluctuations
