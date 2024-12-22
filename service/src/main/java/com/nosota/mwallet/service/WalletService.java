package com.nosota.mwallet.service;

import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.error.WalletNotFoundException;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.TransactionType;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Validated
@AllArgsConstructor
public class WalletService {
    private static final Logger LOG = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;

    private final TransactionRepository transactionRepository;

    private final WalletBalanceService walletBalanceService;

    /**
     * Holds a specified amount from the given wallet, ensuring the wallet has the necessary balance.
     * <p>
     * This method attempts to hold a specified amount from a wallet, identified by the provided wallet ID.
     * Before the hold operation is carried out, the available balance in the wallet is checked to ensure
     * that the hold amount does not exceed the available balance. If the wallet has insufficient funds or
     * if the wallet is not found, appropriate exceptions are thrown.
     * </p>
     * <p>
     * If the hold operation is successful, a new {@link Transaction} record with a status of "HOLD"
     * is created and saved to the database, and the transaction's ID is returned.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet from which the amount is to be held.
     * @param amount The amount to be held from the wallet. This amount should be positive.
     * @param referenceId A unique identifier (UUID) representing the reference or context for this hold operation.
     * @return The unique identifier (ID) of the created transaction representing the hold operation.
     * @throws WalletNotFoundException if the specified wallet ID does not correspond to an existing wallet.
     * @throws InsufficientFundsException if the wallet does not have sufficient funds to cover the hold amount.
     */
    @Transactional
    public Integer hold(@NotNull Integer walletId, @Positive Long amount, @NotNull UUID referenceId) throws WalletNotFoundException, InsufficientFundsException {
        Wallet senderWallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // Ensure the sender has enough balance
        Long availableBalance = walletBalanceService.getAvailableBalance(walletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException("Insufficient funds in wallet with ID " + walletId);
        }

        Transaction holdTransaction = new Transaction();
        holdTransaction.setWalletId(senderWallet.getId());
        holdTransaction.setAmount(-amount);
        holdTransaction.setStatus(TransactionStatus.HOLD);
        holdTransaction.setType(TransactionType.DEBIT);
        holdTransaction.setReferenceId(referenceId);
        holdTransaction.setHoldReserveTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(holdTransaction);

        return savedTransaction.getId();
    }

    /**
     * Reserves a specified amount for the given wallet.
     * <p>
     * This method creates a new {@link Transaction} with a status of "RESERVE" for a wallet, identified
     * by the provided wallet ID. The transaction signifies the reservation of a certain amount for
     * future use. No balance checks are done for reservation, as this operation typically denotes incoming funds.
     * </p>
     * <p>
     * If the wallet with the specified ID is not found in the database, a {@link WalletNotFoundException} is thrown.
     * If the reservation operation is successful, the ID of the created transaction is returned.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet for which the amount is to be reserved.
     * @param amount The amount to be reserved. This value should be positive, denoting an incoming or addition of funds.
     * @param referenceId A unique identifier (UUID) representing the reference or context for this reservation operation.
     * @return The unique identifier (ID) of the created transaction representing the reservation.
     * @throws WalletNotFoundException if the specified wallet ID does not correspond to an existing wallet.
     */
    @Transactional
    public Integer reserve(@NotNull Integer walletId, @Positive Long amount, @NotNull UUID referenceId) throws WalletNotFoundException {
        // Check if the wallet exists and if not, throw WalletNotFoundException
        Wallet senderWallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        Transaction reserveTransaction = new Transaction();
        reserveTransaction.setWalletId(walletId);
        reserveTransaction.setAmount(amount);
        reserveTransaction.setStatus(TransactionStatus.RESERVE);
        reserveTransaction.setReferenceId(referenceId);
        reserveTransaction.setType(TransactionType.CREDIT);
        reserveTransaction.setHoldReserveTimestamp(LocalDateTime.now());

        reserveTransaction = transactionRepository.save(reserveTransaction);
        return reserveTransaction.getId();
    }

    /**
     * Confirms a previously held or reserved transaction for a given wallet.
     * <p>
     * The method attempts to find an existing transaction with the status "HOLD" or "RESERVE" associated with
     * the specified wallet ID and reference ID. If found, a new transaction is created with the status "CONFIRMED"
     * to mark the successful completion of the operation.
     * </p>
     * <p>
     * If no corresponding "HOLD" or "RESERVE" transaction is found for the given wallet ID and reference ID,
     * a {@link TransactionNotFoundException} is thrown.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet associated with the transaction.
     * @param referenceId The unique identifier (UUID) used during the initial HOLD or RESERVE operation.
     * @return The unique identifier (ID) of the created transaction representing the confirmation.
     * @throws TransactionNotFoundException if no corresponding "HOLD" or "RESERVE" transaction is found for
     *         the specified wallet ID and reference ID.
     */
    @Transactional
    public Integer confirm(@NotNull Integer walletId, @NotNull UUID referenceId) throws TransactionNotFoundException {
        // Check if a HOLD transaction exists for the given referenceId
        Optional<Transaction> transactionOpt = transactionRepository.findByWalletIdAndReferenceIdAndStatuses(walletId,
                referenceId, TransactionStatus.HOLD, TransactionStatus.RESERVE);

        if (!transactionOpt.isPresent()) {
            throw new TransactionNotFoundException("Hold transaction not found for reference ID " + referenceId + " and wallet ID " + walletId);
        }

        Transaction holdTransaction = transactionOpt.get();

        // Create a new confirmed transaction
        Transaction confirmTransaction = new Transaction();
        confirmTransaction.setWalletId(holdTransaction.getWalletId());
        confirmTransaction.setAmount(holdTransaction.getAmount());
        confirmTransaction.setStatus(TransactionStatus.CONFIRMED);
        confirmTransaction.setType(holdTransaction.getType());
        confirmTransaction.setReferenceId(referenceId);
        confirmTransaction.setConfirmRejectTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(confirmTransaction);

        return savedTransaction.getId();
    }

    /**
     * Rejects a previously held or reserved transaction for a given wallet.
     * <p>
     * This method checks for the presence of an existing transaction with the status "HOLD" or "RESERVE" associated
     * with the provided wallet ID and reference ID. If found, a new transaction is created with the status "REJECTED"
     * to indicate the operation was not successfully completed.
     * </p>
     * <p>
     * If no "HOLD" or "RESERVE" transaction is found for the given wallet ID and reference ID,
     * a {@link TransactionNotFoundException} is thrown.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet associated with the transaction.
     * @param referenceId The unique identifier (UUID) used during the initial HOLD or RESERVE operation.
     * @return The unique identifier (ID) of the created transaction representing the rejection.
     * @throws TransactionNotFoundException if no corresponding "HOLD" or "RESERVE" transaction is found for
     *         the specified wallet ID and reference ID.
     */
    @Transactional
    public Integer reject(@NotNull Integer walletId, @NotNull UUID referenceId) throws TransactionNotFoundException {
        if(referenceId == null) {
            throw new IllegalArgumentException("referenceId must be not null.");
        }

        // Check if a HOLD transaction exists for the given referenceId
        Optional<Transaction> transactionOpt = transactionRepository.findByWalletIdAndReferenceIdAndStatuses(walletId,
                referenceId, TransactionStatus.HOLD, TransactionStatus.RESERVE);

        if (!transactionOpt.isPresent()) {
            throw new TransactionNotFoundException("Hold transaction not found for reference ID " + referenceId +
                    " and wallet ID " + walletId);
        }

        Transaction holdTransaction = transactionOpt.get();

        // Create a new rejected transaction
        Transaction rejectTransaction = new Transaction();
        rejectTransaction.setWalletId(holdTransaction.getWalletId());
        rejectTransaction.setAmount(holdTransaction.getAmount());
        rejectTransaction.setStatus(TransactionStatus.REJECTED);
        rejectTransaction.setType(holdTransaction.getType());
        rejectTransaction.setReferenceId(referenceId);
        rejectTransaction.setConfirmRejectTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(rejectTransaction);

        return savedTransaction.getId();
    }

    /**
     * Retrieves the {@link Wallet} entity associated with the specified wallet ID.
     * <p>
     * This method attempts to fetch the wallet using the provided ID from the underlying repository.
     * </p>
     * <p>
     * If no wallet is found for the given ID, an {@link IllegalArgumentException} is thrown with a
     * message indicating an invalid wallet ID.
     * </p>
     *
     * @param walletId The unique identifier (ID) of the wallet to be retrieved.
     * @return The {@link Wallet} entity corresponding to the provided ID.
     * @throws IllegalArgumentException if no wallet is found for the specified ID.
     */
    private Wallet getWallet(@NotNull Integer walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid wallet ID"));
    }
}
