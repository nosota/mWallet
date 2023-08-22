package com.nosota.mwallet;
import com.nosota.mwallet.service.SystemStatisticService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SystemTests {

    @Autowired
    private SystemStatisticService systemStatisticService;

    @Test
    void contextLoads() {
    }

    @Test
    public void getSystemReconciliationAmount() {
        Long reconciliationAmount = systemStatisticService.getReconciliationBalanceOfAllConfirmedGroups();
        System.out.println("System reconciliation amount is " + reconciliationAmount);
    }
}
