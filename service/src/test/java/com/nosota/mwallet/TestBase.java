package com.nosota.mwallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nosota.mwallet.model.OwnerType;
import com.nosota.mwallet.model.WalletType;
import com.nosota.mwallet.service.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.atomic.AtomicLong;

@SpringBootTest(
        classes = MwalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.cloud.config.enabled=false",
                "spring.cloud.config.discovery.enabled=false"}
)
@AutoConfigureMockMvc
@Testcontainers
@Import(TestAsyncConfig.class)
@ActiveProfiles("test")
public abstract class TestBase {
    protected static final DockerImageName DOCKER_IMAGE = DockerImageName.parse("postgres:16.6")
            .asCompatibleSubstituteFor("postgres");
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DOCKER_IMAGE);

    @Autowired
    protected WalletManagementService walletManagementService;

    @Autowired
    protected WalletBalanceService walletBalanceService;

    @Autowired
    protected TransactionService transactionService;

    @Autowired
    protected TransactionSnapshotService transactionSnapshotService;

    @Autowired
    protected WalletService walletService;

    @Autowired
    protected TransactionHistoryService transactionHistoryService;

    @Autowired
    protected TransactionStatisticService transactionStatisticService;

    @Autowired
    protected SystemStatisticService systemStatisticService;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // Counter for generating unique owner IDs in tests
    private static final AtomicLong ownerIdCounter = new AtomicLong(1);

    /**
     * Helper method to create a USER wallet with a generated owner ID.
     * Uses incrementing owner IDs (1, 2, 3, ...) for test purposes.
     */
    protected Integer createUserWallet(String description) {
        return walletManagementService.createNewWallet(
                WalletType.USER,
                description,
                ownerIdCounter.getAndIncrement(),
                OwnerType.USER_OWNER
        );
    }

    /**
     * Helper method to create a USER wallet with initial balance and generated owner ID.
     */
    protected Integer createUserWalletWithBalance(String description, Long initialBalance) {
        return walletManagementService.createNewWalletWithBalance(
                WalletType.USER,
                description,
                initialBalance,
                ownerIdCounter.getAndIncrement(),
                OwnerType.USER_OWNER
        );
    }

    /**
     * Helper method to create a SYSTEM wallet (system-owned, no individual owner).
     */
    protected Integer createSystemWallet(String description) {
        return walletManagementService.createSystemWallet(description);
    }

    /**
     * Helper method to create a SYSTEM wallet with initial balance.
     */
    protected Integer createSystemWalletWithBalance(String description, Long initialBalance) {
        return walletManagementService.createSystemWalletWithBalance(description, initialBalance);
    }

    /**
     * Helper method to create a MERCHANT wallet with a generated owner ID.
     */
    protected Integer createMerchantWallet(String description) {
        return walletManagementService.createNewWallet(
                WalletType.MERCHANT,
                description,
                ownerIdCounter.getAndIncrement(),
                OwnerType.MERCHANT_OWNER
        );
    }

    /**
     * Helper method to create a MERCHANT wallet with initial balance and generated owner ID.
     */
    protected Integer createMerchantWalletWithBalance(String description, Long initialBalance) {
        return walletManagementService.createNewWalletWithBalance(
                WalletType.MERCHANT,
                description,
                initialBalance,
                ownerIdCounter.getAndIncrement(),
                OwnerType.MERCHANT_OWNER
        );
    }
}
