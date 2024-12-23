package com.nosota.mwallet.service;

import com.nosota.mwallet.model.*;
import com.nosota.mwallet.repository.TransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

@Service
@Validated
@AllArgsConstructor
@Slf4j
public class WalletManagementService {
    private final WalletRepository walletRepository;

    private final TransactionRepository transactionRepository;

    private final TransactionGroupRepository transactionGroupRepository;

    /**
     * Creates a new wallet of the specified type and persists it to the database.
     * <p>
     * This method initializes a new {@link Wallet} instance with the provided type,
     * saves it using the {@link WalletRepository}, and returns the generated ID of the
     * newly created wallet.
     * </p>
     *
     * @param type The type of the wallet to be created, defined as an enumeration {@link WalletType}.
     * @param description Optional description of the wallet, value can be null.
     * @return The unique identifier (ID) of the newly created wallet.
     */
    @Transactional
    public Integer createNewWallet(@NotNull WalletType type, String description) {
        Wallet newWallet = new Wallet();
        newWallet.setType(type);
        newWallet.setDescription(description);
        return walletRepository.save(newWallet).getId();
    }

    /**
     * Creates a new wallet of the specified type with an initial balance and persists it to the database.
     * <p>
     * This method initializes a new {@link Wallet} instance with the provided type and initial balance.
     * It then saves the wallet using the {@link WalletRepository} and returns the generated ID of the
     * newly created wallet. The initial balance is accounted for as a transaction entry to ensure consistency
     * with the transaction history.
     * </p>
     *
     * @param type          The type of the wallet to be created, defined as an enumeration {@link WalletType}.
     * @param description   Optional description of the wallet, value can be null.
     * @param initialBalance The starting balance to be set for the newly created wallet. This should be a non-negative value.
     * @return The unique identifier (ID) of the newly created wallet with the specified initial balance.
     */
    @Transactional
    public Integer createNewWalletWithBalance(@NotNull WalletType type, String description, @PositiveOrZero Long initialBalance) {
        // Create a new wallet
        Wallet newWallet = new Wallet();
        newWallet.setType(type);
        newWallet.setDescription(description);
        newWallet = walletRepository.save(newWallet);

        if (initialBalance > 0) {
            // Create an initial transaction for the wallet.
            Transaction initialTransaction = new Transaction();
            initialTransaction.setAmount(initialBalance);
            initialTransaction.setWalletId(newWallet.getId());
            initialTransaction.setStatus(TransactionStatus.CONFIRMED);
            initialTransaction.setType(TransactionType.CREDIT); // assuming it's a credit transaction for the initial balance
            initialTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
            initialTransaction.setDescription("New wallet with initial balance");

            // Generate a reference ID for the initial transaction.
            TransactionGroup transactionGroup = new TransactionGroup();
            transactionGroup.setStatus(TransactionGroupStatus.CONFIRMED);
            transactionGroup = transactionGroupRepository.save(transactionGroup);
            initialTransaction.setReferenceId(transactionGroup.getId());

            transactionRepository.save(initialTransaction);
        }

        return newWallet.getId();
    }
}
