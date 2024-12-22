package com.nosota.mwallet.service;

import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.dto.TransactionMapper;
import com.nosota.mwallet.dto.TransactionSnapshotMapper;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionSnapshot;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.TransactionSnapshotRepository;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Validated
@AllArgsConstructor
public class TransactionStatisticService {
    private final TransactionRepository transactionRepository;
    private final TransactionSnapshotRepository transactionSnapshotRepository;

    /**
     * Retrieves daily credit operations for a specified wallet and date.
     * Note: Data from the transaction_snapshot_archive table is not considered in these statistics
     * for performance reasons, as it might slow down the query and is rarely used.
     *
     * @param walletId The ID of the wallet.
     * @param date     The date for which the statistics are to be retrieved.
     * @return A list of object arrays representing the credit operations.
     */
    public List<TransactionDTO> getDailyCreditOperations(@NotNull Integer walletId, @NotNull LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        List<Transaction> transactions = transactionRepository.findDailyCreditOperations(walletId, startOfDay, date);
        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findDailyCreditSnapshotOperations(walletId, startOfDay, date);

        // Performance notes:
        // Using Stream.concat allows for efficient list merging without creating intermediary lists.
        // The mapping is done in the stream, so there's no need for intermediary DTO lists.
        // Since Java streams are lazy by nature, mapping only happens when we eventually collect the results, which can be more efficient memory-wise.
        return Stream.concat(
                        transactions.stream().map(TransactionMapper.INSTANCE::toDTO),
                        snapshots.stream().map(TransactionSnapshotMapper.INSTANCE::toDTO))
                .collect(Collectors.toList());
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
    public List<TransactionDTO> getDailyDebitOperations(@NotNull Integer walletId, @NotNull LocalDateTime date) {
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        List<Transaction> transactions = transactionRepository.findDailyDebitOperations(walletId, startOfDay, date);
        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findDailyDebitSnapshotOperations(walletId, startOfDay, date);

        // Performance notes:
        // Using Stream.concat allows for efficient list merging without creating intermediary lists.
        // The mapping is done in the stream, so there's no need for intermediary DTO lists.
        // Since Java streams are lazy by nature, mapping only happens when we eventually collect the results, which can be more efficient memory-wise.
        return Stream.concat(
                        transactions.stream().map(TransactionMapper.INSTANCE::toDTO),
                        snapshots.stream().map(TransactionSnapshotMapper.INSTANCE::toDTO))
                .collect(Collectors.toList());
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
    public List<TransactionDTO> getCreditOperationsInRange(@NotNull Integer walletId, @NotNull LocalDateTime fromDate, @NotNull LocalDateTime toDate) {
        List<Transaction> transactions = transactionRepository.findCreditOperationsInRange(walletId, fromDate, toDate);
        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findCreditSnapshotOperationsInRange(walletId, fromDate, toDate);

        // Performance notes:
        // Using Stream.concat allows for efficient list merging without creating intermediary lists.
        // The mapping is done in the stream, so there's no need for intermediary DTO lists.
        // Since Java streams are lazy by nature, mapping only happens when we eventually collect the results, which can be more efficient memory-wise.
        return Stream.concat(
                        transactions.stream().map(TransactionMapper.INSTANCE::toDTO),
                        snapshots.stream().map(TransactionSnapshotMapper.INSTANCE::toDTO))
                .collect(Collectors.toList());
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
    public List<TransactionDTO> getDebitOperationsInRange(@NotNull Integer walletId, @NotNull LocalDateTime fromDate, @NotNull LocalDateTime toDate) {
        List<Transaction> transactions = transactionRepository.findDebitOperationsInRange(walletId, fromDate, toDate);
        List<TransactionSnapshot> snapshots = transactionSnapshotRepository.findDebitSnapshotOperationsInRange(walletId, fromDate, toDate);

        // Performance notes:
        // Using Stream.concat allows for efficient list merging without creating intermediary lists.
        // The mapping is done in the stream, so there's no need for intermediary DTO lists.
        // Since Java streams are lazy by nature, mapping only happens when we eventually collect the results, which can be more efficient memory-wise.
        return Stream.concat(
                        transactions.stream().map(TransactionMapper.INSTANCE::toDTO),
                        snapshots.stream().map(TransactionSnapshotMapper.INSTANCE::toDTO))
                .collect(Collectors.toList());
    }
}
