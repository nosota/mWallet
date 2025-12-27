package com.nosota.mwallet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nosota.mwallet.service.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

    @BeforeAll
    static void startPostgresContainer() {
        postgres.start();
        System.setProperty("JDBC_URL", postgres.getJdbcUrl());
        System.setProperty("JDBC_USER", postgres.getUsername());
        System.setProperty("JDBC_PASSWORD", postgres.getPassword());
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }
}
