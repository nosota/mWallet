package com.nosota.mwallet.api;

import com.nosota.mwallet.api.dto.PagedResponse;
import com.nosota.mwallet.api.dto.RefundHistoryDTO;
import com.nosota.mwallet.api.dto.SettlementHistoryDTO;
import com.nosota.mwallet.api.request.RefundRequest;
import com.nosota.mwallet.api.response.RefundResponse;
import com.nosota.mwallet.api.response.SettlementResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Payment API interface for high-level payment operations.
 *
 * <p>Defines REST endpoints for:
 * <ul>
 *   <li>Settlement operations (merchant payouts from ESCROW)</li>
 *   <li>Refund operations (returns to buyers after settlement)</li>
 * </ul>
 *
 * <p>This interface is implemented by:
 * <ul>
 *   <li>PaymentController - in service module (server-side implementation)</li>
 *   <li>PaymentClient - in api module (WebClient-based client for consumers)</li>
 * </ul>
 */
@RequestMapping("/api/v1/payment")
public interface PaymentApi {

    // ==================== Settlement Operations ====================

    /**
     * Calculates settlement for a merchant (preview without executing).
     *
     * @param merchantId The merchant ID
     * @return Settlement calculation with amounts and fees
     */
    @GetMapping("/settlement/merchants/{merchantId}/calculate")
    ResponseEntity<SettlementResponse> calculateSettlement(
            @PathVariable("merchantId") Long merchantId);

    /**
     * Executes settlement for a merchant (transfers funds from ESCROW to MERCHANT).
     *
     * @param merchantId The merchant ID
     * @return Completed settlement response
     */
    @PostMapping("/settlement/merchants/{merchantId}/execute")
    ResponseEntity<SettlementResponse> executeSettlement(
            @PathVariable("merchantId") Long merchantId) throws Exception;

    /**
     * Gets a specific settlement by ID.
     *
     * @param settlementId The settlement ID
     * @return Settlement response
     */
    @GetMapping("/settlement/{settlementId}")
    ResponseEntity<SettlementResponse> getSettlement(
            @PathVariable("settlementId") UUID settlementId);

    /**
     * Gets settlement history for a merchant with pagination.
     *
     * @param merchantId The merchant ID
     * @param page       Page number (0-indexed)
     * @param size       Page size
     * @return Paginated list of settlements
     */
    @GetMapping("/settlement/merchants/{merchantId}/history")
    ResponseEntity<PagedResponse<SettlementHistoryDTO>> getSettlementHistory(
            @PathVariable("merchantId") Long merchantId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size);

    // ==================== Refund Operations ====================

    /**
     * Creates and potentially executes a refund.
     *
     * <p>The refund may be executed immediately or placed in PENDING_FUNDS status
     * depending on merchant balance availability.
     *
     * @param request Refund request details (order ID, amount, reason, initiator)
     * @return Created refund response
     */
    @PostMapping("/refund")
    ResponseEntity<RefundResponse> createRefund(
            @RequestBody @Valid RefundRequest request);

    /**
     * Gets a specific refund by ID.
     *
     * @param refundId The refund ID
     * @return Refund response
     */
    @GetMapping("/refund/{refundId}")
    ResponseEntity<RefundResponse> getRefund(
            @PathVariable("refundId") UUID refundId);

    /**
     * Gets refund history for a merchant with pagination.
     *
     * @param merchantId The merchant ID
     * @param page       Page number (0-indexed)
     * @param size       Page size
     * @return Paginated list of refunds
     */
    @GetMapping("/refund/merchants/{merchantId}/history")
    ResponseEntity<PagedResponse<RefundHistoryDTO>> getRefundHistory(
            @PathVariable("merchantId") Long merchantId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size);

    /**
     * Gets all refunds for a specific order (transaction group).
     *
     * @param transactionGroupId The transaction group ID (order ID)
     * @return List of refunds for this order
     */
    @GetMapping("/refund/orders/{transactionGroupId}")
    ResponseEntity<List<RefundResponse>> getRefundsByOrder(
            @PathVariable("transactionGroupId") UUID transactionGroupId);
}
