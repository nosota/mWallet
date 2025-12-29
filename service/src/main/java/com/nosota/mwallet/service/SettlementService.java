package com.nosota.mwallet.service;

import com.nosota.mwallet.api.model.SettlementStatus;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.dto.SettlementCalculation;
import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.model.*;
import com.nosota.mwallet.repository.SettlementRepository;
import com.nosota.mwallet.repository.SettlementTransactionGroupRepository;
import com.nosota.mwallet.repository.TransactionRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for merchant settlement operations.
 *
 * <p>Settlement is the process of transferring accumulated funds from ESCROW
 * to a MERCHANT wallet, minus platform commission fees.
 *
 * <p>The service provides:
 * <ul>
 *   <li>Settlement calculation - preview amounts and fees before execution</li>
 *   <li>Settlement execution - perform the actual transfer</li>
 *   <li>Settlement history - query past settlements</li>
 * </ul>
 *
 * <p>Settlement workflow:
 * <pre>
 * 1. Calculate settlement for merchant (preview)
 * 2. Execute settlement:
 *    a. Create transaction group for settlement
 *    b. ESCROW → MERCHANT (net amount)
 *    c. ESCROW → SYSTEM (fee amount)
 *    d. Settle transaction group
 *    e. Record settlement in database
 * </pre>
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementTransactionGroupRepository settlementTransactionGroupRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionService transactionService;
    private final WalletService walletService;
    private final RefundReserveService refundReserveService;

    @Value("${settlement.commission-rate}")
    private BigDecimal commissionRate;

    @Value("${settlement.min-amount}")
    private Long minAmount;

    @Value("${settlement.hold-age-days}")
    private Integer holdAgeDays;

    /**
     * Calculates settlement for a specific merchant without executing it.
     *
     * <p>This method:
     * <ol>
     *   <li>Finds all unsettled transaction groups for the merchant</li>
     *   <li>Calculates total amount from HOLD CREDIT transactions on ESCROW</li>
     *   <li>Calculates platform commission fee</li>
     *   <li>Calculates net amount to transfer to merchant</li>
     * </ol>
     *
     * <p>Use this for preview/confirmation before executing settlement.
     *
     * @param merchantId The merchant ID
     * @return Settlement calculation with amounts and transaction groups
     * @throws IllegalStateException if no unsettled transaction groups found
     * @throws IllegalStateException if total amount is below minimum
     */
    public SettlementCalculation calculateSettlement(@NotNull Long merchantId) {
        // 1. Find unsettled transaction groups for merchant
        List<UUID> unsettledGroups = settlementTransactionGroupRepository
                .findUnsettledTransactionGroups(merchantId);

        if (unsettledGroups.isEmpty()) {
            throw new IllegalStateException(
                    String.format("No unsettled transaction groups found for merchant %d", merchantId));
        }

        log.info("Found {} unsettled transaction groups for merchant {}", unsettledGroups.size(), merchantId);

        // 2. Calculate total amount from HOLD CREDIT transactions on ESCROW
        Long totalAmount = calculateTotalAmount(unsettledGroups);

        // 3. Check minimum amount
        if (totalAmount < minAmount) {
            throw new IllegalStateException(
                    String.format("Total amount %d is below minimum %d for merchant %d",
                            totalAmount, minAmount, merchantId));
        }

        // 4. Calculate fee and net amount
        Long feeAmount = calculateFee(totalAmount);
        Long netAmount = totalAmount - feeAmount;

        log.info("Settlement calculation for merchant {}: total={}, fee={}, net={}, groups={}",
                merchantId, totalAmount, feeAmount, netAmount, unsettledGroups.size());

        return SettlementCalculation.builder()
                .merchantId(merchantId)
                .transactionGroups(unsettledGroups)
                .totalAmount(totalAmount)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .commissionRate(commissionRate)
                .groupCount(unsettledGroups.size())
                .build();
    }

    /**
     * Executes settlement for a specific merchant.
     *
     * <p>This method:
     * <ol>
     *   <li>Calculates settlement amounts</li>
     *   <li>Finds ESCROW, MERCHANT, and SYSTEM wallets</li>
     *   <li>Creates transaction group for settlement</li>
     *   <li>Creates ledger entries: ESCROW → MERCHANT, ESCROW → SYSTEM</li>
     *   <li>Settles transaction group</li>
     *   <li>Records settlement in database</li>
     *   <li>Links transaction groups to settlement</li>
     * </ol>
     *
     * <p>The entire operation is atomic - if any step fails, all changes are rolled back.
     *
     * @param merchantId The merchant ID
     * @return Created settlement entity
     * @throws IllegalStateException         if no unsettled groups or amount below minimum
     * @throws EntityNotFoundException       if ESCROW, MERCHANT, or SYSTEM wallet not found
     * @throws InsufficientFundsException    if ESCROW has insufficient funds
     * @throws Exception                     if settlement execution fails
     */
    @Transactional
    public Settlement executeSettlement(@NotNull Long merchantId) throws Exception {
        log.info("Executing settlement for merchant {}", merchantId);

        // 1. Generate idempotency key (format: merchant_{id}_settlement_{date})
        String idempotencyKey = generateIdempotencyKey(merchantId);
        log.debug("Generated idempotency key: {}", idempotencyKey);

        // 2. Check if settlement with this idempotency key already exists
        Optional<Settlement> existingSettlement = settlementRepository.findByIdempotencyKey(idempotencyKey);
        if (existingSettlement.isPresent()) {
            Settlement existing = existingSettlement.get();
            log.info("Settlement with idempotency key {} already exists: id={}, status={}",
                    idempotencyKey, existing.getId(), existing.getStatus());
            return existing;
        }

        // 3. Calculate settlement
        SettlementCalculation calculation = calculateSettlement(merchantId);

        // 4. Find wallets
        Wallet escrowWallet = findEscrowWallet();
        Wallet merchantWallet = findMerchantWallet(merchantId);
        Wallet systemWallet = findSystemWallet();

        log.info("Found wallets: escrow={}, merchant={}, system={}",
                escrowWallet.getId(), merchantWallet.getId(), systemWallet.getId());

        // 5. Create settlement entity (PENDING status)
        Settlement settlement = createSettlementEntity(calculation, merchantWallet.getCurrency(), idempotencyKey);
        settlement = settlementRepository.save(settlement);

        log.info("Created settlement entity: id={}, idempotencyKey={}", settlement.getId(), idempotencyKey);

        try {
            // 6. Create transaction group for settlement
            UUID settlementGroupId = transactionService.createTransactionGroup();
            settlement.setSettlementTransactionGroupId(settlementGroupId);

            log.info("Created settlement transaction group: id={}", settlementGroupId);

            // 7. Create ledger entries
            // ESCROW → MERCHANT (net amount)
            walletService.holdDebit(escrowWallet.getId(), calculation.netAmount(), settlementGroupId);
            walletService.holdCredit(merchantWallet.getId(), calculation.netAmount(), settlementGroupId);

            // ESCROW → SYSTEM (fee)
            if (calculation.feeAmount() > 0) {
                walletService.holdDebit(escrowWallet.getId(), calculation.feeAmount(), settlementGroupId);
                walletService.holdCredit(systemWallet.getId(), calculation.feeAmount(), settlementGroupId);
            }

            log.info("Created ledger entries for settlement {}", settlement.getId());

            // 8. Settle transaction group
            transactionService.settleTransactionGroup(settlementGroupId);

            log.info("Settled transaction group {} for settlement {}", settlementGroupId, settlement.getId());

            // 9. Update settlement status to COMPLETED
            settlement.setStatus(SettlementStatus.COMPLETED);
            settlement.setSettledAt(LocalDateTime.now());
            settlement = settlementRepository.save(settlement);

            // 10. Link transaction groups to settlement
            linkTransactionGroupsToSettlement(settlement.getId(), calculation);

            // 11. Create refund reserve (if enabled)
            try {
                refundReserveService.createReserve(settlement, escrowWallet.getId());
                log.info("Refund reserve created for settlement {}", settlement.getId());
            } catch (Exception e) {
                log.error("Failed to create refund reserve for settlement {}: {}",
                        settlement.getId(), e.getMessage(), e);
                // Don't fail the settlement if reserve creation fails
                // This is a non-critical operation
            }

            log.info("Settlement {} completed successfully for merchant {}: net={}",
                    settlement.getId(), merchantId, calculation.netAmount());

            return settlement;

        } catch (Exception e) {
            // Update settlement status to FAILED
            settlement.setStatus(SettlementStatus.FAILED);
            settlementRepository.save(settlement);

            log.error("Settlement {} failed for merchant {}: {}",
                    settlement.getId(), merchantId, e.getMessage(), e);

            throw e;
        }
    }

    /**
     * Retrieves a settlement by ID.
     *
     * @param settlementId The settlement ID
     * @return Settlement entity
     * @throws EntityNotFoundException if settlement not found
     */
    public Settlement getSettlement(@NotNull UUID settlementId) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Settlement not found: " + settlementId));
    }

    /**
     * Retrieves settlement history for a specific merchant.
     *
     * @param merchantId The merchant ID
     * @param pageable   Pagination information
     * @return Page of settlements
     */
    public Page<Settlement> getSettlementHistory(@NotNull Long merchantId, Pageable pageable) {
        return settlementRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
    }

    /**
     * Checks if a transaction group has been settled.
     *
     * @param transactionGroupId The transaction group ID
     * @return true if settled, false otherwise
     */
    public boolean isTransactionGroupSettled(@NotNull UUID transactionGroupId) {
        return settlementTransactionGroupRepository.existsByTransactionGroupId(transactionGroupId);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculates total amount from HOLD CREDIT transactions on ESCROW for given groups.
     */
    private Long calculateTotalAmount(List<UUID> transactionGroupIds) {
        // Find ESCROW wallet
        Wallet escrowWallet = findEscrowWallet();

        // Sum all HOLD CREDIT transactions on ESCROW for these groups
        Long total = 0L;
        for (UUID groupId : transactionGroupIds) {
            List<com.nosota.mwallet.model.Transaction> transactions = transactionRepository
                    .findByWalletIdAndReferenceIdAndStatuses(
                            escrowWallet.getId(),
                            groupId,
                            TransactionStatus.HOLD
                    )
                    .stream()
                    .filter(tx -> tx.getAmount() > 0) // CREDIT only
                    .toList();

            for (com.nosota.mwallet.model.Transaction tx : transactions) {
                total += tx.getAmount();
            }
        }

        return total;
    }

    /**
     * Calculates platform commission fee.
     */
    private Long calculateFee(Long totalAmount) {
        BigDecimal amount = BigDecimal.valueOf(totalAmount);
        BigDecimal fee = amount.multiply(commissionRate);
        return fee.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * Generates idempotency key for settlement.
     * Format: "merchant_{merchantId}_settlement_{date}"
     */
    private String generateIdempotencyKey(Long merchantId) {
        LocalDate today = LocalDate.now();
        return String.format("merchant_%d_settlement_%s", merchantId, today);
    }

    /**
     * Creates settlement entity from calculation.
     */
    private Settlement createSettlementEntity(SettlementCalculation calculation, String currency, String idempotencyKey) {
        Settlement settlement = new Settlement();
        settlement.setMerchantId(calculation.merchantId());
        settlement.setTotalAmount(calculation.totalAmount());
        settlement.setFeeAmount(calculation.feeAmount());
        settlement.setNetAmount(calculation.netAmount());
        settlement.setCommissionRate(calculation.commissionRate());
        settlement.setGroupCount(calculation.groupCount());
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setCreatedAt(LocalDateTime.now());
        settlement.setCurrency(currency);
        settlement.setIdempotencyKey(idempotencyKey);
        return settlement;
    }

    /**
     * Links transaction groups to settlement.
     */
    private void linkTransactionGroupsToSettlement(UUID settlementId, SettlementCalculation calculation) {
        Wallet escrowWallet = findEscrowWallet();

        for (UUID groupId : calculation.transactionGroups()) {
            // Calculate amount for this group
            Long groupAmount = transactionRepository
                    .findByWalletIdAndReferenceIdAndStatuses(
                            escrowWallet.getId(),
                            groupId,
                            TransactionStatus.HOLD
                    )
                    .stream()
                    .filter(tx -> tx.getAmount() > 0)
                    .mapToLong(com.nosota.mwallet.model.Transaction::getAmount)
                    .sum();

            SettlementTransactionGroup link = new SettlementTransactionGroup();
            link.setSettlementId(settlementId);
            link.setTransactionGroupId(groupId);
            link.setAmount(groupAmount);
            link.setCreatedAt(LocalDateTime.now());

            settlementTransactionGroupRepository.save(link);
        }

        log.info("Linked {} transaction groups to settlement {}", calculation.groupCount(), settlementId);
    }

    /**
     * Finds the system ESCROW wallet.
     */
    private Wallet findEscrowWallet() {
        return walletRepository.findByTypeAndOwnerIdIsNull(WalletType.ESCROW)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("ESCROW wallet not found"));
    }

    /**
     * Finds the merchant wallet for given merchant ID.
     */
    private Wallet findMerchantWallet(Long merchantId) {
        return walletRepository.findByOwnerIdAndType(merchantId, WalletType.MERCHANT)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "MERCHANT wallet not found for merchant: " + merchantId));
    }

    /**
     * Finds the system wallet for fees.
     */
    private Wallet findSystemWallet() {
        return walletRepository.findByTypeAndOwnerIdIsNull(WalletType.SYSTEM)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("SYSTEM wallet not found"));
    }
}
