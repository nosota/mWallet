package com.nosota.mwallet.api.response;

import com.nosota.mwallet.api.model.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for settlement operations.
 *
 * <p>Contains complete information about a settlement, including amounts,
 * fees, and status. Returned by settlement API endpoints.
 *
 * @param id                             Settlement UUID
 * @param merchantId                     ID of the merchant
 * @param totalAmount                    Total amount before fees (in cents)
 * @param feeAmount                      Platform commission fee (in cents)
 * @param netAmount                      Net amount transferred to merchant (in cents)
 * @param commissionRate                 Commission rate applied (e.g., 0.03 for 3%)
 * @param groupCount                     Number of transaction groups included
 * @param status                         Current status (PENDING, COMPLETED, FAILED)
 * @param createdAt                      Timestamp when settlement was created
 * @param settledAt                      Timestamp when settlement was completed (null if not completed)
 * @param settlementTransactionGroupId   Transaction group ID for settlement ledger entries
 */
public record SettlementResponse(
        UUID id,
        Long merchantId,
        Long totalAmount,
        Long feeAmount,
        Long netAmount,
        BigDecimal commissionRate,
        Integer groupCount,
        SettlementStatus status,
        LocalDateTime createdAt,
        LocalDateTime settledAt,
        UUID settlementTransactionGroupId
) {
}
