package com.nosota.mwallet.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request for withdrawing funds from a wallet to external destination.
 *
 * <p>Withdrawal operation represents money leaving the system to external world
 * (banks, payment processors, cash). It creates proper double-entry bookkeeping:
 * <ul>
 *   <li>DEBIT from source wallet (loses the funds)</li>
 *   <li>CREDIT to WITHDRAWAL system wallet (goes positive - destination tracking)</li>
 * </ul>
 *
 * @param walletId           Source wallet ID to withdraw from
 * @param amount             Amount to withdraw (in cents/minor units)
 * @param destinationAccount Destination account (e.g., IBAN, card number, account reference)
 */
public record WithdrawalRequest(
        @NotNull(message = "Wallet ID is required")
        Integer walletId,

        @Positive(message = "Amount must be positive")
        Long amount,

        String destinationAccount
) {
}
