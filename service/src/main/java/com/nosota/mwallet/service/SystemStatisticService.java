package com.nosota.mwallet.service;

import com.nosota.mwallet.repository.SystemStatisticRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Service
@Validated
@AllArgsConstructor
@Slf4j
public class SystemStatisticService
{
    private final SystemStatisticRepository systemStatisticRepository;

    /**
     * Retrieves the reconciliation balance for the entire system.
     *
     * <p>The resulting balance represents the cumulative initial balance
     * of all wallets created within the system. This initial balance might
     * signify funds external to the system or the system's startup balance.</p>
     *
     * <p>This method accounts for all finalized transaction groups (SETTLED, RELEASED, CANCELLED).
     * According to double-entry accounting principles:
     * - Internal transfers should sum to zero
     * - Non-zero result indicates external funds or initial balances</p>
     *
     * <p>NOTE: This function processes all storage tiers (transaction, snapshot, archive)
     * and might take significant time for large datasets.</p>
     *
     * @return The reconciliation balance of all finalized transaction groups.
     */
    @Transactional
    public Long getReconciliationBalanceOfAllFinalizedGroups() {
        BigDecimal result = systemStatisticRepository.calculateReconciliationBalanceOfAllFinalizedGroups();
        result = result != null ? result : BigDecimal.ZERO;
        return result.longValueExact();
    }
}
