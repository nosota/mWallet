package com.nosota.mwallet.service;

import com.nosota.mwallet.dto.TransactionDTO;
import com.nosota.mwallet.dto.TransactionMapper;
import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionGroup;
import com.nosota.mwallet.model.TransactionGroupStatus;
import com.nosota.mwallet.repository.TransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@Validated
public class TransactionService {

    private final WalletService walletService;

    private final TransactionGroupRepository transactionGroupRepository;

    private final TransactionRepository transactionRepository;

    public TransactionService(WalletService walletService, TransactionGroupRepository transactionGroupRepository, TransactionRepository transactionRepository) {
        this.walletService = walletService;
        this.transactionGroupRepository = transactionGroupRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public UUID createTransactionGroup() {
        TransactionGroup transactionGroup = new TransactionGroup();
        transactionGroup.setStatus(TransactionGroupStatus.IN_PROGRESS);
        transactionGroup = transactionGroupRepository.save(transactionGroup);

        UUID referenceId = transactionGroup.getId();  // This is the UUID generated
        return referenceId;
    }

    @Transactional
    public void confirmTransactionGroup(@NotNull UUID referenceId) throws TransactionNotFoundException {
        TransactionGroup transactionGroup = transactionGroupRepository.findById(referenceId)
                .orElseThrow(() -> new EntityNotFoundException("No transaction group found with referenceId: " + referenceId));

        List<Transaction> transactions = transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);
        for(int i = 0; i < transactions.size(); ++i) {
            Transaction transaction = transactions.get(i);
            walletService.confirm(transaction.getWalletId(), referenceId);
        }

        transactionGroup.setStatus(TransactionGroupStatus.CONFIRMED);
        transactionGroupRepository.save(transactionGroup);
    }

    @Transactional
    public void rejectTransactionGroup(@NotNull UUID referenceId) throws TransactionNotFoundException {
        TransactionGroup transactionGroup = transactionGroupRepository.findById(referenceId)
                .orElseThrow(() -> new EntityNotFoundException("No transaction group found with referenceId: " + referenceId));

        List<Transaction> transactions = transactionRepository.findByReferenceIdOrderByIdDesc(referenceId);
        for(int i = 0; i < transactions.size(); ++i) {
            Transaction transaction = transactions.get(i);
            walletService.reject(transaction.getWalletId(), referenceId);
        }

        transactionGroup.setStatus(TransactionGroupStatus.REJECTED);
        transactionGroupRepository.save(transactionGroup);
    }

    /**
     * Facilitates a transfer of funds between two specified wallets.
     *
     * <p>
     * This method initiates a transaction to move the specified amount of funds from the wallet
     * associated with {@code senderId} to the wallet associated with {@code recipientId}. The method
     * ensures that the sender has a sufficient balance before initiating the transfer.
     * </p>
     *
     * <p>
     * If the transfer is successful, a unique transaction ID (UUID) is generated and returned which can
     * be used for tracking or verification purposes.
     * </p>
     *
     * @param senderId The unique identifier (ID) of the sender's wallet. Must not be {@code null}.
     *
     * @param recipientId The unique identifier (ID) of the recipient's wallet. Must not be {@code null}.
     *
     * @param amount The amount to be transferred. It should be a positive value representing the number
     *               of units to transfer.
     *
     * @return A {@link UUID} representing the unique identifier of the initiated transaction. This UUID
     *         can be used for future reference or for tracking the status of the transaction.
     *
     * @throws IllegalArgumentException If {@code senderId} or {@code recipientId} are {@code null}, or if
     *                                  {@code amount} is non-positive.
     *
     * @throws InsufficientFundsException If the sender's wallet does not have a sufficient balance to
     *                                    cover the transfer amount.
     *
     * @throws Exception If any other unexpected error occurs during the transfer process.
     */
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public UUID transferBetweenTwoWallets(@NotNull Integer senderId, @NotNull Integer recipientId, @Positive Long amount) throws Exception {
        // Create a TransactionGroup with the status IN_PROGRESS
        UUID referenceId = createTransactionGroup();

        try {
            // 2. Hold the amount from the sender's account for deduction
            walletService.hold(senderId, amount, referenceId);

            // 3. Reserve the amount on recipient's account for addition
            walletService.reserve(recipientId, amount, referenceId);

            // 4. Update the TransactionGroup status to CONFIRMED
            // And confirm all HOLD and RESERVE operations made under the same referenceId.
            confirmTransactionGroup(referenceId);
        } catch (Exception e) {
            // 5. Update the TransactionGroup status to REJECTED
            // And reject all HOLD and RESERVE operations made under the same referenceId.
            rejectTransactionGroup(referenceId);
            throw e;  // Propagate the exception for further handling or to inform the user
        }

        return referenceId; // Return the referenceId for tracking or further operations
    }

    /**
     * Retrieves the status of a transaction group associated with a specific reference ID.
     *
     * <p>
     * This method looks up the transaction group corresponding to the provided {@code referenceId}
     * and returns its current status. Transaction groups might be used to batch or group multiple
     * transactions together, and their status can be used to track the combined status of all
     * transactions within the group.
     * </p>
     *
     * <p>
     * If there's no transaction group associated with the given {@code referenceId}, this method may
     * return {@code null} or throw an exception, based on the underlying implementation.
     * </p>
     *
     * @param referenceId The unique identifier (UUID) of the transaction group whose status is to be retrieved.
     *                    Must not be {@code null}.
     *
     * @return The {@link TransactionGroupStatus} indicating the current status of the transaction group.
     *         It can be one of the predefined statuses like PENDING, CONFIRMED, REJECTED, etc.
     *
     * @throws IllegalArgumentException If {@code referenceId} is {@code null}.
     *
     * @throws Exception If any other unexpected error occurs during the retrieval process.
     */
    public TransactionGroupStatus getStatusForReferenceId(@NotNull UUID referenceId) {
        return transactionGroupRepository.findById(referenceId)
                .map(TransactionGroup::getStatus)
                .orElseThrow(() -> new EntityNotFoundException("No transaction group found with referenceId: " + referenceId));
    }

    /**
     * Retrieves a list of transactions associated with a specific reference ID.
     *
     * <p>
     * This method fetches all transactions that are linked to the given {@code referenceId}. The reference ID
     * can be seen as a mechanism to group or batch multiple transactions together for a specific purpose or
     * context. This method helps in fetching all such transactions under this grouping.
     * </p>
     *
     * <p>
     * If no transactions are associated with the given {@code referenceId}, this method will return an empty list.
     * It's recommended to check the size of the returned list or use it directly in enhanced for-loops or streams
     * without the need for null checks.
     * </p>
     *
     * @param referenceId The unique identifier (UUID) of the transaction group for which associated transactions
     *                    are to be retrieved. Must not be {@code null}.
     *
     * @return A list of {@link TransactionDTO} objects representing each transaction associated with the reference ID.
     *         If no transactions are found, an empty list is returned.
     *
     * @throws IllegalArgumentException If {@code referenceId} is {@code null}.
     *
     * @throws DataAccessException If any issues arise during data retrieval from the underlying storage or database.
     *
     */
    public List<TransactionDTO> getTransactionsByReferenceId(@NotNull UUID referenceId) {
        List<Transaction> transactions = transactionRepository.findByReferenceId(referenceId);
        return TransactionMapper.INSTANCE.toDTOList(transactions);
    }
}
