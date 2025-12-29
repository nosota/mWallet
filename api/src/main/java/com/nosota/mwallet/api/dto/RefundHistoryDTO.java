package com.nosota.mwallet.api.dto;

import com.nosota.mwallet.api.model.RefundInitiator;
import com.nosota.mwallet.api.model.RefundStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for refund history entries.
 *
 * <p>Simplified version of {@link com.nosota.mwallet.api.response.RefundResponse}
 * used for listing multiple refunds in history queries.
 *
 * @param id                 Refund UUID
 * @param transactionGroupId Original order (transaction group) being refunded
 * @param amount             Refund amount (in cents)
 * @param reason             Reason for refund
 * @param status             Current status
 * @param initiator          Who initiated this refund
 * @param createdAt          Timestamp when refund was created
 * @param processedAt        Timestamp when refund was completed
 */
public record RefundHistoryDTO(
        UUID id,
        UUID transactionGroupId,
        Long amount,
        String reason,
        RefundStatus status,
        RefundInitiator initiator,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
}
