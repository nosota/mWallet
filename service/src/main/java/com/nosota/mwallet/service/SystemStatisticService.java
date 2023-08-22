package com.nosota.mwallet.service;

import com.nosota.mwallet.repository.SystemStatisticRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Service
@Validated
public class SystemStatisticService
{
    private final SystemStatisticRepository systemStatisticRepository;

    public SystemStatisticService(SystemStatisticRepository systemStatisticRepository) {
        this.systemStatisticRepository = systemStatisticRepository;
    }

    /**
     * Retrieves the reconciliation balance for the entire system.
     *
     * <p>The resulting balance represents the cumulative initial balance
     * of all wallets created within the system. This initial balance might
     * signify funds external to the system or the system's startup balance.
     * The exact interpretation is contingent on the specific business
     * rules and logic of systems built upon this wallet infrastructure.</p>
     *
     * <p>Notably, internal money transfers between wallets should not
     * affect the reconciliation balance, ensuring its constancy
     * throughout the system's lifecycle. </p>
     *
     * <p> NOTE: Since the function processes transactions in snapshot and in archive, it might take time! </p>
     *
     * @return
     */
    @Transactional
    public Long getReconciliationBalanceOfAllConfirmedGroups() {
        BigDecimal result = systemStatisticRepository.calculateReconciliationBalanceOfAllConfirmedGroups();
        result = result != null ? result : BigDecimal.ZERO;
        return result.longValueExact();
    }
}
