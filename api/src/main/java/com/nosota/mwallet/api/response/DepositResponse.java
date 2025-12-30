package com.nosota.mwallet.api.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response for deposit operation.
 *
 * @param referenceId       Transaction group UUID
 * @param walletId          Target wallet ID that received the deposit
 * @param amount            Deposited amount (in cents/minor units)
 * @param externalReference External reference ID from source system
 * @param status            Operation status (typically "COMPLETED" for deposits)
 * @param timestamp         Operation timestamp
 */
public record DepositResponse(
        UUID referenceId,
        Integer walletId,
        Long amount,
        String externalReference,
        String status,
        LocalDateTime timestamp
) {
}
