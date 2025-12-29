package com.nosota.mwallet.api.request;

import com.nosota.mwallet.api.model.RefundInitiator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for creating a refund.
 *
 * <p>Used when initiating a refund operation for a settled order.
 *
 * @param transactionGroupId  ID of the order (transaction group) to refund
 * @param amount              Refund amount in cents (must be positive)
 * @param reason              Reason for refund (max 500 characters)
 * @param initiator           Who is initiating this refund (SYSTEM, MERCHANT, ADMIN)
 */
public record RefundRequest(
        @NotNull(message = "Transaction group ID is required")
        UUID transactionGroupId,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        Long amount,

        @NotNull(message = "Reason is required")
        @Size(min = 1, max = 500, message = "Reason must be between 1 and 500 characters")
        String reason,

        @NotNull(message = "Initiator is required")
        RefundInitiator initiator
) {
}
