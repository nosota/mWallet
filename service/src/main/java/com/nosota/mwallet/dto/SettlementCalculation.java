package com.nosota.mwallet.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Internal DTO for settlement calculation results.
 *
 * <p>Contains all the data needed to execute a settlement operation,
 * including transaction groups to settle, amounts, and fees.
 *
 * <p>This is an internal DTO used within the service layer.
 * For API responses, use {@link com.nosota.mwallet.api.response.SettlementResponse}.
 *
 * @param merchantId          ID of the merchant
 * @param transactionGroups   List of transaction group IDs to include
 * @param totalAmount         Total amount before fees (in cents)
 * @param feeAmount           Platform commission fee (in cents)
 * @param netAmount           Net amount to transfer to merchant (in cents)
 * @param commissionRate      Commission rate applied (e.g., 0.03 for 3%)
 * @param groupCount          Number of transaction groups
 */
@Builder
public record SettlementCalculation(
        Long merchantId,
        List<UUID> transactionGroups,
        Long totalAmount,
        Long feeAmount,
        Long netAmount,
        BigDecimal commissionRate,
        Integer groupCount
) {
}
