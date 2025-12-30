package com.nosota.mwallet.api.response;

/**
 * Response containing wallet information.
 *
 * @param walletId   Unique identifier of the wallet
 * @param type       Type of the wallet (USER, MERCHANT, ESCROW, SYSTEM)
 * @param ownerId    ID of the owner (null for system wallets)
 * @param ownerType  Type of the owner (USER_OWNER, MERCHANT_OWNER, SYSTEM_OWNER)
 * @param description Optional description
 * @param currency   Currency code (ISO 4217: USD, EUR, etc.)
 */
public record WalletResponse(
        Integer walletId,
        String type,
        Long ownerId,
        String ownerType,
        String description,
        String currency
) {
}
