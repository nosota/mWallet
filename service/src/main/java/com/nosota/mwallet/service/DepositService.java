package com.nosota.mwallet.service;

import com.nosota.mwallet.error.WalletNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

/**
 * Service for handling deposit operations.
 *
 * <p>Deposit represents money entering the system from external world
 * (banks, payment processors, cash). It creates proper double-entry bookkeeping:
 * <ul>
 *   <li>DEBIT from DEPOSIT system wallet (goes negative - source of funds)</li>
 *   <li>CREDIT to target wallet (receives the funds)</li>
 * </ul>
 *
 * <p>The DEPOSIT wallet represents the external world. Its negative balance
 * shows how much real money should be in correspondent bank accounts.
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class DepositService {

    private final WalletManagementService walletManagementService;
    private final TransactionService transactionService;
    private final com.nosota.mwallet.repository.WalletRepository walletRepository;

    /**
     * Deposits funds to a wallet from external source.
     *
     * @param walletId          Target wallet ID
     * @param amount            Amount to deposit (in cents/minor units)
     * @param externalReference External reference ID (e.g., bank transaction ID)
     * @return Transaction group UUID (referenceId)
     * @throws WalletNotFoundException if wallet does not exist
     */
    @Transactional
    public UUID deposit(Integer walletId, Long amount, String externalReference) throws Exception {
        log.info("Processing deposit: walletId={}, amount={}, externalReference={}",
                walletId, amount, externalReference);

        // 1. Get or create DEPOSIT system wallet
        Integer depositWalletId = walletManagementService.getOrCreateDepositWallet();
        log.debug("Using DEPOSIT wallet: walletId={}", depositWalletId);

        // 2. Verify target wallet exists (will throw if not found)
        walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet with ID " + walletId + " not found"));

        // 3. Create direct transfer: DEPOSIT → TARGET (SETTLED immediately)
        // This creates 2 SETTLED transactions (no HOLD phase needed):
        // - Transaction 1: DEBIT from DEPOSIT wallet (-amount, SETTLED)
        // - Transaction 2: CREDIT to target wallet (+amount, SETTLED)
        // - Sum: 0 (zero-sum maintained)
        UUID referenceId = transactionService.directTransfer(
                depositWalletId,
                walletId,
                amount
        );

        log.info("Deposit completed: walletId={}, amount={}, referenceId={}, externalReference={}",
                walletId, amount, referenceId, externalReference);

        return referenceId;
    }
}
