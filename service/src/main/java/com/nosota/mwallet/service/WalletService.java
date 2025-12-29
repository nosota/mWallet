package com.nosota.mwallet.service;

import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.TransactionNotFoundException;
import com.nosota.mwallet.error.WalletNotFoundException;
import com.nosota.mwallet.model.Transaction;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for wallet operations following banking ledger standards.
 *
 * <p>This service implements a two-phase transaction lifecycle:
 * <ul>
 *   <li>Phase 1 (HOLD): Block funds without transfer</li>
 *   <li>Phase 2 (Final): SETTLED, RELEASED, or CANCELLED</li>
 * </ul>
 *
 * <p>All transactions are immutable - new transactions are created for each state change.
 * <p>All finalization methods (settle/release/cancel) create offsetting transactions.
 */
@Service
@Validated
@AllArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletBalanceService walletBalanceService;

    /**
     * Holds (blocks) a specified amount from the wallet for debit operation.
     *
     * <p>This is Phase 1 of a two-phase transaction. The amount is blocked from the sender's
     * available balance but not yet transferred. The wallet must have sufficient funds.
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: negative (debit)</li>
     *   <li>Type: DEBIT</li>
     *   <li>Status: HOLD</li>
     * </ul>
     *
     * <p>Example: Hold $100 from wallet for payment
     * → Creates transaction: -100, DEBIT, HOLD
     *
     * @param walletId    The unique identifier of the wallet to debit from
     * @param amount      The amount to hold (must be positive, will be negated internally)
     * @param referenceId UUID grouping related transactions (typically matches transaction group ID)
     * @return The ID of the created HOLD transaction
     * @throws WalletNotFoundException    if the wallet does not exist
     * @throws InsufficientFundsException if the wallet has insufficient available balance
     */
    @Transactional
    public Integer holdDebit(@NotNull Integer walletId, @Positive Long amount, @NotNull UUID referenceId)
            throws WalletNotFoundException, InsufficientFundsException {

        // Verify wallet exists
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // Check sufficient funds (pessimistic locking handled by balance service)
        Long availableBalance = walletBalanceService.getAvailableBalance(walletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException(
                    String.format("Insufficient funds in wallet %d: available=%d, required=%d",
                            walletId, availableBalance, amount));
        }

        // Create HOLD transaction (negative amount for debit)
        Transaction holdTransaction = new Transaction();
        holdTransaction.setWalletId(wallet.getId());
        holdTransaction.setAmount(-amount);
        holdTransaction.setType(TransactionType.DEBIT);
        holdTransaction.setStatus(TransactionStatus.HOLD);
        holdTransaction.setReferenceId(referenceId);
        holdTransaction.setHoldReserveTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(holdTransaction);

        log.info("Held debit: walletId={}, amount={}, referenceId={}, transactionId={}",
                walletId, amount, referenceId, savedTransaction.getId());

        return savedTransaction.getId();
    }

    /**
     * Holds (prepares) a specified amount for the wallet for credit operation.
     *
     * <p>This is Phase 1 of a two-phase transaction. The amount is prepared for the recipient
     * but not yet added to their available balance. No balance check is performed (incoming funds).
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: positive (credit)</li>
     *   <li>Type: CREDIT</li>
     *   <li>Status: HOLD</li>
     * </ul>
     *
     * <p>Example: Hold $100 for wallet (incoming payment)
     * → Creates transaction: +100, CREDIT, HOLD
     *
     * @param walletId    The unique identifier of the wallet to credit to
     * @param amount      The amount to hold (must be positive)
     * @param referenceId UUID grouping related transactions (typically matches transaction group ID)
     * @return The ID of the created HOLD transaction
     * @throws WalletNotFoundException if the wallet does not exist
     */
    @Transactional
    public Integer holdCredit(@NotNull Integer walletId, @Positive Long amount, @NotNull UUID referenceId)
            throws WalletNotFoundException {

        // Verify wallet exists
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // Create HOLD transaction (positive amount for credit)
        Transaction holdTransaction = new Transaction();
        holdTransaction.setWalletId(walletId);
        holdTransaction.setAmount(amount);
        holdTransaction.setType(TransactionType.CREDIT);
        holdTransaction.setStatus(TransactionStatus.HOLD);
        holdTransaction.setReferenceId(referenceId);
        holdTransaction.setHoldReserveTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(holdTransaction);

        log.info("Held credit: walletId={}, amount={}, referenceId={}, transactionId={}",
                walletId, amount, referenceId, savedTransaction.getId());

        return savedTransaction.getId();
    }

    /**
     * Settles (finalizes) a previously held transaction in favor of the recipient.
     *
     * <p>This is Phase 2 (success path) of a two-phase transaction. The blocked funds are
     * transferred to the recipient. For debit transactions, funds are removed from sender's
     * available balance. For credit transactions, funds are added to recipient's available balance.
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: same as HOLD transaction</li>
     *   <li>Type: same as HOLD transaction</li>
     *   <li>Status: SETTLED (final state)</li>
     * </ul>
     *
     * <p>Example: Settle held $100 debit
     * → Finds: -100, DEBIT, HOLD
     * → Creates: -100, DEBIT, SETTLED
     *
     * @param walletId    The unique identifier of the wallet
     * @param referenceId UUID of the transaction group to settle
     * @return The ID of the created SETTLED transaction
     * @throws TransactionNotFoundException if no HOLD transaction exists for this wallet and reference
     */
    @Transactional
    public Integer settle(@NotNull Integer walletId, @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        // Find the HOLD transaction
        Transaction holdTransaction = transactionRepository
                .findByWalletIdAndReferenceIdAndStatuses(walletId, referenceId, TransactionStatus.HOLD)
                .orElseThrow(() -> new TransactionNotFoundException(
                        String.format("HOLD transaction not found for wallet %d and reference %s",
                                walletId, referenceId)));

        // Create SETTLED transaction with same amount and type
        Transaction settleTransaction = new Transaction();
        settleTransaction.setWalletId(holdTransaction.getWalletId());
        settleTransaction.setAmount(holdTransaction.getAmount());
        settleTransaction.setType(holdTransaction.getType());
        settleTransaction.setStatus(TransactionStatus.SETTLED);
        settleTransaction.setReferenceId(referenceId);
        settleTransaction.setConfirmRejectTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(settleTransaction);

        log.info("Settled transaction: walletId={}, amount={}, type={}, referenceId={}, transactionId={}",
                walletId, holdTransaction.getAmount(), holdTransaction.getType(), referenceId, savedTransaction.getId());

        return savedTransaction.getId();
    }

    /**
     * Releases (returns) previously held funds back to sender after dispute resolution.
     *
     * <p>This is Phase 2 (dispute/return path) of a two-phase transaction. The blocked funds
     * are returned to their original account by creating an offsetting transaction in the
     * OPPOSITE direction.
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: OPPOSITE sign of HOLD transaction</li>
     *   <li>Type: OPPOSITE type of HOLD transaction (DEBIT ↔ CREDIT)</li>
     *   <li>Status: RELEASED (final state)</li>
     * </ul>
     *
     * <p>Example: Release held $100 debit (return funds to sender)
     * → Finds: -100, DEBIT, HOLD
     * → Creates: +100, CREDIT, RELEASED (returns money)
     *
     * <p>Use case: Payment was held, conditions were met, but after investigation/dispute
     * it was decided to return the funds to the sender.
     *
     * @param walletId    The unique identifier of the wallet
     * @param referenceId UUID of the transaction group to release
     * @return The ID of the created RELEASED transaction
     * @throws TransactionNotFoundException if no HOLD transaction exists for this wallet and reference
     */
    @Transactional
    public Integer release(@NotNull Integer walletId, @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        // Find the HOLD transaction
        Transaction holdTransaction = transactionRepository
                .findByWalletIdAndReferenceIdAndStatuses(walletId, referenceId, TransactionStatus.HOLD)
                .orElseThrow(() -> new TransactionNotFoundException(
                        String.format("HOLD transaction not found for wallet %d and reference %s",
                                walletId, referenceId)));

        // Create RELEASED transaction with OPPOSITE amount and type
        TransactionType oppositeType = holdTransaction.getType() == TransactionType.DEBIT
                ? TransactionType.CREDIT
                : TransactionType.DEBIT;

        Transaction releaseTransaction = new Transaction();
        releaseTransaction.setWalletId(holdTransaction.getWalletId());
        releaseTransaction.setAmount(-holdTransaction.getAmount()); // Flip sign
        releaseTransaction.setType(oppositeType); // Flip type
        releaseTransaction.setStatus(TransactionStatus.RELEASED);
        releaseTransaction.setReferenceId(referenceId);
        releaseTransaction.setConfirmRejectTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(releaseTransaction);

        log.info("Released transaction: walletId={}, originalAmount={}, releaseAmount={}, originalType={}, releaseType={}, referenceId={}, transactionId={}",
                walletId, holdTransaction.getAmount(), releaseTransaction.getAmount(),
                holdTransaction.getType(), releaseTransaction.getType(), referenceId, savedTransaction.getId());

        return savedTransaction.getId();
    }

    /**
     * Cancels a previously held transaction before execution conditions were met.
     *
     * <p>This is Phase 2 (cancellation path) of a two-phase transaction. The blocked funds
     * are returned to their original account by creating an offsetting transaction in the
     * OPPOSITE direction.
     *
     * <p>Creates a transaction:
     * <ul>
     *   <li>Amount: OPPOSITE sign of HOLD transaction</li>
     *   <li>Type: OPPOSITE type of HOLD transaction (DEBIT ↔ CREDIT)</li>
     *   <li>Status: CANCELLED (final state)</li>
     * </ul>
     *
     * <p>Example: Cancel held $100 debit (return funds to sender)
     * → Finds: -100, DEBIT, HOLD
     * → Creates: +100, CREDIT, CANCELLED (returns money)
     *
     * <p>Use case: Payment was held, but conditions were never met (e.g., timeout, user cancelled,
     * validation failed), so the operation is cancelled before settlement.
     *
     * <p>Difference from release(): Cancel is used when conditions were never met,
     * release is used when conditions were met but funds are returned after dispute/investigation.
     *
     * @param walletId    The unique identifier of the wallet
     * @param referenceId UUID of the transaction group to cancel
     * @return The ID of the created CANCELLED transaction
     * @throws TransactionNotFoundException if no HOLD transaction exists for this wallet and reference
     */
    @Transactional
    public Integer cancel(@NotNull Integer walletId, @NotNull UUID referenceId)
            throws TransactionNotFoundException {

        // Find the HOLD transaction
        Transaction holdTransaction = transactionRepository
                .findByWalletIdAndReferenceIdAndStatuses(walletId, referenceId, TransactionStatus.HOLD)
                .orElseThrow(() -> new TransactionNotFoundException(
                        String.format("HOLD transaction not found for wallet %d and reference %s",
                                walletId, referenceId)));

        // Create CANCELLED transaction with OPPOSITE amount and type
        TransactionType oppositeType = holdTransaction.getType() == TransactionType.DEBIT
                ? TransactionType.CREDIT
                : TransactionType.DEBIT;

        Transaction cancelTransaction = new Transaction();
        cancelTransaction.setWalletId(holdTransaction.getWalletId());
        cancelTransaction.setAmount(-holdTransaction.getAmount()); // Flip sign
        cancelTransaction.setType(oppositeType); // Flip type
        cancelTransaction.setStatus(TransactionStatus.CANCELLED);
        cancelTransaction.setReferenceId(referenceId);
        cancelTransaction.setConfirmRejectTimestamp(LocalDateTime.now());

        Transaction savedTransaction = transactionRepository.save(cancelTransaction);

        log.info("Cancelled transaction: walletId={}, originalAmount={}, cancelAmount={}, originalType={}, cancelType={}, referenceId={}, transactionId={}",
                walletId, holdTransaction.getAmount(), cancelTransaction.getAmount(),
                holdTransaction.getType(), cancelTransaction.getType(), referenceId, savedTransaction.getId());

        return savedTransaction.getId();
    }

    /**
     * Processes a refund by transferring funds from merchant wallet to buyer wallet.
     *
     * <p>This operation is different from RELEASE/CANCEL which return funds from ESCROW.
     * Refund happens AFTER settlement when merchant already received the funds.
     *
     * <p>Creates two transactions atomically:
     * <ul>
     *   <li>DEBIT from merchant wallet: -amount, DEBIT, REFUNDED</li>
     *   <li>CREDIT to buyer wallet: +amount, CREDIT, REFUNDED</li>
     * </ul>
     *
     * <p>Example: Refund $100 from merchant to buyer
     * → Creates: -100, DEBIT, REFUNDED (on merchant wallet)
     * → Creates: +100, CREDIT, REFUNDED (on buyer wallet)
     *
     * <p>Both transactions share the same referenceId (refund transaction group ID).
     * The merchant wallet must have sufficient available balance.
     *
     * @param merchantWalletId The wallet ID of the merchant (funds source)
     * @param buyerWalletId    The wallet ID of the buyer (funds destination)
     * @param amount           The amount to refund (must be positive)
     * @param referenceId      UUID for refund transaction group (links debit and credit)
     * @return The referenceId of the created refund transactions
     * @throws WalletNotFoundException    if either wallet does not exist
     * @throws InsufficientFundsException if merchant wallet has insufficient available balance
     */
    @Transactional
    public UUID refund(@NotNull Integer merchantWalletId,
                       @NotNull Integer buyerWalletId,
                       @Positive Long amount,
                       @NotNull UUID referenceId)
            throws WalletNotFoundException, InsufficientFundsException {

        // Verify both wallets exist
        Wallet merchantWallet = walletRepository.findById(merchantWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Merchant wallet with ID " + merchantWalletId + " not found"));

        Wallet buyerWallet = walletRepository.findById(buyerWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Buyer wallet with ID " + buyerWalletId + " not found"));

        // Check merchant has sufficient funds
        Long availableBalance = walletBalanceService.getAvailableBalance(merchantWalletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException(
                    String.format("Insufficient funds in merchant wallet %d for refund: available=%d, required=%d",
                            merchantWalletId, availableBalance, amount));
        }

        LocalDateTime now = LocalDateTime.now();

        // Create DEBIT transaction on merchant wallet (negative amount)
        Transaction merchantDebit = new Transaction();
        merchantDebit.setWalletId(merchantWalletId);
        merchantDebit.setAmount(-amount);
        merchantDebit.setType(TransactionType.DEBIT);
        merchantDebit.setStatus(TransactionStatus.REFUNDED);
        merchantDebit.setReferenceId(referenceId);
        merchantDebit.setConfirmRejectTimestamp(now);

        Transaction savedMerchantDebit = transactionRepository.save(merchantDebit);

        // Create CREDIT transaction on buyer wallet (positive amount)
        Transaction buyerCredit = new Transaction();
        buyerCredit.setWalletId(buyerWalletId);
        buyerCredit.setAmount(amount);
        buyerCredit.setType(TransactionType.CREDIT);
        buyerCredit.setStatus(TransactionStatus.REFUNDED);
        buyerCredit.setReferenceId(referenceId);
        buyerCredit.setConfirmRejectTimestamp(now);

        Transaction savedBuyerCredit = transactionRepository.save(buyerCredit);

        log.info("Refund completed: merchantWalletId={}, buyerWalletId={}, amount={}, referenceId={}, merchantTxId={}, buyerTxId={}",
                merchantWalletId, buyerWalletId, amount, referenceId,
                savedMerchantDebit.getId(), savedBuyerCredit.getId());

        return referenceId;
    }
}
