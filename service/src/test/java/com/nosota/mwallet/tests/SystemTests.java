package com.nosota.mwallet.tests;

import com.nosota.mwallet.TestBase;
import org.junit.jupiter.api.Test;

class SystemTests extends TestBase {

    @Test
    public void getSystemReconciliationAmount() {
        Long reconciliationAmount = systemStatisticService.getReconciliationBalanceOfAllFinalizedGroups();
        System.out.println("System reconciliation amount is " + reconciliationAmount);
    }
}
