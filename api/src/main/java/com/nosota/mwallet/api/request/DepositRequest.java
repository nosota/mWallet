package com.nosota.mwallet.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request for depositing funds to a wallet from external source.
 *
 * <p>Deposit operation represents money entering the system from external world
 * (banks, payment processors, cash). It creates proper double-entry bookkeeping:
 * <ul>
 *   <li>DEBIT from DEPOSIT system wallet (goes negative - source of funds)</li>
 *   <li>CREDIT to target wallet (receives the funds)</li>
 * </ul>
 *
 * @param walletId          Target wallet ID to deposit to
 * @param amount            Amount to deposit (in cents/minor units)
 * @param externalReference External reference ID (e.g., bank transaction ID, payment processor reference)
 */
public record DepositRequest(
        @NotNull(message = "Wallet ID is required")
        Integer walletId,

        @Positive(message = "Amount must be positive")
        Long amount,

        String externalReference
) {
}
