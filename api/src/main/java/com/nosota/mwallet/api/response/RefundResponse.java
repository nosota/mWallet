package com.nosota.mwallet.api.response;

import com.nosota.mwallet.api.model.RefundInitiator;
import com.nosota.mwallet.api.model.RefundStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for refund operations.
 *
 * <p>Contains complete information about a refund, including amounts,
 * status, and related entities. Returned by refund API endpoints.
 *
 * @param id                         Refund UUID
 * @param transactionGroupId         Original order (transaction group) being refunded
 * @param settlementId               Settlement that paid out this order
 * @param merchantId                 ID of the merchant
 * @param merchantWalletId           Wallet ID of the merchant
 * @param buyerId                    ID of the buyer
 * @param buyerWalletId              Wallet ID of the buyer
 * @param amount                     Refund amount (in cents)
 * @param originalAmount             Original order amount before commission (in cents)
 * @param reason                     Reason for refund
 * @param status                     Current status (PENDING, PENDING_FUNDS, PROCESSING, COMPLETED, etc.)
 * @param initiator                  Who initiated this refund (SYSTEM, MERCHANT, ADMIN)
 * @param refundTransactionGroupId   Transaction group ID for refund ledger entries
 * @param createdAt                  Timestamp when refund was created
 * @param processedAt                Timestamp when refund was completed (null if not completed)
 * @param updatedAt                  Timestamp of last update
 * @param expiresAt                  Expiration timestamp for PENDING_FUNDS status
 * @param notes                      Additional notes or error messages
 */
public record RefundResponse(
        UUID id,
        UUID transactionGroupId,
        UUID settlementId,
        Long merchantId,
        Integer merchantWalletId,
        Long buyerId,
        Integer buyerWalletId,
        Long amount,
        Long originalAmount,
        String reason,
        RefundStatus status,
        RefundInitiator initiator,
        UUID refundTransactionGroupId,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        LocalDateTime updatedAt,
        LocalDateTime expiresAt,
        String notes
) {
}
