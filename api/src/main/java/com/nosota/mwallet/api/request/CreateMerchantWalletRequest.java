package com.nosota.mwallet.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request for creating a MERCHANT wallet.
 *
 * <p>MERCHANT wallets are owned by merchants and must have a non-null ownerId.
 */
public record CreateMerchantWalletRequest(
        @NotNull(message = "Owner ID is required for MERCHANT wallets")
        Long ownerId,

        String description,

        String currency,

        @PositiveOrZero(message = "Initial balance must be non-negative")
        Long initialBalance
) {
}
