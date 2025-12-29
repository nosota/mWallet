# Testing Guide

## Overview

mWallet uses a comprehensive testing strategy with different test types for different purposes.

## Test Structure

```
src/test/java/
├── com.nosota.mwallet/
│   ├── TestBase.java                    # Base class for all tests
│   └── tests/
│       ├── BasicTests.java              # Integration tests for LedgerApi
│       ├── PaymentControllerTest.java   # Integration tests for PaymentApi
│       ├── StatisticServiceTest.java    # Service-level tests
│       ├── TransactionHistoryTest.java  # Service-level tests
│       ├── TransactionSnapshotTest.java # Service-level tests
│       └── TransactionArchiveTest.java  # Service-level tests
```

## Test Types

### 1. Integration Tests (via MockMvc)

**Purpose**: Test REST API endpoints through the controller layer

**When to use**: Testing API contracts and end-to-end flows

**Examples**: `BasicTests.java`, `PaymentControllerTest.java`

**Structure**:
```java
@SpringBootTest
@AutoConfigureMockMvc
public class PaymentControllerTest extends TestBase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void executeSettlement_Success() throws Exception {
        // Setup test data
        // ...

        // Execute via REST API
        MvcResult result = mockMvc.perform(
            post("/api/v1/payment/settlement/merchants/{id}/execute", merchantId)
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isCreated())
        .andReturn();

        // Verify response
        String responseJson = result.getResponse().getContentAsString();
        SettlementResponse response = objectMapper.readValue(
            responseJson, SettlementResponse.class);

        assertThat(response.status()).isEqualTo("EXECUTED");
    }
}
```

**Key Points**:
- Use `MockMvc` to simulate HTTP requests
- Test through controller layer (full Spring context)
- Verify HTTP status codes and response bodies
- Use Testcontainers for real PostgreSQL database

### 2. Service-Level Tests

**Purpose**: Test internal service logic without HTTP layer

**When to use**: Testing internal services with no REST endpoints

**Examples**: `StatisticServiceTest.java`, `TransactionHistoryTest.java`

**Structure**:
```java
@SpringBootTest
public class StatisticServiceTest extends TestBase {

    @Test
    public void dailyStat() throws Exception {
        // Create test data via service
        Integer wallet1Id = createUserWalletWithBalance("test", 10000L);

        // Call service directly
        List<TransactionDTO> credits = transactionStatisticService
            .getDailyCreditOperations(wallet1Id, LocalDateTime.now());

        // Verify results
        assertThat(credits).hasSize(1);
    }
}
```

**Key Points**:
- Call services directly (no MockMvc)
- Test internal logic and algorithms
- Useful for services without REST endpoints
- Faster than integration tests

## TestBase Class

All tests extend `TestBase` which provides:

### Test Infrastructure

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class TestBase {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16.6");

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    // Injected services for helper methods
    @Autowired protected WalletManagementService walletManagementService;
    @Autowired protected WalletBalanceService walletBalanceService;
    @Autowired protected TransactionService transactionService;
    // ... other services
}
```

### Helper Methods

**Wallet Creation**:
```java
protected Integer createUserWallet(String description);
protected Integer createUserWalletWithBalance(String description, Long initialBalance);
protected Integer createMerchantWallet(String description);
protected Integer createMerchantWalletWithBalance(String description, Long initialBalance);
protected Integer createSystemWallet(String description);
protected Integer createSystemWalletWithBalance(String description, Long initialBalance);
```

**Usage**:
```java
@Test
public void testTransfer() throws Exception {
    Integer buyerWallet = createUserWalletWithBalance("buyer", 10000L);
    Integer merchantWallet = createMerchantWallet("merchant");

    // Use wallets in test
    // ...
}
```

## Test Configuration

### application.yaml (Test Profile)

Location: `src/test/resources/application.yaml`

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
    url: ${JDBC_URL}        # Set by Testcontainers
    username: ${JDBC_USER}
    password: ${JDBC_PASSWORD}

# Settlement configuration for tests
settlement:
  commission-rate: 0.03
  min-amount: 0
  hold-age-days: 0

# Refund configuration for tests
refund:
  return-commission-to-buyer: false
  partial-refund-enabled: true
  multiple-refunds-enabled: true
  max-days-after-settlement: 90
  require-settled-status: true
  allow-negative-balance: false
  auto-execute-pending: true
  pending-funds-expiry-days: 30
```

## Test Scenarios

### Positive Scenarios

Test that operations succeed when conditions are met:

```java
@Test
public void executeSettlement_WithValidData_ShouldSucceed() throws Exception {
    // Setup: Create wallets, perform transactions
    // Execute: Call API
    // Verify: Check response, balances, database state
}
```

### Negative Scenarios

Test that operations fail gracefully with appropriate errors:

```java
@Test
public void executeSettlement_WithNoTransactions_ShouldFail() throws Exception {
    mockMvc.perform(
        post("/api/v1/payment/settlement/merchants/{id}/execute", merchantId)
            .contentType(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isConflict()); // 409 - IllegalStateException
}

@Test
public void getSettlement_WhenNotFound_ShouldReturn404() throws Exception {
    UUID randomId = UUID.randomUUID();

    mockMvc.perform(
        get("/api/v1/payment/settlement/{id}", randomId)
            .contentType(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isNotFound()); // 404 - EntityNotFoundException
}
```

## Test Coverage Guidelines

### What to Test

1. **Happy Path**: Normal successful operations
2. **Validation**: Invalid inputs rejected with 400 Bad Request
3. **Not Found**: Missing entities return 404 Not Found
4. **Conflict**: Business rule violations return 409 Conflict
5. **Edge Cases**: Boundary conditions, empty results
6. **State Transitions**: Valid and invalid state changes
7. **Concurrency**: Race conditions (where applicable)

### What NOT to Test

1. **Framework Behavior**: Don't test Spring Boot internals
2. **Third-Party Libraries**: Don't test Hibernate, Jackson, etc.
3. **Trivial Code**: Getters/setters, simple constructors
4. **Private Methods**: Test through public interface

## Example Test Coverage

### BasicTests.java (LedgerApi)

Tests ledger operations via MockMvc:

```java
✓ createWallets                            # Wallet creation
✓ transferMoney2Positive                   # Simple 2-wallet transfer
✓ transferMoney3Negative                   # Insufficient funds handling
✓ transferMoney3Positive                   # 3-wallet group transfer
✓ transferMoney3ReconciliationError        # Unbalanced group detection
✓ transferMoneyAndSnapshot                 # Snapshot system
✓ transferMoney3PositiveAndSnapshot        # Snapshot with groups
✓ transferMoney3PositiveAndSnapshotAndArchive # Full lifecycle
```

### PaymentControllerTest.java (PaymentApi)

Tests payment operations via MockMvc:

```java
// Settlement Tests
✓ calculateSettlement_WhenNoTransactions_ShouldFail
✓ executeSettlement_WhenNoTransactions_ShouldFail
✓ getSettlement_WhenNotFound_ShouldReturn404
✓ getSettlementHistory_WhenNoData_ShouldReturnEmptyList

// Refund Tests
✓ createRefund_WhenNoSettlement_ShouldFail
✓ getRefund_WhenNotFound_ShouldReturn404
✓ getRefundHistory_WhenNoData_ShouldReturnEmptyList
✓ getRefundsByOrder_WhenNoRefunds_ShouldReturnEmptyList
```

## Running Tests

### Run All Tests

```bash
mvn test
```

### Run Specific Test Class

```bash
mvn test -Dtest=BasicTests
mvn test -Dtest=PaymentControllerTest
```

### Run Specific Test Method

```bash
mvn test -Dtest=BasicTests#transferMoney3Positive
```

### Run with Testcontainers

Tests automatically start PostgreSQL container:

```
[INFO] tc.postgres:16.6 -- Creating container for image: postgres:16.6
[INFO] tc.postgres:16.6 -- Container postgres:16.6 is starting
[INFO] tc.postgres:16.6 -- Container started in PT1.775049S
```

## Best Practices

### 1. Test Isolation

Each test should be independent:
```java
@BeforeEach
public void setup() {
    // Create fresh test data
}

@AfterEach
public void cleanup() {
    // Clean up if needed (usually not needed with Testcontainers)
}
```

### 2. Descriptive Test Names

```java
// Good
@Test
public void executeSettlement_WithInsufficientEscrowFunds_ShouldFail()

// Bad
@Test
public void test1()
```

### 3. AAA Pattern

Arrange-Act-Assert:
```java
@Test
public void test() {
    // ARRANGE: Setup test data
    Integer walletId = createUserWalletWithBalance("test", 1000L);

    // ACT: Execute operation
    MvcResult result = mockMvc.perform(...)
        .andReturn();

    // ASSERT: Verify results
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
}
```

### 4. Use AssertJ for Fluent Assertions

```java
// Good - fluent and readable
assertThat(balance).isEqualTo(1000L);
assertThat(transactions).hasSize(3);
assertThat(status).isIn("EXECUTED", "PENDING");

// Avoid - JUnit assertions are less fluent
assertEquals(1000L, balance);
assertEquals(3, transactions.size());
```

### 5. Test Data Naming

Use descriptive names for test data:
```java
Integer buyerWallet = createUserWalletWithBalance("test-buyer", 10000L);
Integer merchantWallet = createMerchantWallet("test-merchant");
Integer escrowWallet = createEscrowWallet("test-escrow");
```

## Troubleshooting

### Connection Timeout Issues

If tests fail with connection timeouts:
```
HikariPool-1 - Connection is not available, request timed out after 30005ms
```

**Solutions**:
1. Increase connection pool size in test config
2. Reduce parallel test execution
3. Check for connection leaks (unclosed transactions)

### Testcontainers Issues

If Docker container fails to start:
```
Could not find a valid Docker environment
```

**Solutions**:
1. Ensure Docker is running: `docker ps`
2. Check Docker socket: `ls -l /var/run/docker.sock`
3. Verify Testcontainers can access Docker

### Flaky Tests

If tests pass/fail randomly:
1. Check for time-dependent code (use fixed times in tests)
2. Verify test isolation (no shared state between tests)
3. Look for race conditions (add proper synchronization)

## Related Documentation

- [Architecture Overview](./architecture.md)
- [API Design](./api-design.md)
- [Exception Handling](./exception-handling.md)
