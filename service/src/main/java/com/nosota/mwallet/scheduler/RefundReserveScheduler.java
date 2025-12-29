package com.nosota.mwallet.scheduler;

import com.nosota.mwallet.service.RefundReserveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for refund reserve operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Release expired reserves (cron: every hour)</li>
 *   <li>Settle HOLD transactions for expired reserves</li>
 *   <li>Transfer funds from RESERVE_WALLET to MERCHANT_WALLET</li>
 * </ul>
 *
 * <p>Configuration:
 * <pre>
 * scheduler:
 *   refund-reserve:
 *     enabled: true                           # enable/disable scheduler
 *     release-expired-cron: "0 0 * * * *"    # every hour
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        value = "scheduler.refund-reserve.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RefundReserveScheduler {

    private final RefundReserveService refundReserveService;

    /**
     * Releases expired refund reserves.
     *
     * <p>Runs every hour by default (configurable via cron expression).
     * Finds reserves where:
     * <ul>
     *   <li>status IN (ACTIVE, PARTIALLY_USED)</li>
     *   <li>expiresAt < now</li>
     * </ul>
     *
     * <p>For each expired reserve:
     * <ol>
     *   <li>Settles HOLD transaction group</li>
     *   <li>Transfers: RESERVE_WALLET → MERCHANT_WALLET</li>
     *   <li>Updates reserve status to RELEASED</li>
     * </ol>
     */
    @Scheduled(cron = "${scheduler.refund-reserve.release-expired-cron:0 0 * * * *}")
    public void releaseExpiredReserves() {
        log.info("Starting scheduled job: release expired refund reserves");

        try {
            int releasedCount = refundReserveService.releaseExpiredReserves();

            if (releasedCount > 0) {
                log.info("Successfully released {} expired refund reserves", releasedCount);
            } else {
                log.debug("No expired refund reserves found");
            }

        } catch (Exception e) {
            log.error("Failed to release expired refund reserves: {}", e.getMessage(), e);
        }
    }
}
