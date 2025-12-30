-- Add unique constraint on wallet (type, description) to prevent duplicate system wallets
-- This ensures only one DEPOSIT, WITHDRAWAL, ESCROW wallet exists

-- First, remove any duplicate wallets (keep the oldest one)
DELETE FROM wallet w1
WHERE w1.type IN ('SYSTEM', 'ESCROW')
  AND w1.description IS NOT NULL
  AND EXISTS (
    SELECT 1
    FROM wallet w2
    WHERE w2.type = w1.type
      AND w2.description = w1.description
      AND w2.id < w1.id
);

-- Now add the unique index (only for SYSTEM and ESCROW wallets)
-- This allows multiple USER/MERCHANT wallets with same description
-- but prevents duplicate DEPOSIT/WITHDRAWAL/ESCROW system wallets
CREATE UNIQUE INDEX uk_system_wallet_description
    ON wallet (type, description)
    WHERE type IN ('SYSTEM', 'ESCROW');
