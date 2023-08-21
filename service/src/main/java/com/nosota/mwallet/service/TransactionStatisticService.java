package com.nosota.mwallet.service;

import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.dto.TransactionMapper;
import com.nosota.mwallet.dto.TransactionSnapshotMapper;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionSnapshot;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.TransactionSnapshotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionStatisticService {
    private final TransactionRepository transactionRepository;
    private final TransactionSnapshotRepository transactionSnapshotRepository;

    @Autowired
    public TransactionStatisticService(TransactionRepository transactionRepository,
                                       TransactionSnapshotRepository transactionSnapshotRepository) {
        this.transactionRepository = transactionRepository;
        this.transactionSnapshotRepository = transactionSnapshotRepository;
    }

    /**
     * Retrieves daily credit operations for a specified wallet and date.
     * Note: Data from the transaction_snapshot_archive table is not considered in these statistics
     * for performance reasons, as it might slow down the query and is rarely used.
     *
     * @param walletId The ID of the wallet.
     * @param date     The date for which the statistics are to be retrieved.
     * @return A list of object arrays representing the credit operations.
     */
    public List<TransactionDTO> getDailyCreditOperations(Integer walletId, LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        List<Transaction> transactions = transactionRepository.findDailyCreditOperations(walletId, startOfDay, date);

        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findDailyCreditSnapshotOperations(walletId, startOfDay, date);

        // Convert transactions to DTOs and add to the result
        List<TransactionDTO> result = new ArrayList<>(TransactionMapper.INSTANCE.toDTOList(transactions));
        result.addAll(TransactionSnapshotMapper.INSTANCE.toDTOList(snapshots));

        return result;
    }

    /**
     * Retrieves daily debit operations for a specified wallet and date.
     * Note: Data from the transaction_snapshot_archive table is not considered in these statistics
     * for performance reasons, as it might slow down the query and is rarely used.
     *
     * @param walletId The ID of the wallet.
     * @param date     The date for which the statistics are to be retrieved.
     * @return A list of object arrays representing the debit operations.
     */
    public List<TransactionDTO> getDailyDebitOperations(Integer walletId, LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        List<Transaction> transactions = transactionRepository.findDailyDebitOperations(walletId, startOfDay, date);
        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findDailyDebitSnapshotOperations(walletId, startOfDay, date);

        // Convert transactions to DTOs and add to the result
        List<TransactionDTO> result = new ArrayList<>(TransactionMapper.INSTANCE.toDTOList(transactions));
        result.addAll(TransactionSnapshotMapper.INSTANCE.toDTOList(snapshots));

        return result;
    }

    /**
     * Retrieves credit operations for a specified wallet within a range of dates.
     * Note: Data from the transaction_snapshot_archive table is not considered in these statistics
     * for performance reasons, as it might slow down the query and is rarely used.
     *
     * @param walletId The ID of the wallet.
     * @param fromDate The start date of the range.
     * @param toDate   The end date of the range (inclusive).
     * @return A list of object arrays representing the credit operations.
     */
    public List<TransactionDTO> getCreditOperationsInRange(Integer walletId, LocalDateTime fromDate, LocalDateTime toDate) {
        List<Transaction> transactions = transactionRepository.findCreditOperationsInRange(walletId, fromDate, toDate);
        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findCreditSnapshotOperationsInRange(walletId, fromDate, toDate);

        // Convert transactions to DTOs and add to the result
        List<TransactionDTO> result = new ArrayList<>(TransactionMapper.INSTANCE.toDTOList(transactions));
        result.addAll(TransactionSnapshotMapper.INSTANCE.toDTOList(snapshots));

        return result;
    }

    /**
     * Retrieves debit operations for a specified wallet within a range of dates.
     * Note: Data from the transaction_snapshot_archive table is not considered in these statistics
     * for performance reasons, as it might slow down the query and is rarely used.
     *
     * @param walletId The ID of the wallet.
     * @param fromDate The start date of the range.
     * @param toDate   The end date of the range (inclusive).
     * @return A list of object arrays representing the debit operations.
     */
    public List<TransactionDTO> getDebitOperationsInRange(Integer walletId, LocalDateTime fromDate, LocalDateTime toDate) {
        List<Transaction> transactions = transactionRepository.findDebitOperationsInRange(walletId, fromDate, toDate);
        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findDebitSnapshotOperationsInRange(walletId, fromDate, toDate);

        // Convert transactions to DTOs and add to the result
        List<TransactionDTO> result = new ArrayList<>(TransactionMapper.INSTANCE.toDTOList(transactions));
        result.addAll(TransactionSnapshotMapper.INSTANCE.toDTOList(snapshots));

        return result;
    }
}
