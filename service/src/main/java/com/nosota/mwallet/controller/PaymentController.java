package com.nosota.mwallet.controller;

import com.nosota.mwallet.api.PaymentApi;
import com.nosota.mwallet.api.dto.PagedResponse;
import com.nosota.mwallet.api.dto.RefundHistoryDTO;
import com.nosota.mwallet.api.dto.SettlementHistoryDTO;
import com.nosota.mwallet.api.request.DepositRequest;
import com.nosota.mwallet.api.request.RefundRequest;
import com.nosota.mwallet.api.request.WithdrawalRequest;
import com.nosota.mwallet.api.response.DepositResponse;
import com.nosota.mwallet.api.response.RefundResponse;
import com.nosota.mwallet.api.response.SettlementResponse;
import com.nosota.mwallet.api.response.WithdrawalResponse;
import com.nosota.mwallet.dto.SettlementCalculation;
import com.nosota.mwallet.model.Refund;
import com.nosota.mwallet.model.Settlement;
import com.nosota.mwallet.service.DepositService;
import com.nosota.mwallet.service.RefundService;
import com.nosota.mwallet.service.SettlementService;
import com.nosota.mwallet.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for high-level payment operations.
 *
 * <p>Implements {@link PaymentApi} interface for:
 * <ul>
 *   <li>Settlement operations (merchant payouts)</li>
 *   <li>Refund operations (returns to buyers)</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
@Slf4j
public class PaymentController implements PaymentApi {

    private final SettlementService settlementService;
    private final RefundService refundService;
    private final DepositService depositService;
    private final WithdrawalService withdrawalService;

    // ==================== Settlement Operations ====================

    @Override
    public ResponseEntity<SettlementResponse> calculateSettlement(Long merchantId) {
        SettlementCalculation calculation = settlementService.calculateSettlement(merchantId);
        SettlementResponse response = new SettlementResponse(
                null,  // no ID yet (not saved)
                calculation.merchantId(),
                calculation.totalAmount(),
                calculation.feeAmount(),
                calculation.netAmount(),
                calculation.commissionRate(),
                calculation.groupCount(),
                null,  // no status yet
                null,  // no createdAt yet
                null,  // no settledAt yet
                null   // no settlementTransactionGroupId yet
        );
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<SettlementResponse> executeSettlement(Long merchantId) throws Exception {
        Settlement settlement = settlementService.executeSettlement(merchantId);
        SettlementResponse response = toSettlementResponse(settlement);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<SettlementResponse> getSettlement(UUID settlementId) {
        Settlement settlement = settlementService.getSettlement(settlementId);
        SettlementResponse response = toSettlementResponse(settlement);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<SettlementHistoryDTO>> getSettlementHistory(
            Long merchantId, int page, int size) {
        Page<Settlement> settlements = settlementService.getSettlementHistory(
                merchantId, PageRequest.of(page, size));

        List<SettlementHistoryDTO> content = settlements.getContent().stream()
                .map(this::toSettlementHistoryDTO)
                .toList();

        PagedResponse<SettlementHistoryDTO> response = new PagedResponse<>(
                content,
                settlements.getNumber(),
                settlements.getSize(),
                (int) settlements.getTotalElements()
        );

        return ResponseEntity.ok(response);
    }

    // ==================== Refund Operations ====================

    @Override
    public ResponseEntity<RefundResponse> createRefund(RefundRequest request) {
        Refund refund = refundService.createRefund(request);
        RefundResponse response = toRefundResponse(refund);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<RefundResponse> getRefund(UUID refundId) {
        Refund refund = refundService.getRefund(refundId);
        RefundResponse response = toRefundResponse(refund);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<RefundHistoryDTO>> getRefundHistory(
            Long merchantId, int page, int size) {
        Page<Refund> refunds = refundService.getRefundHistory(
                merchantId, PageRequest.of(page, size));

        List<RefundHistoryDTO> content = refunds.getContent().stream()
                .map(this::toRefundHistoryDTO)
                .toList();

        PagedResponse<RefundHistoryDTO> response = new PagedResponse<>(
                content,
                refunds.getNumber(),
                refunds.getSize(),
                (int) refunds.getTotalElements()
        );

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<RefundResponse>> getRefundsByOrder(UUID transactionGroupId) {
        List<Refund> refunds = refundService.getRefundsByOrder(transactionGroupId);
        List<RefundResponse> responses = refunds.stream()
                .map(this::toRefundResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    // ==================== Private Helper Methods ====================

    private SettlementResponse toSettlementResponse(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getMerchantId(),
                settlement.getTotalAmount(),
                settlement.getFeeAmount(),
                settlement.getNetAmount(),
                settlement.getCommissionRate(),
                settlement.getGroupCount(),
                settlement.getStatus(),
                settlement.getCreatedAt(),
                settlement.getSettledAt(),
                settlement.getSettlementTransactionGroupId()
        );
    }

    private SettlementHistoryDTO toSettlementHistoryDTO(Settlement settlement) {
        return new SettlementHistoryDTO(
                settlement.getId(),
                settlement.getMerchantId(),
                settlement.getNetAmount(),
                settlement.getCommissionRate(),
                settlement.getGroupCount(),
                settlement.getStatus(),
                settlement.getSettledAt()
        );
    }

    private RefundResponse toRefundResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getTransactionGroupId(),
                refund.getSettlementId(),
                refund.getMerchantId(),
                refund.getMerchantWalletId(),
                refund.getBuyerId(),
                refund.getBuyerWalletId(),
                refund.getAmount(),
                refund.getOriginalAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getInitiator(),
                refund.getRefundTransactionGroupId(),
                refund.getCreatedAt(),
                refund.getProcessedAt(),
                refund.getUpdatedAt(),
                refund.getExpiresAt(),
                refund.getNotes()
        );
    }

    private RefundHistoryDTO toRefundHistoryDTO(Refund refund) {
        return new RefundHistoryDTO(
                refund.getId(),
                refund.getTransactionGroupId(),
                refund.getAmount(),
                refund.getReason(),
                refund.getStatus(),
                refund.getInitiator(),
                refund.getCreatedAt(),
                refund.getProcessedAt()
        );
    }

    // ==================== Deposit/Withdrawal Operations ====================

    @Override
    public ResponseEntity<DepositResponse> deposit(DepositRequest request) throws Exception {
        log.info("Deposit request: walletId={}, amount={}, externalReference={}",
                request.walletId(), request.amount(), request.externalReference());

        UUID referenceId = depositService.deposit(
                request.walletId(),
                request.amount(),
                request.externalReference()
        );

        DepositResponse response = new DepositResponse(
                referenceId,
                request.walletId(),
                request.amount(),
                request.externalReference(),
                "COMPLETED",
                java.time.LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<WithdrawalResponse> withdraw(WithdrawalRequest request) throws Exception {
        log.info("Withdrawal request: walletId={}, amount={}, destinationAccount={}",
                request.walletId(), request.amount(), request.destinationAccount());

        UUID referenceId = withdrawalService.withdraw(
                request.walletId(),
                request.amount(),
                request.destinationAccount()
        );

        WithdrawalResponse response = new WithdrawalResponse(
                referenceId,
                request.walletId(),
                request.amount(),
                request.destinationAccount(),
                "COMPLETED",
                java.time.LocalDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
