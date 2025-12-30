package com.nosota.mwallet.service;

import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.WalletNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/**
 * Service for handling withdrawal operations.
 *
 * <p>Withdrawal represents money leaving the system to external world
 * (banks, payment processors, cash). It creates proper double-entry bookkeeping:
 * <ul>
 *   <li>DEBIT from source wallet (loses the funds)</li>
 *   <li>CREDIT to WITHDRAWAL system wallet (goes positive - destination tracking)</li>
 * </ul>
 *
 * <p>The WITHDRAWAL wallet represents money leaving the system. Its positive balance
 * shows how much real money should have left to external accounts.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class WithdrawalService {

    private final WalletManagementService walletManagementService;
    private final TransactionService transactionService;
    private final com.nosota.mwallet.repository.WalletRepository walletRepository;
    private final WalletBalanceService walletBalanceService;

    /**
     * Withdraws funds from a wallet to external destination.
     *
     * @param walletId           Source wallet ID
     * @param amount             Amount to withdraw (in cents/minor units)
     * @param destinationAccount Destination account reference
     * @return Transaction group UUID (referenceId)
     * @throws WalletNotFoundException    if wallet does not exist
     * @throws InsufficientFundsException if wallet has insufficient balance
     */
    @Transactional
    public UUID withdraw(Integer walletId, Long amount, String destinationAccount) throws Exception {
        log.info("Processing withdrawal: walletId={}, amount={}, destination={}",
                walletId, amount, destinationAccount);

        // 1. Verify source wallet exists (will throw if not found)
        walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // 2. Check available balance
        Long availableBalance = walletBalanceService.getAvailableBalance(walletId);
        if (availableBalance < amount) {
            throw new InsufficientFundsException(
                    "Insufficient funds for withdrawal: available=" + availableBalance + ", requested=" + amount);
        }

        // 3. Get or create WITHDRAWAL system wallet
        Integer withdrawalWalletId = walletManagementService.getOrCreateWithdrawalWallet();
        log.debug("Using WITHDRAWAL wallet: walletId={}", withdrawalWalletId);

        // 4. Create transfer: SOURCE → WITHDRAWAL
        // This creates proper double-entry bookkeeping:
        // - Transaction 1: DEBIT from source wallet (-amount)
        // - Transaction 2: CREDIT to WITHDRAWAL wallet (+amount)
        // - Sum: 0 (zero-sum maintained)
        String description = "Withdrawal to external destination" +
                (destinationAccount != null ? " (dest: " + destinationAccount + ")" : "");

        UUID referenceId = transactionService.transferBetweenTwoWallets(
                walletId,
                withdrawalWalletId,
                amount
        );

        log.info("Withdrawal completed: walletId={}, amount={}, referenceId={}, destination={}",
                walletId, amount, referenceId, destinationAccount);

        return referenceId;
    }
}
