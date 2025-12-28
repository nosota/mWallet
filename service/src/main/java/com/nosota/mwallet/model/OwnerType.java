package com.nosota.mwallet.model;

/**
 * Defines the type of owner for a wallet.
 * <p>
 * This enum is used to distinguish between different ownership models:
 * - USER_OWNER: Wallet belongs to a regular user (type=USER)
 * - MERCHANT_OWNER: Wallet belongs to a merchant/seller (type=MERCHANT)
 * - SYSTEM_OWNER: Wallet is owned by the system (type=ESCROW or SYSTEM)
 * </p>
 * <p>
 * For SYSTEM_OWNER wallets (ESCROW/SYSTEM), the ownerId field must be null.
 * For USER_OWNER and MERCHANT_OWNER wallets, the ownerId field must contain the owner's ID.
 * </p>
 */
public enum OwnerType {
    /**
     * Wallet belongs to a regular user.
     * Used with WalletType.USER.
     * ownerId must be non-null.
     */
    USER_OWNER,

    /**
     * Wallet belongs to a merchant/seller.
     * Used with WalletType.MERCHANT.
     * ownerId must be non-null.
     */
    MERCHANT_OWNER,

    /**
     * Wallet is owned by the system (no individual owner).
     * Used with WalletType.ESCROW and WalletType.SYSTEM.
     * ownerId must be null.
     */
    SYSTEM_OWNER
}
