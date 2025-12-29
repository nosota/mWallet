package com.nosota.mwallet.service;

import com.nosota.mwallet.model.RefundReserve;
import com.nosota.mwallet.model.RefundReserveStatus;
import com.nosota.mwallet.model.Settlement;
import com.nosota.mwallet.model.Wallet;
import com.nosota.mwallet.repository.RefundReserveRepository;
import com.nosota.mwallet.repository.WalletRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refund reserves.
 *
 * <p>Refund reserve is a mechanism to ensure funds are available for refunds
 * by physically holding (HOLD status) a percentage of merchant's settlement
 * for a configurable period.
 *
 * <p>Implementation (жесткий подход):
 * <ul>
 *   <li>Reserve is created as HOLD transactions: ESCROW → RESERVE_WALLET</li>
 *   <li>When expired: HOLD is settled → funds transfer to MERCHANT</li>
 *   <li>When used for refund: HOLD is cancelled, new refund transactions created</li>
 * </ul>
 *
 * <p>Configuration:
 * <pre>
 * settlement:
 *   refund-reserve-rate: 0.10      # 10% of net amount
 *   refund-reserve-hold-days: 30   # hold for 30 days
 * </pre>
 */
@Service
@Validated
@RequiredArgsConstructor
@Slf4j
public class RefundReserveService {

    private final RefundReserveRepository refundReserveRepository;
    private final WalletRepository walletRepository;
    private final TransactionService transactionService;
    private final WalletService walletService;

    @Value("${settlement.refund-reserve-rate:0.10}")
    private BigDecimal refundReserveRate;

    @Value("${settlement.refund-reserve-hold-days:30}")
    private Integer refundReserveHoldDays;

    @Value("${settlement.refund-reserve-enabled:true}")
    private Boolean refundReserveEnabled;

    /**
     * Creates a refund reserve for a settlement.
     *
     * <p>This method:
     * <ol>
     *   <li>Calculates reserve amount (net * rate)</li>
     *   <li>Creates transaction group for reserve</li>
     *   <li>Creates HOLD transactions: ESCROW → RESERVE_WALLET</li>
     *   <li>Creates RefundReserve entity to track it</li>
     * </ol>
     *
     * <p>The reserve HOLD physically blocks funds from being spent by merchant.
     *
     * @param settlement     The settlement for which to create reserve
     * @param escrowWalletId The ESCROW wallet ID (source of funds)
     * @return Created RefundReserve entity
     * @throws Exception if wallet operations fail
     */
    @Transactional
    public RefundReserve createReserve(@NotNull Settlement settlement,
                                       @NotNull Integer escrowWalletId) throws Exception {

        if (!refundReserveEnabled) {
            log.info("Refund reserve disabled, skipping for settlement {}", settlement.getId());
            return null;
        }

        // 1. Calculate reserve amount
        Long reserveAmount = calculateReserveAmount(settlement.getNetAmount());

        if (reserveAmount <= 0) {
            log.info("Reserve amount is zero, skipping for settlement {}", settlement.getId());
            return null;
        }

        log.info("Creating refund reserve: settlement={}, amount={}, rate={}, holdDays={}",
                settlement.getId(), reserveAmount, refundReserveRate, refundReserveHoldDays);

        // 2. Find merchant wallet and reserve wallet
        Wallet merchantWallet = walletRepository.findByOwnerIdAndType(
                        settlement.getMerchantId(), com.nosota.mwallet.model.WalletType.MERCHANT)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "MERCHANT wallet not found for merchant: " + settlement.getMerchantId()));

        Wallet reserveWallet = findOrCreateReserveWallet();

        // 3. Create transaction group for reserve
        UUID reserveGroupId = transactionService.createTransactionGroup();

        // 4. Create HOLD transactions: ESCROW → RESERVE_WALLET
        walletService.holdDebit(escrowWalletId, reserveAmount, reserveGroupId);
        walletService.holdCredit(reserveWallet.getId(), reserveAmount, reserveGroupId);

        log.info("Created HOLD transactions for reserve: escrow={} → reserve={}, amount={}, groupId={}",
                escrowWalletId, reserveWallet.getId(), reserveAmount, reserveGroupId);

        // 5. Create RefundReserve entity
        RefundReserve reserve = new RefundReserve();
        reserve.setSettlementId(settlement.getId());
        reserve.setMerchantId(settlement.getMerchantId());
        reserve.setMerchantWalletId(merchantWallet.getId());
        reserve.setReservedAmount(reserveAmount);
        reserve.setUsedAmount(0L);
        reserve.setAvailableAmount(reserveAmount);
        reserve.setReserveTransactionGroupId(reserveGroupId);
        reserve.setStatus(RefundReserveStatus.ACTIVE);
        reserve.setCreatedAt(LocalDateTime.now());
        reserve.setExpiresAt(LocalDateTime.now().plusDays(refundReserveHoldDays));
        reserve.setCurrency(settlement.getCurrency());

        reserve = refundReserveRepository.save(reserve);

        log.info("Refund reserve created: id={}, settlement={}, merchant={}, amount={}, expiresAt={}",
                reserve.getId(), settlement.getId(), settlement.getMerchantId(),
                reserveAmount, reserve.getExpiresAt());

        return reserve;
    }

    /**
     * Releases a reserve (settles HOLD transactions).
     *
     * <p>Used when:
     * <ul>
     *   <li>Reserve expires (scheduled job)</li>
     *   <li>Admin manually releases reserve</li>
     * </ul>
     *
     * <p>This settles the HOLD transaction group, transferring funds from
     * RESERVE_WALLET to MERCHANT_WALLET.
     *
     * @param reserveId The reserve ID to release
     * @throws Exception if settlement fails
     */
    @Transactional
    public void releaseReserve(@NotNull UUID reserveId) throws Exception {
        RefundReserve reserve = refundReserveRepository.findById(reserveId)
                .orElseThrow(() -> new IllegalStateException("Reserve not found: " + reserveId));

        if (reserve.getStatus() == RefundReserveStatus.RELEASED) {
            log.warn("Reserve {} already released", reserveId);
            return;
        }

        if (reserve.getStatus() == RefundReserveStatus.FULLY_USED) {
            log.warn("Reserve {} fully used, cannot release", reserveId);
            return;
        }

        log.info("Releasing reserve: id={}, settlement={}, merchant={}, availableAmount={}",
                reserveId, reserve.getSettlementId(), reserve.getMerchantId(),
                reserve.getAvailableAmount());

        // Settle the reserve transaction group
        // This transfers: RESERVE_WALLET (HOLD) → MERCHANT_WALLET (SETTLED)
        transactionService.settleTransactionGroup(reserve.getReserveTransactionGroupId());

        // Update reserve status
        reserve.setStatus(RefundReserveStatus.RELEASED);
        reserve.setReleasedAt(LocalDateTime.now());
        refundReserveRepository.save(reserve);

        log.info("Reserve released: id={}, merchant={}, amount={}",
                reserveId, reserve.getMerchantId(), reserve.getAvailableAmount());
    }

    /**
     * Releases all expired reserves.
     *
     * <p>Called by scheduled job to automatically release reserves
     * that have passed their expiration date.
     *
     * @return Number of reserves released
     */
    @Transactional
    public int releaseExpiredReserves() {
        List<RefundReserve> expiredReserves = refundReserveRepository
                .findExpiredReserves(LocalDateTime.now());

        int releasedCount = 0;
        for (RefundReserve reserve : expiredReserves) {
            try {
                releaseReserve(reserve.getId());
                releasedCount++;
            } catch (Exception e) {
                log.error("Failed to release expired reserve {}: {}",
                        reserve.getId(), e.getMessage(), e);
            }
        }

        log.info("Released {} expired reserves out of {} found",
                releasedCount, expiredReserves.size());

        return releasedCount;
    }

    /**
     * Uses reserve for a refund (decrements available amount).
     *
     * <p>This method is called by RefundService when executing a refund.
     * It updates the reserve accounting but does NOT create any transactions.
     * Actual refund transactions are created by RefundService.
     *
     * @param merchantId   The merchant ID
     * @param refundAmount The amount to use from reserve
     * @return true if reserve was used, false if not enough reserve available
     */
    @Transactional
    public boolean useReserveForRefund(@NotNull Long merchantId, @NotNull Long refundAmount) {
        // Find active reserves for merchant (oldest first)
        List<RefundReserve> activeReserves = refundReserveRepository
                .findActiveReservesByMerchantId(merchantId);

        Long remainingAmount = refundAmount;

        for (RefundReserve reserve : activeReserves) {
            if (remainingAmount <= 0) {
                break;
            }

            Long amountToUse = Math.min(remainingAmount, reserve.getAvailableAmount());

            // Update reserve amounts
            reserve.setUsedAmount(reserve.getUsedAmount() + amountToUse);
            reserve.setAvailableAmount(reserve.getAvailableAmount() - amountToUse);

            // Update status
            if (reserve.getAvailableAmount() == 0) {
                reserve.setStatus(RefundReserveStatus.FULLY_USED);
            } else if (reserve.getUsedAmount() > 0) {
                reserve.setStatus(RefundReserveStatus.PARTIALLY_USED);
            }

            refundReserveRepository.save(reserve);

            log.info("Used reserve for refund: reserveId={}, usedAmount={}, remainingAvailable={}",
                    reserve.getId(), amountToUse, reserve.getAvailableAmount());

            remainingAmount -= amountToUse;
        }

        return remainingAmount == 0;
    }

    /**
     * Calculates total available reserve for a merchant.
     *
     * @param merchantId The merchant ID
     * @return Total available reserve amount in cents
     */
    public Long getAvailableReserve(@NotNull Long merchantId) {
        return refundReserveRepository.calculateTotalAvailableReserve(merchantId);
    }

    /**
     * Finds reserve by settlement ID.
     *
     * @param settlementId The settlement ID
     * @return Optional containing the reserve if found
     */
    public Optional<RefundReserve> findBySettlement(@NotNull UUID settlementId) {
        return refundReserveRepository.findBySettlementId(settlementId);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Calculates reserve amount from settlement net amount.
     */
    private Long calculateReserveAmount(Long netAmount) {
        BigDecimal amount = BigDecimal.valueOf(netAmount);
        BigDecimal reserve = amount.multiply(refundReserveRate);
        return reserve.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * Finds or creates the RESERVE_WALLET for holding reserves.
     * Note: This is a simplification. In production, you might want
     * multiple reserve wallets or different strategy.
     */
    private Wallet findOrCreateReserveWallet() {
        // TODO: Implement proper reserve wallet management
        // For now, we'll use SYSTEM wallet as reserve wallet
        // In production, create a dedicated RESERVE wallet type
        return walletRepository.findByTypeAndOwnerIdIsNull(
                com.nosota.mwallet.model.WalletType.SYSTEM)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("SYSTEM wallet not found"));
    }
}
