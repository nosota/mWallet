package com.nosota.mwallet.service;

import com.nosota.mwallet.api.model.TransactionGroupStatus;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
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
     * This method initializes a new {@link Wallet} instance with the provided type, owner information,
     * saves it using the {@link WalletRepository}, and returns the generated ID of the
     * newly created wallet.
     * </p>
     * <p>
     * Ownership rules (enforced by database constraints in V2.04):
     * - USER wallets: ownerId must be non-null, ownerType must be USER_OWNER
     * - MERCHANT wallets: ownerId must be non-null, ownerType must be MERCHANT_OWNER
     * - ESCROW/SYSTEM wallets: ownerId must be null, ownerType must be SYSTEM_OWNER
     * </p>
     *
     * @param type The type of the wallet to be created, defined as an enumeration {@link WalletType}.
     * @param description Optional description of the wallet, value can be null.
     * @param ownerId The ID of the owner (user or merchant). Must be non-null for USER/MERCHANT wallets, null for ESCROW/SYSTEM wallets.
     * @param ownerType The type of owner (USER_OWNER, MERCHANT_OWNER, or SYSTEM_OWNER).
     * @return The unique identifier (ID) of the newly created wallet.
     * @throws IllegalArgumentException if ownership parameters violate rules
     */
    @Transactional
    public Integer createNewWallet(
            @NotNull WalletType type,
            String description,
            Long ownerId,
            @NotNull OwnerType ownerType) {

        validateOwnership(type, ownerId, ownerType);

        Wallet newWallet = new Wallet();
        newWallet.setType(type);
        newWallet.setDescription(description);
        newWallet.setOwnerId(ownerId);
        newWallet.setOwnerType(ownerType);

        return walletRepository.save(newWallet).getId();
    }

    /**
     * Creates a new wallet of the specified type with an initial balance and persists it to the database.
     * <p>
     * This method initializes a new {@link Wallet} instance with the provided type, owner information, and initial balance.
     * It then saves the wallet using the {@link WalletRepository} and returns the generated ID of the
     * newly created wallet. The initial balance is accounted for as a transaction entry to ensure consistency
     * with the transaction history.
     * </p>
     * <p>
     * Ownership rules (enforced by database constraints in V2.04):
     * - USER wallets: ownerId must be non-null, ownerType must be USER_OWNER
     * - MERCHANT wallets: ownerId must be non-null, ownerType must be MERCHANT_OWNER
     * - ESCROW/SYSTEM wallets: ownerId must be null, ownerType must be SYSTEM_OWNER
     * </p>
     *
     * @param type          The type of the wallet to be created, defined as an enumeration {@link WalletType}.
     * @param description   Optional description of the wallet, value can be null.
     * @param initialBalance The starting balance to be set for the newly created wallet. This should be a non-negative value.
     * @param ownerId The ID of the owner (user or merchant). Must be non-null for USER/MERCHANT wallets, null for ESCROW/SYSTEM wallets.
     * @param ownerType The type of owner (USER_OWNER, MERCHANT_OWNER, or SYSTEM_OWNER).
     * @return The unique identifier (ID) of the newly created wallet with the specified initial balance.
     * @throws IllegalArgumentException if ownership parameters violate rules
     */
    @Transactional
    public Integer createNewWalletWithBalance(
            @NotNull WalletType type,
            String description,
            @PositiveOrZero Long initialBalance,
            Long ownerId,
            @NotNull OwnerType ownerType) {

        validateOwnership(type, ownerId, ownerType);

        // Create a new wallet
        Wallet newWallet = new Wallet();
        newWallet.setType(type);
        newWallet.setDescription(description);
        newWallet.setOwnerId(ownerId);
        newWallet.setOwnerType(ownerType);
        newWallet = walletRepository.save(newWallet);

        if (initialBalance > 0) {
            // Create an initial transaction for the wallet.
            Transaction initialTransaction = new Transaction();
            initialTransaction.setAmount(initialBalance);
            initialTransaction.setWalletId(newWallet.getId());
            initialTransaction.setStatus(TransactionStatus.SETTLED);
            initialTransaction.setType(TransactionType.CREDIT); // assuming it's a credit transaction for the initial balance
            initialTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
            initialTransaction.setDescription("New wallet with initial balance");

            // Generate a reference ID for the initial transaction.
            TransactionGroup transactionGroup = new TransactionGroup();
            transactionGroup.setStatus(TransactionGroupStatus.SETTLED);
            transactionGroup = transactionGroupRepository.save(transactionGroup);
            initialTransaction.setReferenceId(transactionGroup.getId());

            transactionRepository.save(initialTransaction);
        }

        return newWallet.getId();
    }

    /**
     * Creates a system-owned ESCROW wallet (internal use only).
     * <p>
     * ESCROW wallets are temporary holding accounts for transactions.
     * They are system-owned (ownerId=null, ownerType=SYSTEM_OWNER).
     * </p>
     * <p>
     * This method should only be called by internal system operations.
     * It is not exposed via public API.
     * </p>
     *
     * @param description Optional description of the ESCROW wallet.
     * @return The unique identifier (ID) of the newly created ESCROW wallet.
     */
    @Transactional
    public Integer createEscrowWallet(String description) {
        return createNewWallet(WalletType.ESCROW, description, null, OwnerType.SYSTEM_OWNER);
    }

    /**
     * Creates a system-owned SYSTEM wallet for fees and technical operations (internal use only).
     * <p>
     * SYSTEM wallets are used for fees, commissions, and other technical operations.
     * They are system-owned (ownerId=null, ownerType=SYSTEM_OWNER).
     * </p>
     * <p>
     * This method should only be called by internal system operations.
     * It is not exposed via public API.
     * </p>
     *
     * @param description Optional description of the SYSTEM wallet.
     * @return The unique identifier (ID) of the newly created SYSTEM wallet.
     */
    @Transactional
    public Integer createSystemWallet(String description) {
        return createNewWallet(WalletType.SYSTEM, description, null, OwnerType.SYSTEM_OWNER);
    }

    /**
     * Creates a system-owned SYSTEM wallet with initial balance (internal use only).
     * <p>
     * SYSTEM wallets are used for fees, commissions, and other technical operations.
     * They are system-owned (ownerId=null, ownerType=SYSTEM_OWNER).
     * </p>
     *
     * @param description Optional description of the SYSTEM wallet.
     * @param initialBalance The starting balance for the wallet.
     * @return The unique identifier (ID) of the newly created SYSTEM wallet.
     */
    @Transactional
    public Integer createSystemWalletWithBalance(String description, @PositiveOrZero Long initialBalance) {
        return createNewWalletWithBalance(WalletType.SYSTEM, description, initialBalance, null, OwnerType.SYSTEM_OWNER);
    }

    /**
     * Validates wallet ownership parameters.
     * <p>
     * Enforces the following rules:
     * - USER wallets: ownerId must be non-null, ownerType must be USER_OWNER
     * - MERCHANT wallets: ownerId must be non-null, ownerType must be MERCHANT_OWNER
     * - ESCROW/SYSTEM wallets: ownerId must be null, ownerType must be SYSTEM_OWNER
     * </p>
     *
     * @param type The wallet type
     * @param ownerId The owner ID (can be null for system-owned wallets)
     * @param ownerType The owner type
     * @throws IllegalArgumentException if ownership parameters violate rules
     */
    private void validateOwnership(WalletType type, Long ownerId, OwnerType ownerType) {
        switch (type) {
            case USER -> {
                if (ownerId == null) {
                    throw new IllegalArgumentException(
                            "USER wallets must have a non-null ownerId (the user who owns the wallet)");
                }
                if (ownerType != OwnerType.USER_OWNER) {
                    throw new IllegalArgumentException(
                            "USER wallets must have ownerType=USER_OWNER (got: " + ownerType + ")");
                }
            }
            case MERCHANT -> {
                if (ownerId == null) {
                    throw new IllegalArgumentException(
                            "MERCHANT wallets must have a non-null ownerId (the merchant who owns the wallet)");
                }
                if (ownerType != OwnerType.MERCHANT_OWNER) {
                    throw new IllegalArgumentException(
                            "MERCHANT wallets must have ownerType=MERCHANT_OWNER (got: " + ownerType + ")");
                }
            }
            case ESCROW, SYSTEM -> {
                if (ownerId != null) {
                    throw new IllegalArgumentException(
                            type + " wallets must have ownerId=null (system-owned, no individual owner)");
                }
                if (ownerType != OwnerType.SYSTEM_OWNER) {
                    throw new IllegalArgumentException(
                            type + " wallets must have ownerType=SYSTEM_OWNER (got: " + ownerType + ")");
                }
            }
        }

        log.debug("Wallet ownership validated: type={}, ownerId={}, ownerType={}", type, ownerId, ownerType);
    }
}
