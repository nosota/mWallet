package com.nosota.mwallet.service;

import com.nosota.mwallet.api.model.RefundInitiator;
import com.nosota.mwallet.api.model.RefundStatus;
import com.nosota.mwallet.api.model.RefundType;
import com.nosota.mwallet.api.model.TransactionGroupStatus;
import com.nosota.mwallet.api.request.RefundRequest;
import com.nosota.mwallet.error.InsufficientFundsException;
import com.nosota.mwallet.error.WalletNotFoundException;
import com.nosota.mwallet.model.*;
import com.nosota.mwallet.repository.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for refund operations.
 *
 * <p>Refund is the process of returning funds to a buyer AFTER settlement
 * (when merchant has already received payment). This is different from
 * RELEASE/CANCEL operations which return funds BEFORE settlement from ESCROW.
 *
 * <p>The service provides:
 * <ul>
 *   <li>Refund creation - with validation and balance checking</li>
 *   <li>Refund execution - transfer funds from merchant to buyer</li>
 *   <li>Refund history - query past refunds</li>
 *   <li>Pending funds processing - auto-execute when balance sufficient</li>
 *   <li>Expiry handling - expire old pending funds refunds</li>
 * </ul>
 *
 * <p>Refund workflow:
 * <pre>
 * 1. Validate refund request (order settled, time window, amount limits)
 * 2. Check merchant balance:
 *    a. If sufficient: execute immediately (PROCESSING → COMPLETED)
 *    b. If insufficient: create as PENDING_FUNDS with expiry
 * 3. Create refund entity
 * 4. If executing: MERCHANT → BUYER transfer (WalletService.refund)
 * 5. Update refund status
 * </pre>
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private final TransactionGroupRepository transactionGroupRepository;
    private final SettlementTransactionGroupRepository settlementTransactionGroupRepository;
    private final SettlementRepository settlementRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final WalletBalanceService walletBalanceService;
    private final TransactionService transactionService;

    @Value("${refund.return-commission-to-buyer}")
    private Boolean returnCommissionToBuyer;

    @Value("${refund.partial-refund-enabled}")
    private Boolean partialRefundEnabled;

    @Value("${refund.multiple-refunds-enabled}")
    private Boolean multipleRefundsEnabled;

    @Value("${refund.max-days-after-settlement}")
    private Integer maxDaysAfterSettlement;

    @Value("${refund.require-settled-status}")
    private Boolean requireSettledStatus;

    @Value("${refund.allow-negative-balance}")
    private Boolean allowNegativeBalance;

    @Value("${refund.auto-execute-pending}")
    private Boolean autoExecutePending;

    @Value("${refund.pending-funds-expiry-days}")
    private Integer pendingFundsExpiryDays;

    /**
     * Creates and potentially executes a refund.
     *
     * <p>This method performs comprehensive validation:
     * <ol>
     *   <li>Order exists and is SETTLED</li>
     *   <li>Time window check (maxDaysAfterSettlement)</li>
     *   <li>Amount validation (total refunds ≤ net amount received)</li>
     *   <li>No pending refunds for same order</li>
     *   <li>Merchant balance check</li>
     * </ol>
     *
     * <p>Depending on merchant balance:
     * <ul>
     *   <li>Sufficient: Execute immediately → COMPLETED</li>
     *   <li>Insufficient: Create as PENDING_FUNDS with expiry</li>
     * </ul>
     *
     * @param request Refund request details
     * @return Created refund entity
     * @throws IllegalStateException    if validation fails
     * @throws EntityNotFoundException  if order, settlement, or wallets not found
     */
    @Transactional
    public Refund createRefund(@Valid @NotNull RefundRequest request) {
        log.info("Creating refund for order {}, amount={}, initiator={}, idempotencyKey={}",
                request.transactionGroupId(), request.amount(), request.initiator(), request.idempotencyKey());

        // 0. Determine refund type (FULL or PARTIAL) - needed for idempotency check
        // Note: we need to get orderAmount first to determine type
        SettlementTransactionGroup settlementLink = settlementTransactionGroupRepository
                .findByTransactionGroupId(request.transactionGroupId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Settlement not found for order: " + request.transactionGroupId()));

        Long orderAmount = settlementLink.getAmount();
        Long totalRefunded = refundRepository.calculateTotalRefundedForOrder(request.transactionGroupId());
        RefundType refundType = determineRefundType(request.amount(), orderAmount, totalRefunded);

        log.debug("Determined refund type: {} (amount={}, orderAmount={}, totalRefunded={})",
                refundType, request.amount(), orderAmount, totalRefunded);

        // 1. Check idempotency: if refund with this key already exists, return it
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Optional<Refund> existingRefund = refundRepository
                    .findByTransactionGroupIdAndRefundTypeAndIdempotencyKey(
                            request.transactionGroupId(),
                            refundType,
                            request.idempotencyKey()
                    );

            if (existingRefund.isPresent()) {
                Refund existing = existingRefund.get();
                log.info("Refund with idempotency key {} already exists: id={}, status={}",
                        request.idempotencyKey(), existing.getId(), existing.getStatus());
                return existing;
            }
        }

        // 2. Validate order exists and is SETTLED
        TransactionGroup order = transactionGroupRepository.findById(request.transactionGroupId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Order not found: " + request.transactionGroupId()));

        if (requireSettledStatus && order.getStatus() != TransactionGroupStatus.SETTLED) {
            throw new IllegalStateException(
                    String.format("Order %s is not settled (status: %s)",
                            request.transactionGroupId(), order.getStatus()));
        }

        // 3. Get settlement entity
        Settlement settlement = settlementRepository.findById(settlementLink.getSettlementId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Settlement entity not found: " + settlementLink.getSettlementId()));

        // 4. Validate time window
        validateTimeWindow(settlement);

        // 5. Validate amount limits (already calculated totalRefunded above)
        validateRefundAmount(request.transactionGroupId(), request.amount(), orderAmount, totalRefunded);

        // 6. Check for pending refunds
        if (!multipleRefundsEnabled || refundRepository.hasPendingRefunds(request.transactionGroupId())) {
            throw new IllegalStateException(
                    "Cannot create refund: pending refunds exist for order " + request.transactionGroupId());
        }

        // 7. Find wallets
        Wallet merchantWallet = findMerchantWallet(order.getMerchantId());
        Wallet buyerWallet = findBuyerWallet(order.getBuyerId());

        // 8. Check merchant balance
        Long availableBalance = walletBalanceService.getAvailableBalance(merchantWallet.getId());
        boolean hasSufficientBalance = availableBalance >= request.amount();

        // 9. Create refund entity (with refundType and idempotencyKey)
        Refund refund = createRefundEntity(request, settlement, order, merchantWallet, buyerWallet,
                orderAmount, refundType, request.idempotencyKey());

        if (hasSufficientBalance) {
            // Execute immediately
            refund.setStatus(RefundStatus.PROCESSING);
            refund = refundRepository.save(refund);

            try {
                executeRefundTransaction(refund);
                refund.setStatus(RefundStatus.COMPLETED);
                refund.setProcessedAt(LocalDateTime.now());
                log.info("Refund {} completed immediately for order {}",
                        refund.getId(), request.transactionGroupId());
            } catch (Exception e) {
                refund.setStatus(RefundStatus.FAILED);
                refund.setNotes("Execution failed: " + e.getMessage());
                log.error("Refund {} execution failed: {}", refund.getId(), e.getMessage(), e);
            }
        } else {
            // Insufficient balance - create as PENDING_FUNDS
            if (!allowNegativeBalance) {
                refund.setStatus(RefundStatus.PENDING_FUNDS);
                refund.setExpiresAt(LocalDateTime.now().plusDays(pendingFundsExpiryDays));
                refund.setNotes(String.format("Insufficient merchant balance: available=%d, required=%d",
                        availableBalance, request.amount()));
                log.info("Refund {} created as PENDING_FUNDS (insufficient balance) for order {}",
                        refund.getId(), request.transactionGroupId());
            } else {
                // Allow negative balance - execute anyway
                refund.setStatus(RefundStatus.PROCESSING);
                refund = refundRepository.save(refund);

                try {
                    executeRefundTransaction(refund);
                    refund.setStatus(RefundStatus.COMPLETED);
                    refund.setProcessedAt(LocalDateTime.now());
                    log.info("Refund {} completed with negative balance for order {}",
                            refund.getId(), request.transactionGroupId());
                } catch (Exception e) {
                    refund.setStatus(RefundStatus.FAILED);
                    refund.setNotes("Execution failed: " + e.getMessage());
                    log.error("Refund {} execution failed: {}", refund.getId(), e.getMessage(), e);
                }
            }
        }

        refund.setUpdatedAt(LocalDateTime.now());
        return refundRepository.save(refund);
    }

    /**
     * Retrieves a refund by ID.
     *
     * @param refundId The refund ID
     * @return Refund entity
     * @throws EntityNotFoundException if refund not found
     */
    public Refund getRefund(@NotNull UUID refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new EntityNotFoundException("Refund not found: " + refundId));
    }

    /**
     * Retrieves refund history for a specific merchant.
     *
     * @param merchantId The merchant ID
     * @param pageable   Pagination information
     * @return Page of refunds
     */
    public Page<Refund> getRefundHistory(@NotNull Long merchantId, Pageable pageable) {
        return refundRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable);
    }

    /**
     * Retrieves all refunds for a specific order (transaction group).
     *
     * @param transactionGroupId The transaction group ID
     * @return List of refunds for this order
     */
    public List<Refund> getRefundsByOrder(@NotNull UUID transactionGroupId) {
        return refundRepository.findByTransactionGroupId(transactionGroupId);
    }

    /**
     * Processes PENDING_FUNDS refunds that can now be executed.
     *
     * <p>This method should be called by a scheduler to automatically
     * execute refunds when merchant balance becomes sufficient.
     *
     * <p>Only processes refunds where:
     * <ul>
     *   <li>Status is PENDING_FUNDS</li>
     *   <li>Not expired (expiresAt > now)</li>
     *   <li>Merchant has sufficient balance</li>
     * </ul>
     *
     * @return Number of refunds processed
     */
    @Transactional
    public int processPendingFundsRefunds() {
        if (!autoExecutePending) {
            log.debug("Auto-execute pending refunds is disabled");
            return 0;
        }

        List<Refund> pendingRefunds = refundRepository.findByStatus(RefundStatus.PENDING_FUNDS);
        int processedCount = 0;

        for (Refund refund : pendingRefunds) {
            // Skip expired refunds
            if (refund.getExpiresAt() != null && refund.getExpiresAt().isBefore(LocalDateTime.now())) {
                continue;
            }

            // Check merchant balance
            Long availableBalance = walletBalanceService.getAvailableBalance(refund.getMerchantWalletId());
            if (availableBalance >= refund.getAmount()) {
                try {
                    refund.setStatus(RefundStatus.PROCESSING);
                    refund.setUpdatedAt(LocalDateTime.now());
                    refundRepository.save(refund);

                    executeRefundTransaction(refund);

                    refund.setStatus(RefundStatus.COMPLETED);
                    refund.setProcessedAt(LocalDateTime.now());
                    refund.setUpdatedAt(LocalDateTime.now());
                    refundRepository.save(refund);

                    processedCount++;
                    log.info("Auto-executed PENDING_FUNDS refund {}", refund.getId());
                } catch (Exception e) {
                    refund.setStatus(RefundStatus.FAILED);
                    refund.setNotes("Auto-execution failed: " + e.getMessage());
                    refund.setUpdatedAt(LocalDateTime.now());
                    refundRepository.save(refund);
                    log.error("Failed to auto-execute refund {}: {}", refund.getId(), e.getMessage(), e);
                }
            }
        }

        log.info("Processed {} PENDING_FUNDS refunds", processedCount);
        return processedCount;
    }

    /**
     * Expires old PENDING_FUNDS refunds.
     *
     * <p>This method should be called by a scheduler to expire refunds
     * that have been in PENDING_FUNDS status for too long.
     *
     * @return Number of refunds expired
     */
    @Transactional
    public int expirePendingFundsRefunds() {
        List<Refund> expiredRefunds = refundRepository.findExpiredPendingFundsRefunds(LocalDateTime.now());

        for (Refund refund : expiredRefunds) {
            refund.setStatus(RefundStatus.EXPIRED);
            refund.setUpdatedAt(LocalDateTime.now());
            refund.setNotes("Refund expired - merchant balance was insufficient for " +
                    pendingFundsExpiryDays + " days");
            refundRepository.save(refund);
            log.info("Expired PENDING_FUNDS refund {}", refund.getId());
        }

        log.info("Expired {} PENDING_FUNDS refunds", expiredRefunds.size());
        return expiredRefunds.size();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Validates that refund is within allowed time window.
     */
    private void validateTimeWindow(Settlement settlement) {
        if (maxDaysAfterSettlement > 0 && settlement.getSettledAt() != null) {
            LocalDateTime deadline = settlement.getSettledAt().plusDays(maxDaysAfterSettlement);
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new IllegalStateException(
                        String.format("Refund window expired: settlement was %d days ago (max: %d days)",
                                java.time.temporal.ChronoUnit.DAYS.between(settlement.getSettledAt(), LocalDateTime.now()),
                                maxDaysAfterSettlement));
            }
        }
    }

    /**
     * Validates refund amount against order amount and existing refunds.
     */
    private void validateRefundAmount(UUID transactionGroupId, Long refundAmount,
                                      Long orderAmount, Long totalRefunded) {
        // Check amount is positive
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }

        // Check if this refund would exceed order amount
        Long newTotal = totalRefunded + refundAmount;
        if (newTotal > orderAmount) {
            throw new IllegalStateException(
                    String.format("Refund amount exceeds order net amount: " +
                                    "requested=%d, already refunded=%d, total=%d, order net=%d",
                            refundAmount, totalRefunded, newTotal, orderAmount));
        }

        // Check partial refund is enabled if not full refund
        if (!partialRefundEnabled && !refundAmount.equals(orderAmount - totalRefunded)) {
            throw new IllegalStateException("Partial refunds are not enabled");
        }
    }

    /**
     * Determines refund type based on amount comparison.
     */
    private RefundType determineRefundType(Long refundAmount, Long orderAmount, Long totalRefunded) {
        Long remainingAmount = orderAmount - totalRefunded;
        return refundAmount.equals(remainingAmount) ? RefundType.FULL : RefundType.PARTIAL;
    }

    /**
     * Creates refund entity from request.
     */
    private Refund createRefundEntity(RefundRequest request, Settlement settlement,
                                      TransactionGroup order, Wallet merchantWallet,
                                      Wallet buyerWallet, Long originalAmount,
                                      RefundType refundType, String idempotencyKey) {
        Refund refund = new Refund();
        refund.setTransactionGroupId(request.transactionGroupId());
        refund.setSettlementId(settlement.getId());
        refund.setMerchantId(order.getMerchantId());
        refund.setMerchantWalletId(merchantWallet.getId());
        refund.setBuyerId(order.getBuyerId());
        refund.setBuyerWalletId(buyerWallet.getId());
        refund.setAmount(request.amount());
        refund.setOriginalAmount(originalAmount);
        refund.setReason(request.reason());
        refund.setInitiator(request.initiator());
        refund.setCreatedAt(LocalDateTime.now());
        refund.setCurrency(merchantWallet.getCurrency());
        refund.setRefundType(refundType);
        refund.setIdempotencyKey(idempotencyKey);
        return refund;
    }

    /**
     * Executes refund transaction via WalletService.
     */
    private void executeRefundTransaction(Refund refund)
            throws WalletNotFoundException, InsufficientFundsException {

        // Create transaction group for refund
        UUID refundTransactionGroupId = transactionService.createTransactionGroup();
        refund.setRefundTransactionGroupId(refundTransactionGroupId);

        // Execute refund: MERCHANT → BUYER
        walletService.refund(
                refund.getMerchantWalletId(),
                refund.getBuyerWalletId(),
                refund.getAmount(),
                refundTransactionGroupId
        );

        log.info("Executed refund transaction: refundId={}, txGroupId={}, amount={}",
                refund.getId(), refundTransactionGroupId, refund.getAmount());
    }

    /**
     * Finds merchant wallet for given merchant ID.
     */
    private Wallet findMerchantWallet(Long merchantId) {
        return walletRepository.findByOwnerIdAndType(merchantId, WalletType.MERCHANT)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "MERCHANT wallet not found for merchant: " + merchantId));
    }

    /**
     * Finds buyer wallet for given buyer ID.
     */
    private Wallet findBuyerWallet(Long buyerId) {
        return walletRepository.findByOwnerIdAndType(buyerId, WalletType.USER)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "USER wallet not found for buyer: " + buyerId));
    }
}
