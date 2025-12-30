package com.nosota.mwallet.api.response;

import java.util.Map;

/**
 * Response for ledger reconciliation query.
 *
 * <p>Provides zero-sum validation data for the entire ledger system.
 */
public record ReconciliationResponse(
        Long totalSum,
        Long settledSum,
        Long holdSum,
        Long releasedSum,
        Long cancelledSum,
        Long refundedSum,
        Map<String, Long> sumByStatus
) {}
