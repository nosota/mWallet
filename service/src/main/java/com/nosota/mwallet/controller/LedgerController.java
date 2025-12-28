package com.nosota.mwallet.controller;

import com.nosota.mwallet.api.LedgerApi;
import com.nosota.mwallet.api.dto.PagedResponse;
import com.nosota.mwallet.api.dto.SettlementHistoryDTO;
import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
import com.nosota.mwallet.api.response.*;
import com.nosota.mwallet.dto.SettlementCalculation;
import com.nosota.mwallet.model.Settlement;
import com.nosota.mwallet.service.SettlementService;
import com.nosota.mwallet.service.TransactionService;
import com.nosota.mwallet.service.WalletBalanceService;
import com.nosota.mwallet.service.WalletService;
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

@RestController
@Validated
@RequiredArgsConstructor
@Slf4j
public class LedgerController implements LedgerApi {

    private final WalletService walletService;
    private final WalletBalanceService walletBalanceService;
    private final TransactionService transactionService;
    private final SettlementService settlementService;

    @Override
    public ResponseEntity<TransactionResponse> holdDebit(Integer walletId, Long amount, UUID referenceId) throws Exception {
        Integer transactionId = walletService.holdDebit(walletId, amount, referenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TransactionResponse(transactionId, walletId, -amount, "DEBIT", "HOLD", referenceId));
    }

    @Override
    public ResponseEntity<TransactionResponse> holdCredit(Integer walletId, Long amount, UUID referenceId) throws Exception {
        Integer transactionId = walletService.holdCredit(walletId, amount, referenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TransactionResponse(transactionId, walletId, amount, "CREDIT", "HOLD", referenceId));
    }

    @Override
    public ResponseEntity<TransactionResponse> settle(Integer walletId, UUID referenceId) throws Exception {
        Integer transactionId = walletService.settle(walletId, referenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TransactionResponse(transactionId, walletId, null, null, "SETTLED", referenceId));
    }

    @Override
    public ResponseEntity<TransactionResponse> release(Integer walletId, UUID referenceId) throws Exception {
        Integer transactionId = walletService.release(walletId, referenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TransactionResponse(transactionId, walletId, null, null, "RELEASED", referenceId));
    }

    @Override
    public ResponseEntity<TransactionResponse> cancel(Integer walletId, UUID referenceId) throws Exception {
        Integer transactionId = walletService.cancel(walletId, referenceId);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TransactionResponse(transactionId, walletId, null, null, "CANCELLED", referenceId));
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> createTransactionGroup() {
        UUID referenceId = transactionService.createTransactionGroup();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TransactionGroupResponse(referenceId, "IN_PROGRESS", null));
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> settleTransactionGroup(UUID referenceId) throws Exception {
        transactionService.settleTransactionGroup(referenceId);
        return ResponseEntity.ok(
                new TransactionGroupResponse(referenceId, "SETTLED", "Transaction group settled successfully"));
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> releaseTransactionGroup(UUID referenceId, String reason) throws Exception {
        transactionService.releaseTransactionGroup(referenceId, reason);
        return ResponseEntity.ok(
                new TransactionGroupResponse(referenceId, "RELEASED", "Transaction group released successfully"));
    }

    @Override
    public ResponseEntity<TransactionGroupResponse> cancelTransactionGroup(UUID referenceId, String reason) throws Exception {
        transactionService.cancelTransactionGroup(referenceId, reason);
        return ResponseEntity.ok(
                new TransactionGroupResponse(referenceId, "CANCELLED", "Transaction group cancelled successfully"));
    }

    @Override
    public ResponseEntity<TransferResponse> transfer(Integer senderId, Integer recipientId, Long amount) throws Exception {
        UUID referenceId = transactionService.transferBetweenTwoWallets(senderId, recipientId, amount);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new TransferResponse(referenceId, senderId, recipientId, amount, "SETTLED"));
    }

    @Override
    public ResponseEntity<BalanceResponse> getBalance(Integer walletId) {
        Long balance = walletBalanceService.getAvailableBalance(walletId);
        return ResponseEntity.ok(new BalanceResponse(walletId, balance));
    }

    @Override
    public ResponseEntity<GroupStatusResponse> getGroupStatus(UUID referenceId) {
        TransactionGroupStatus status = transactionService.getStatusForReferenceId(referenceId);
        return ResponseEntity.ok(new GroupStatusResponse(referenceId, status.name()));
    }

    @Override
    public ResponseEntity<List<TransactionDTO>> getGroupTransactions(UUID referenceId) {
        List<TransactionDTO> transactions = transactionService.getTransactionsByReferenceId(referenceId);
        return ResponseEntity.ok(transactions);
    }

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
}
