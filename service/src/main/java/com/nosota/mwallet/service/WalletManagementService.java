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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Validated
@AllArgsConstructor
@Slf4j
public class WalletManagementService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionGroupRepository transactionGroupRepository;
    private final TransactionService transactionService;

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
     * newly created wallet.
     * </p>
     * <p>
     * <b>IMPORTANT:</b> Initial balance is deposited using proper double-entry bookkeeping:
     * - Transaction 1: DEBIT from DEPOSIT system wallet (goes negative, representing external money)
     * - Transaction 2: CREDIT to new wallet (receives the balance)
     * - Total: 0 (zero-sum principle maintained)
     * </p>
     * <p>
     * The DEPOSIT wallet represents the external world (banks, payment processors).
     * Its negative balance shows how much real money should be in correspondent accounts.
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
            // Use proper double-entry bookkeeping: create offsetting transactions manually
            // This ensures zero-sum: DEPOSIT=-initialBalance, newWallet=+initialBalance, Total=0
            Integer depositWalletId = getOrCreateDepositWallet();

            // Create transaction group
            TransactionGroup transactionGroup = new TransactionGroup();
            transactionGroup.setStatus(TransactionGroupStatus.SETTLED);
            transactionGroup = transactionGroupRepository.save(transactionGroup);
            UUID referenceId = transactionGroup.getId();

            // Transaction 1: DEBIT from DEPOSIT wallet (negative, representing money from external world)
            Transaction debitTransaction = new Transaction();
            debitTransaction.setWalletId(depositWalletId);
            debitTransaction.setAmount(-initialBalance);  // NEGATIVE
            debitTransaction.setType(TransactionType.DEBIT);
            debitTransaction.setStatus(TransactionStatus.SETTLED);
            debitTransaction.setReferenceId(referenceId);
            debitTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
            debitTransaction.setDescription("Deposit from external world");
            debitTransaction.setCurrency(newWallet.getCurrency());
            transactionRepository.save(debitTransaction);

            // Transaction 2: CREDIT to new wallet (positive, receiving balance)
            Transaction creditTransaction = new Transaction();
            creditTransaction.setWalletId(newWallet.getId());
            creditTransaction.setAmount(initialBalance);  // POSITIVE
            creditTransaction.setType(TransactionType.CREDIT);
            creditTransaction.setStatus(TransactionStatus.SETTLED);
            creditTransaction.setReferenceId(referenceId);
            creditTransaction.setConfirmRejectTimestamp(LocalDateTime.now());
            creditTransaction.setDescription("Initial balance deposit");
            creditTransaction.setCurrency(newWallet.getCurrency());
            transactionRepository.save(creditTransaction);

            log.info("Deposited {} to new wallet {} from DEPOSIT wallet {} (referenceId={})",
                    initialBalance, newWallet.getId(), depositWalletId, referenceId);
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
     * Gets or creates the DEPOSIT system wallet (on-demand creation).
     * <p>
     * The DEPOSIT wallet represents the external world (banks, payment processors).
     * It goes NEGATIVE when deposits occur (money came from outside).
     * The negative balance shows how much real money should be in correspondent accounts.
     * </p>
     * <p>
     * This ensures zero-sum principle:
     * - Deposit 100,000 to USER: DEPOSIT=-100,000, USER=+100,000, Total=0 ✓
     * </p>
     * <p>
     * <b>IMPORTANT:</b> Uses REQUIRES_NEW propagation to ensure the DEPOSIT wallet
     * is committed in a separate transaction before being used.
     * </p>
     *
     * @return The ID of the DEPOSIT system wallet
     */
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer getOrCreateDepositWallet() {
        // Try to find existing DEPOSIT wallet with description "DEPOSIT"
        return walletRepository.findByTypeAndDescription(WalletType.SYSTEM, "DEPOSIT")
                .map(Wallet::getId)
                .orElseGet(() -> {
                    log.info("Creating DEPOSIT system wallet");
                    return createSystemWallet("DEPOSIT");
                });
    }

    /**
     * Gets or creates the WITHDRAWAL system wallet (on-demand creation).
     * <p>
     * The WITHDRAWAL wallet represents money leaving the system to external world.
     * It goes POSITIVE when withdrawals occur (money left to outside).
     * </p>
     * <p>
     * <b>IMPORTANT:</b> Uses REQUIRES_NEW propagation to ensure the WITHDRAWAL wallet
     * is committed in a separate transaction before being used.
     * </p>
     *
     * @return The ID of the WITHDRAWAL system wallet
     */
    @org.springframework.transaction.annotation.Transactional(propagation = Propagation.REQUIRES_NEW)
    public Integer getOrCreateWithdrawalWallet() {
        // Try to find existing WITHDRAWAL wallet with description "WITHDRAWAL"
        return walletRepository.findByTypeAndDescription(WalletType.SYSTEM, "WITHDRAWAL")
                .map(Wallet::getId)
                .orElseGet(() -> {
                    log.info("Creating WITHDRAWAL system wallet");
                    return createSystemWallet("WITHDRAWAL");
                });
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
