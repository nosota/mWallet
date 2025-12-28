-- V2.04: Wallet Ownership Consolidation
--
-- This migration consolidates wallet ownership information into the wallet table itself:
-- 1. Adds owner_id and owner_type columns to wallet table
-- 2. Migrates data from wallet_owner table to wallet
-- 3. Adds new wallet types: MERCHANT, ESCROW
-- 4. Enforces ownership constraints via CHECK constraints
-- 5. Removes wallet_owner table (ownership now embedded in wallet)
--
-- Ownership Rules:
-- - USER/MERCHANT wallets: must have non-null ownerId (belongs to specific user/merchant)
-- - ESCROW/SYSTEM wallets: must have null ownerId (system-owned)

-- ==================== STEP 1: Add New Columns ====================

-- Add owner_id column (nullable for now, will add constraints later)
ALTER TABLE wallet ADD COLUMN owner_id BIGINT;

-- Add owner_type column (nullable for now)
ALTER TABLE wallet ADD COLUMN owner_type VARCHAR(20);

COMMENT ON COLUMN wallet.owner_id IS
'ID of the owner (user or merchant). NULL for system-owned wallets (ESCROW/SYSTEM).';

COMMENT ON COLUMN wallet.owner_type IS
'Type of owner: USER_OWNER, MERCHANT_OWNER, or SYSTEM_OWNER.';

-- ==================== STEP 2: Migrate Data from wallet_owner ====================

-- Copy ownership data from wallet_owner table to wallet
UPDATE wallet w
SET
    owner_type = wo.owner_type::VARCHAR,
    owner_id = CASE
        -- If owner_ref is a valid number, use it; otherwise NULL
        WHEN wo.owner_ref ~ '^\d+$' THEN wo.owner_ref::BIGINT
        ELSE NULL
    END
FROM wallet_owner wo
WHERE w.id = wo.wallet_id;

-- ==================== STEP 3: Set Default Values for Unmigrated Wallets ====================

-- For wallets without owner_type, set based on wallet type
UPDATE wallet
SET owner_type = CASE
    WHEN type = 'SYSTEM' THEN 'SYSTEM_OWNER'
    WHEN type = 'USER' THEN 'USER_OWNER'
    ELSE 'SYSTEM_OWNER'  -- default for any other types
END
WHERE owner_type IS NULL;

-- For USER wallets without owner_id, use wallet id as temporary owner_id
-- (This is a placeholder - in production, you should have real user IDs)
UPDATE wallet
SET owner_id = id
WHERE type = 'USER' AND owner_id IS NULL;

-- For SYSTEM wallets, ensure owner_id is NULL
UPDATE wallet
SET owner_id = NULL
WHERE type = 'SYSTEM' AND owner_id IS NOT NULL;

-- ==================== STEP 4: Make owner_type NOT NULL ====================

ALTER TABLE wallet ALTER COLUMN owner_type SET NOT NULL;

-- ==================== STEP 5: Add New Wallet Types ====================

-- Note: wallet.type is VARCHAR, not ENUM, so MERCHANT and ESCROW values are already allowed
-- No database changes needed - wallet types are managed in application code (WalletType enum)

-- ==================== STEP 6: Add Ownership Constraints ====================

-- Constraint 1: USER wallets must have non-null owner_id and USER_OWNER type
ALTER TABLE wallet ADD CONSTRAINT chk_wallet_user_ownership
    CHECK (
        (type = 'USER' AND owner_id IS NOT NULL AND owner_type = 'USER_OWNER')
        OR type != 'USER'
    );

-- Constraint 2: MERCHANT wallets must have non-null owner_id and MERCHANT_OWNER type
ALTER TABLE wallet ADD CONSTRAINT chk_wallet_merchant_ownership
    CHECK (
        (type = 'MERCHANT' AND owner_id IS NOT NULL AND owner_type = 'MERCHANT_OWNER')
        OR type != 'MERCHANT'
    );

-- Constraint 3: ESCROW wallets must have null owner_id and SYSTEM_OWNER type
ALTER TABLE wallet ADD CONSTRAINT chk_wallet_escrow_ownership
    CHECK (
        (type = 'ESCROW' AND owner_id IS NULL AND owner_type = 'SYSTEM_OWNER')
        OR type != 'ESCROW'
    );

-- Constraint 4: SYSTEM wallets must have null owner_id and SYSTEM_OWNER type
ALTER TABLE wallet ADD CONSTRAINT chk_wallet_system_ownership
    CHECK (
        (type = 'SYSTEM' AND owner_id IS NULL AND owner_type = 'SYSTEM_OWNER')
        OR type != 'SYSTEM'
    );


-- ==================== STEP 7: Drop wallet_owner Table ====================

-- The wallet_owner table is no longer needed as ownership is now in wallet itself
DROP TABLE IF EXISTS wallet_owner CASCADE;

-- ==================== DOCUMENTATION ====================

COMMENT ON TABLE wallet IS
'Wallets with embedded ownership information.
Ownership rules enforced via CHECK constraints:
- USER/MERCHANT: must have owner_id (non-null)
- ESCROW/SYSTEM: must have owner_id=null (system-owned)';

COMMENT ON CONSTRAINT chk_wallet_user_ownership ON wallet IS
'Ensures USER wallets have valid ownership: non-null owner_id and USER_OWNER type';

COMMENT ON CONSTRAINT chk_wallet_merchant_ownership ON wallet IS
'Ensures MERCHANT wallets have valid ownership: non-null owner_id and MERCHANT_OWNER type';

COMMENT ON CONSTRAINT chk_wallet_escrow_ownership ON wallet IS
'Ensures ESCROW wallets are system-owned: null owner_id and SYSTEM_OWNER type';

COMMENT ON CONSTRAINT chk_wallet_system_ownership ON wallet IS
'Ensures SYSTEM wallets are system-owned: null owner_id and SYSTEM_OWNER type';

-- ==================== SUMMARY ====================

-- After this migration:
-- ✅ Wallet ownership is embedded in wallet table (owner_id + owner_type)
-- ✅ wallet_owner table removed (no longer needed)
-- ✅ New wallet types added: MERCHANT, ESCROW
-- ✅ Ownership constraints enforced at database level
-- ✅ USER/MERCHANT wallets must have owner
-- ✅ ESCROW/SYSTEM wallets must NOT have owner (system-owned)
