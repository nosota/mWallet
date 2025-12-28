package com.nosota.mwallet.model;

/**
 * Defines the type of wallet in the system.
 * <p>
 * Wallet types determine ownership rules and usage restrictions:
 * - USER: Wallets for regular users (must have ownerId + ownerType=USER_OWNER)
 * - MERCHANT: Wallets for merchants/sellers (must have ownerId + ownerType=MERCHANT_OWNER)
 * - ESCROW: Temporary holding account for transactions (ownerId must be null, ownerType=SYSTEM_OWNER)
 * - SYSTEM: Technical accounts for fees and system operations (ownerId must be null, ownerType=SYSTEM_OWNER)
 * </p>
 * <p>
 * ESCROW and SYSTEM wallets can only be created by the system (internal API).
 * </p>
 */
public enum WalletType {
    /**
     * Wallet for a regular user.
     * Must have non-null ownerId and ownerType=USER_OWNER.
     */
    USER,

    /**
     * Wallet for a merchant/seller.
     * Must have non-null ownerId and ownerType=MERCHANT_OWNER.
     */
    MERCHANT,

    /**
     * Temporary holding account for escrow transactions.
     * Must have ownerId=null and ownerType=SYSTEM_OWNER.
     * Can only be created by internal system API.
     */
    ESCROW,

    /**
     * System wallet for fees and technical operations.
     * Must have ownerId=null and ownerType=SYSTEM_OWNER.
     * Can only be created by internal system API.
     */
    SYSTEM
}