package com.nosota.mwallet.tests;
import com.nosota.mwallet.MwalletApplication;
import com.nosota.mwallet.container.PostgresContainer;
import com.nosota.mwallet.service.SystemStatisticService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        classes = MwalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.main.allow-bean-definition-overriding=true",
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.discovery.enabled=false"}
)
@Testcontainers
@ActiveProfiles("test")
class SystemTests {

    @Autowired
    private SystemStatisticService systemStatisticService;

    @BeforeAll
    public static void startPostgresContainer() {
        PostgresContainer.getInstance();
    }

    @Test
    public void getSystemReconciliationAmount() {
        Long reconciliationAmount = systemStatisticService.getReconciliationBalanceOfAllConfirmedGroups();
        System.out.println("System reconciliation amount is " + reconciliationAmount);
    }
}
