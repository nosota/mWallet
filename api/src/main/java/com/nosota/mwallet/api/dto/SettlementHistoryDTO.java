package com.nosota.mwallet.api.dto;

import com.nosota.mwallet.api.model.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for settlement history entries.
 *
 * <p>Simplified version of {@link com.nosota.mwallet.api.response.SettlementResponse}
 * used for listing multiple settlements in history queries.
 *
 * @param id             Settlement UUID
 * @param merchantId     ID of the merchant
 * @param netAmount      Net amount transferred to merchant (in cents)
 * @param groupCount     Number of transaction groups included
 * @param status         Current status
 * @param settledAt      Timestamp when settlement was completed
 */
public record SettlementHistoryDTO(
        UUID id,
        Long merchantId,
        Long netAmount,
        BigDecimal commissionRate,
        Integer groupCount,
        SettlementStatus status,
        LocalDateTime settledAt
) {
}
