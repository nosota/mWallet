package com.nosota.mwallet.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request for creating a USER wallet.
 *
 * <p>USER wallets are owned by individual users and must have a non-null ownerId.
 */
public record CreateUserWalletRequest(
        @NotNull(message = "Owner ID is required for USER wallets")
        Long ownerId,

        String description,

        String currency,

        @PositiveOrZero(message = "Initial balance must be non-negative")
        Long initialBalance
) {
}
