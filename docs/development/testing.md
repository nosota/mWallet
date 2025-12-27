# Testing Guide

This guide covers testing strategies, patterns, and best practices for mWallet.

## Testing Philosophy

mWallet follows a comprehensive testing strategy:

1. **Unit Tests**: Test business logic in isolation
2. **Integration Tests**: Test with real database (Testcontainers)
3. **End-to-End Tests**: Test complete workflows

**Key Principles**:
- ✅ Tests must be fast, isolated, and repeatable
- ✅ Each test should test one concept
- ✅ Use meaningful test names
- ✅ Prefer real dependencies over mocks when practical
- ✅ Use Testcontainers for database integration tests

## Test Structure

### Directory Organization

```
src/test/java/com/nosota/mwallet/
├── TestBase.java                    # Base class for integration tests
├── TestAsyncConfig.java             # Async configuration for tests
└── tests/
    ├── BasicTests.java              # Core wallet and transaction tests
    ├── TransactionSnapshotTest.java # Snapshot creation tests
    ├── TransactionArchiveTest.java  # Archiving tests
    ├── TransactionHistoryTest.java  # History query tests
    ├── StatisticServiceTest.java    # Statistics service tests
    ├── SystemTests.java             # System-level tests
    └── TransferMoneyAsyncService.java # Async transfer helper
```

## Test Base Configuration

### TestBase Class

All integration tests extend `TestBase`:

```java
@SpringBootTest(
    classes = MwalletApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Testcontainers
@Import(TestAsyncConfig.class)
@ActiveProfiles("test")
public abstract class TestBase {

    protected static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.6"));

    @Autowired
    protected WalletManagementService walletManagementService;

    @Autowired
    protected WalletBalanceService walletBalanceService;

    // ... other service injections

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
```

**Key Features**:
- **Testcontainers**: Spins up real PostgreSQL in Docker
- **Spring Boot Test**: Full application context
- **Service Injection**: All services available for testing
- **Shared Container**: Single PostgreSQL instance for all tests (performance)

## Running Tests

### All Tests

```bash
cd service
mvn test
```

### Specific Test Class

```bash
mvn test -Dtest=BasicTests
```

### Specific Test Method

```bash
mvn test -Dtest=BasicTests#testCreateWalletWithInitialBalance
```

### With Coverage

```bash
mvn clean test jacoco:report
# Report in: target/site/jacoco/index.html
```

### Skip Tests

```bash
mvn package -DskipTests
```

## Test Categories

### 1. Basic Wallet and Transaction Tests

**File**: `BasicTests.java`

**Coverage**:
- Wallet creation with and without initial balance
- HOLD, RESERVE, CONFIRM, REJECT operations
- Balance calculations (available, hold, reserved)
- Transaction group lifecycle
- Two-wallet transfers

**Example Test**:

```java
@Test
void testCreateWalletWithInitialBalance() {
    // Given
    Long initialBalance = 10000L; // 100.00 in cents

    // When
    Integer walletId = walletManagementService
        .createNewWalletWithBalance(WalletType.USER, "Test wallet", initialBalance);

    // Then
    assertThat(walletId).isNotNull();

    Long balance = walletBalanceService.getAvailableBalance(walletId);
    assertThat(balance).isEqualTo(initialBalance);
}

@Test
void testHoldAndConfirm() {
    // Given
    Integer walletId = walletManagementService
        .createNewWalletWithBalance(WalletType.USER, null, 10000L);
    UUID groupId = transactionService.createTransactionGroup();

    // When
    walletService.hold(walletId, 1000L, groupId);
    Long balanceAfterHold = walletBalanceService.getAvailableBalance(walletId);

    walletService.confirm(walletId, groupId);
    Long balanceAfterConfirm = walletBalanceService.getAvailableBalance(walletId);

    // Then
    assertThat(balanceAfterHold).isEqualTo(9000L); // 10000 - 1000 held
    assertThat(balanceAfterConfirm).isEqualTo(9000L); // 10000 - 1000 confirmed
}
```

### 2. Transaction Snapshot Tests

**File**: `TransactionSnapshotTest.java`

**Coverage**:
- Daily snapshot creation
- Transaction migration from active to snapshot
- Verification counts
- IN_PROGRESS group exclusion

**Example Test**:

```java
@Test
void testCaptureDailySnapshot() {
    // Given
    Integer walletId = createWalletWithTransactions();
    UUID groupId = transactionService.createTransactionGroup();
    walletService.hold(walletId, 1000L, groupId);
    walletService.confirm(walletId, groupId);
    transactionService.confirmTransactionGroup(groupId);

    // When
    transactionSnapshotService.captureDailySnapshotForWallet(walletId);

    // Then
    List<Transaction> activeTransactions =
        transactionRepository.findAllByWalletId(walletId);
    List<TransactionSnapshot> snapshots =
        snapshotRepository.findAllByWalletId(walletId);

    assertThat(activeTransactions)
        .isEmpty(); // All moved to snapshots
    assertThat(snapshots)
        .isNotEmpty(); // Snapshots created
}

@Test
void testSnapshotDoesNotMoveInProgressGroups() {
    // Given
    Integer walletId = createWallet();
    UUID groupId = transactionService.createTransactionGroup();
    walletService.hold(walletId, 1000L, groupId);
    // Group NOT confirmed

    // When
    transactionSnapshotService.captureDailySnapshotForWallet(walletId);

    // Then
    List<Transaction> activeTransactions =
        transactionRepository.findAllByWalletId(walletId);

    assertThat(activeTransactions)
        .isNotEmpty(); // IN_PROGRESS transactions preserved
}
```

### 3. Transaction Archive Tests

**File**: `TransactionArchiveTest.java`

**Coverage**:
- Archiving old snapshots
- Ledger entry creation
- Ledger tracking table population
- Balance accuracy after archiving
- Verification counts

**Example Test**:

```java
@Test
void testArchiveOldSnapshots() {
    // Given
    Integer walletId = createWalletWithSnapshots();
    LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(1);

    Long balanceBefore = walletBalanceService.getAvailableBalance(walletId);

    // When
    transactionSnapshotService.archiveOldSnapshots(walletId, cutoffDate);

    // Then
    Long balanceAfter = walletBalanceService.getAvailableBalance(walletId);
    assertThat(balanceAfter).isEqualTo(balanceBefore); // Balance unchanged

    // Verify ledger entry created
    List<TransactionSnapshot> ledgerEntries = snapshotRepository
        .findByWalletIdAndIsLedgerEntry(walletId, true);
    assertThat(ledgerEntries).isNotEmpty();

    // Verify old snapshots moved to archive
    long archiveCount = archiveRepository
        .countByWalletIdAndTimestampBefore(walletId, cutoffDate);
    assertThat(archiveCount).isGreaterThan(0);
}

@Test
void testLedgerTrackingCreated() {
    // Given
    Integer walletId = createWalletWithSnapshots();
    LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(1);

    // When
    transactionSnapshotService.archiveOldSnapshots(walletId, cutoffDate);

    // Then
    List<TransactionSnapshot> ledgerEntries = snapshotRepository
        .findByWalletIdAndIsLedgerEntry(walletId, true);
    TransactionSnapshot ledger = ledgerEntries.get(0);

    List<LedgerEntriesTracking> tracking = ledgerTrackingRepository
        .findByLedgerEntryId(ledger.getId());

    assertThat(tracking).isNotEmpty();
}
```

### 4. Transaction History Tests

**File**: `TransactionHistoryTest.java`

**Coverage**:
- Full transaction history across all tiers
- Paginated transaction history
- Filtering by type and status
- Correct ordering

**Example Test**:

```java
@Test
void testFullTransactionHistory() {
    // Given
    Integer walletId = createWalletWithHistory();

    // When
    List<TransactionHistoryDTO> history =
        transactionHistoryService.getFullTransactionHistory(walletId);

    // Then
    assertThat(history).isNotEmpty();
    assertThat(history).isSortedAccordingTo(
        Comparator.comparing(TransactionHistoryDTO::getTimestamp).reversed()
    );
}

@Test
void testPaginatedHistory() {
    // Given
    Integer walletId = createWalletWithManyTransactions(100);

    // When
    PagedResponse<TransactionHistoryDTO> page1 =
        transactionHistoryService.getPaginatedTransactionHistory(
            walletId, 1, 10,
            Collections.emptyList(), Collections.emptyList()
        );

    // Then
    assertThat(page1.getData()).hasSize(10);
    assertThat(page1.getTotalRecords()).isEqualTo(100);
    assertThat(page1.getTotalPages()).isEqualTo(10);
}

@Test
void testFilteredHistory() {
    // Given
    Integer walletId = createWalletWithMixedTransactions();
    List<String> typeFilters = List.of("CREDIT");
    List<String> statusFilters = List.of("CONFIRMED");

    // When
    PagedResponse<TransactionHistoryDTO> filtered =
        transactionHistoryService.getPaginatedTransactionHistory(
            walletId, 1, 100, typeFilters, statusFilters
        );

    // Then
    assertThat(filtered.getData())
        .allMatch(t -> t.getType().equals("CREDIT"))
        .allMatch(t -> t.getStatus().equals("CONFIRMED"));
}
```

### 5. Statistics Service Tests

**File**: `StatisticServiceTest.java`

**Coverage**:
- Daily credit/debit operations
- Date range queries
- Merging data from multiple tiers
- CONFIRMED transactions only

**Example Test**:

```java
@Test
void testGetDailyCreditOperations() {
    // Given
    Integer walletId = createWallet();
    LocalDateTime today = LocalDateTime.now();

    createConfirmedCredit(walletId, 1000L, today);
    createConfirmedCredit(walletId, 2000L, today);
    createConfirmedDebit(walletId, 500L, today); // Should be excluded

    // When
    List<TransactionDTO> credits =
        transactionStatisticService.getDailyCreditOperations(walletId, today);

    // Then
    assertThat(credits).hasSize(2);
    assertThat(credits).allMatch(t -> t.getType() == TransactionType.CREDIT);
    assertThat(credits).allMatch(t -> t.getStatus() == TransactionStatus.CONFIRMED);

    long totalAmount = credits.stream()
        .mapToLong(TransactionDTO::getAmount)
        .sum();
    assertThat(totalAmount).isEqualTo(3000L);
}

@Test
void testGetCreditOperationsInRange() {
    // Given
    Integer walletId = createWallet();
    LocalDateTime start = LocalDateTime.now().minusDays(7);
    LocalDateTime end = LocalDateTime.now();

    createCreditsOverDateRange(walletId, start, end, 10);

    // When
    List<TransactionDTO> credits =
        transactionStatisticService.getCreditOperationsInRange(walletId, start, end);

    // Then
    assertThat(credits).hasSize(10);
}
```

### 6. System Tests

**File**: `SystemTests.java`

**Coverage**:
- System-wide reconciliation balance
- Zero-sum validation across all transaction groups
- Multi-wallet transfers

**Example Test**:

```java
@Test
void testSystemReconciliationBalance() {
    // Given
    Integer wallet1 = walletManagementService
        .createNewWalletWithBalance(WalletType.USER, null, 10000L);
    Integer wallet2 = walletManagementService
        .createNewWalletWithBalance(WalletType.USER, null, 5000L);

    // Initial balance = 15000L

    // When
    transactionService.transferBetweenTwoWallets(wallet1, wallet2, 1000L);

    Long reconciliation = systemStatisticService
        .getReconciliationBalanceOfAllConfirmedGroups();

    // Then
    assertThat(reconciliation).isEqualTo(15000L);
    // Internal transfers don't change system balance
}

@Test
void testZeroSumEnforcement() {
    // Given
    Integer wallet1 = createWalletWithBalance(10000L);
    Integer wallet2 = createWallet();
    UUID groupId = transactionService.createTransactionGroup();

    walletService.hold(wallet1, 1000L, groupId);
    walletService.reserve(wallet2, 900L, groupId); // Unbalanced!

    // When/Then
    assertThatThrownBy(() ->
        transactionService.confirmTransactionGroup(groupId)
    ).isInstanceOf(TransactionGroupZeroingOutException.class);
}
```

## Testing Patterns

### Pattern 1: Given-When-Then

Structure tests clearly:

```java
@Test
void testTransferBetweenTwoWallets() {
    // Given - Setup
    Integer sender = createWalletWithBalance(10000L);
    Integer recipient = createWallet();
    Long transferAmount = 1000L;

    // When - Execute
    UUID groupId = transactionService.transferBetweenTwoWallets(
        sender, recipient, transferAmount
    );

    // Then - Verify
    assertThat(walletBalanceService.getAvailableBalance(sender))
        .isEqualTo(9000L);
    assertThat(walletBalanceService.getAvailableBalance(recipient))
        .isEqualTo(1000L);

    TransactionGroupStatus status =
        transactionService.getStatusForReferenceId(groupId);
    assertThat(status).isEqualTo(TransactionGroupStatus.CONFIRMED);
}
```

### Pattern 2: Test One Concept

Each test should verify one behavior:

```java
// Good - Tests one concept
@Test
void testHoldReducesAvailableBalance() {
    Integer walletId = createWalletWithBalance(10000L);
    UUID groupId = transactionService.createTransactionGroup();

    walletService.hold(walletId, 1000L, groupId);

    assertThat(walletBalanceService.getAvailableBalance(walletId))
        .isEqualTo(9000L);
}

// Bad - Tests multiple concepts
@Test
void testHoldConfirmRejectAndBalance() {
    // Tests too many things at once
}
```

### Pattern 3: Helper Methods

Extract setup logic:

```java
private Integer createWalletWithBalance(Long balance) {
    return walletManagementService.createNewWalletWithBalance(
        WalletType.USER, "Test wallet", balance
    );
}

private UUID createConfirmedTransfer(Integer from, Integer to, Long amount) {
    UUID groupId = transactionService.createTransactionGroup();
    walletService.hold(from, amount, groupId);
    walletService.reserve(to, amount, groupId);
    transactionService.confirmTransactionGroup(groupId);
    return groupId;
}
```

### Pattern 4: Meaningful Assertions

Use AssertJ for readable assertions:

```java
// Good
assertThat(balance).isEqualTo(10000L);
assertThat(transactions).hasSize(5);
assertThat(transactions).allMatch(t -> t.getStatus() == CONFIRMED);
assertThat(history).isSortedAccordingTo(
    Comparator.comparing(TransactionHistoryDTO::getTimestamp).reversed()
);

// Less readable
assertTrue(balance == 10000L);
assertEquals(5, transactions.size());
```

## Test Data Management

### Testcontainers Database Lifecycle

Each test class uses a shared PostgreSQL container:
- Container starts before all tests
- Database reset between test methods
- Container stops after all tests

**Isolation**:
- Each test method runs in a transaction (if using `@Transactional`)
- Database state is clean for each test
- Tests can run in any order

### Creating Test Data

```java
protected Integer createWalletWithHistory() {
    Integer walletId = walletManagementService
        .createNewWalletWithBalance(WalletType.USER, null, 10000L);

    // Create multiple transactions
    for (int i = 0; i < 10; i++) {
        UUID groupId = transactionService.createTransactionGroup();
        walletService.hold(walletId, 100L, groupId);
        walletService.confirm(walletId, groupId);
        transactionService.confirmTransactionGroup(groupId);
    }

    // Move to snapshots
    transactionSnapshotService.captureDailySnapshotForWallet(walletId);

    return walletId;
}
```

## Assertions and Verification

### Balance Assertions

```java
// Available balance
Long available = walletBalanceService.getAvailableBalance(walletId);
assertThat(available).isEqualTo(expectedBalance);

// Hold balance
Long hold = walletBalanceService.getHoldBalanceForWallet(walletId);
assertThat(hold).isZero();

// Reserved balance
Long reserved = walletBalanceService.getReservedBalanceForWallet(walletId);
assertThat(reserved).isZero();
```

### Transaction Assertions

```java
List<TransactionDTO> transactions =
    transactionService.getTransactionsByReferenceId(groupId);

assertThat(transactions)
    .hasSize(2)
    .allMatch(t -> t.getReferenceId().equals(groupId))
    .anyMatch(t -> t.getType() == TransactionType.DEBIT)
    .anyMatch(t -> t.getType() == TransactionType.CREDIT);
```

### Exception Assertions

```java
assertThatThrownBy(() ->
    walletService.hold(walletId, 99999L, groupId)
).isInstanceOf(InsufficientFundsException.class)
 .hasMessageContaining("Insufficient funds");
```

## Test Coverage Goals

### Coverage Targets

- **Service Layer**: 90%+ coverage
- **Repository Layer**: 80%+ (custom queries)
- **Overall**: 85%+

### Running Coverage Report

```bash
mvn clean test jacoco:report
open target/site/jacoco/index.html
```

### What to Test

✅ **Must Test**:
- Business logic in services
- Custom repository queries
- Balance calculations
- Transaction lifecycle
- Error handling
- Zero-sum validation

❌ **Skip Testing**:
- Simple getters/setters (Lombok-generated)
- Spring configuration classes
- Entity classes (unless complex logic)
- Generated MapStruct implementations

## Best Practices

### DO:
- ✅ Use meaningful test names: `testHoldReducesAvailableBalance`
- ✅ Test one concept per test method
- ✅ Use Given-When-Then structure
- ✅ Extract common setup to helper methods
- ✅ Use AssertJ for readable assertions
- ✅ Test edge cases and error conditions
- ✅ Keep tests fast (use Testcontainers efficiently)

### DON'T:
- ❌ Test multiple concepts in one test
- ❌ Use magic numbers (define constants)
- ❌ Depend on test execution order
- ❌ Use `Thread.sleep()` for async operations
- ❌ Leave commented-out test code
- ❌ Ignore failing tests

## Continuous Integration

### GitHub Actions Example

```yaml
name: Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 23
        uses: actions/setup-java@v3
        with:
          java-version: '23'
          distribution: 'temurin'

      - name: Run tests
        run: mvn clean test

      - name: Generate coverage report
        run: mvn jacoco:report

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
```

## Troubleshooting Tests

### Testcontainers Issues

**Problem**: `Could not find a valid Docker environment`

**Solution**:
```bash
# Ensure Docker is running
docker ps

# Check Testcontainers config
export TESTCONTAINERS_RYUK_DISABLED=false
```

### Slow Tests

**Problem**: Tests take too long

**Solutions**:
1. Use shared Testcontainers container (already done in `TestBase`)
2. Avoid unnecessary `@SpringBootTest` (use `@DataJpaTest` for repository tests)
3. Mock external dependencies
4. Use in-memory database for unit tests (if applicable)

### Flaky Tests

**Problem**: Tests pass/fail intermittently

**Common Causes**:
- Timing issues (avoid `Thread.sleep()`)
- Shared state between tests
- Test execution order dependency

**Solutions**:
- Ensure test isolation
- Use `@DirtiesContext` if needed (sparingly)
- Fix timing issues properly (use await conditions)

## Related Documentation

- [Development Setup](./setup.md) - Environment setup
- [Architecture Overview](../architecture/overview.md) - System architecture
- [Service API Reference](../api/services.md) - Service layer documentation
