package com.nosota.mwallet.controller;

import com.nosota.mwallet.api.LedgerApi;
import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
import com.nosota.mwallet.api.response.*;
import com.nosota.mwallet.service.TransactionService;
import com.nosota.mwallet.service.WalletBalanceService;
import com.nosota.mwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
