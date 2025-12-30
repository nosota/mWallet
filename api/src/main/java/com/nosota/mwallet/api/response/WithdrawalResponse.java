package com.nosota.mwallet.api.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response for withdrawal operation.
 *
 * @param referenceId        Transaction group UUID
 * @param walletId           Source wallet ID from which funds were withdrawn
 * @param amount             Withdrawn amount (in cents/minor units)
 * @param destinationAccount Destination account reference
 * @param status             Operation status (typically "COMPLETED" for withdrawals)
 * @param timestamp          Operation timestamp
 */
public record WithdrawalResponse(
        UUID referenceId,
        Integer walletId,
        Long amount,
        String destinationAccount,
        String status,
        LocalDateTime timestamp
) {
}
