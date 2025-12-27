# Service API Reference

This document provides a complete reference for all service layer APIs in mWallet.

## Overview

The service layer is organized into focused services, each handling a specific domain:

| Service | Purpose |
|---------|---------|
| [WalletManagementService](#walletmanagementservice) | Wallet lifecycle management |
| [WalletService](#walletservice) | Low-level transaction operations (HOLD, RESERVE, CONFIRM, REJECT) |
| [TransactionService](#transactionservice) | Transaction group orchestration and transfers |
| [WalletBalanceService](#walletbalanceservice) | Balance queries and calculations |
| [TransactionSnapshotService](#transactionsnapshotservice) | Snapshot and archiving operations |
| [TransactionHistoryService](#transactionhistoryservice) | Transaction history queries |
| [TransactionStatisticService](#transactionstatisticservice) | Statistical queries |
| [SystemStatisticService](#systemstatisticservice) | System-wide statistics |
| [WalletOwnershipService](#walletownershipservice) | Wallet ownership management |

---

## WalletManagementService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Wallet lifecycle management

### Methods

#### createNewWallet

Creates a new empty wallet.

**Signature**:
```java
Integer createNewWallet(
    @NotNull WalletType type,
    String description
)
```

**Parameters**:
- `type` - Wallet type (USER, FEE, SYSTEM)
- `description` - Optional wallet description

**Returns**: Wallet ID

**Transaction**: Yes

**Example**:
```java
Integer walletId = walletManagementService.createNewWallet(
    WalletType.USER,
    "John Doe's wallet"
);
```

---

#### createNewWalletWithBalance

Creates a new wallet with an initial balance.

**Signature**:
```java
Integer createNewWalletWithBalance(
    @NotNull WalletType type,
    String description,
    @Positive Long initialBalance
)
```

**Parameters**:
- `type` - Wallet type (USER, FEE, SYSTEM)
- `description` - Optional wallet description
- `initialBalance` - Initial balance in cents (must be positive)

**Returns**: Wallet ID

**Transaction**: Yes

**Behavior**:
- If `initialBalance > 0`:
  - Creates wallet
  - Creates CONFIRMED CREDIT transaction
  - Creates CONFIRMED transaction group
  - Links transaction to group

**Example**:
```java
Integer walletId = walletManagementService.createNewWalletWithBalance(
    WalletType.USER,
    "John Doe's wallet",
    50000L  // $500.00
);

Long balance = walletBalanceService.getAvailableBalance(walletId);
// balance = 50000L
```

---

## WalletService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Low-level transaction operations (Phase 1 and Phase 2)

### Methods

#### hold

Holds funds for a pending debit operation.

**Signature**:
```java
Integer hold(
    @NotNull Integer walletId,
    @Positive Long amount,
    @NotNull UUID referenceId
)
```

**Parameters**:
- `walletId` - Wallet ID
- `amount` - Amount to hold in cents (must be positive)
- `referenceId` - Transaction group UUID

**Returns**: Transaction ID

**Transaction**: Yes

**Exceptions**:
- `WalletNotFoundException` - Wallet not found
- `InsufficientFundsException` - Not enough available balance

**Behavior**:
- Checks available balance
- Creates transaction with:
  - `status = HOLD`
  - `type = DEBIT`
  - `amount = -amount` (negative)
  - `hold_reserve_timestamp = NOW()`

**Example**:
```java
UUID groupId = transactionService.createTransactionGroup();

Integer txId = walletService.hold(walletId, 1000L, groupId);
// Available balance reduced by 1000 cents
```

---

#### reserve

Reserves space for a pending credit operation.

**Signature**:
```java
Integer reserve(
    @NotNull Integer walletId,
    @Positive Long amount,
    @NotNull UUID referenceId
)
```

**Parameters**:
- `walletId` - Wallet ID
- `amount` - Amount to reserve in cents (must be positive)
- `referenceId` - Transaction group UUID

**Returns**: Transaction ID

**Transaction**: Yes

**Exceptions**:
- `WalletNotFoundException` - Wallet not found

**Behavior**:
- No balance check (incoming funds)
- Creates transaction with:
  - `status = RESERVE`
  - `type = CREDIT`
  - `amount = amount` (positive)
  - `hold_reserve_timestamp = NOW()`

**Example**:
```java
UUID groupId = transactionService.createTransactionGroup();

Integer txId = walletService.reserve(walletId, 1000L, groupId);
// Funds not yet available (RESERVE state)
```

---

#### confirm

Confirms a held or reserved transaction.

**Signature**:
```java
Integer confirm(
    @NotNull Integer walletId,
    @NotNull UUID referenceId
)
```

**Parameters**:
- `walletId` - Wallet ID
- `referenceId` - Transaction group UUID

**Returns**: New transaction ID (CONFIRMED)

**Transaction**: Yes

**Exceptions**:
- `TransactionNotFoundException` - No HOLD or RESERVE transaction found

**Behavior**:
- Finds HOLD or RESERVE transaction
- Creates new transaction with:
  - `status = CONFIRMED`
  - `type = <same as original>`
  - `amount = <same as original>`
  - `confirm_reject_timestamp = NOW()`
- Original transaction remains (audit trail)

**Example**:
```java
walletService.hold(wallet1, 1000L, groupId);
walletService.reserve(wallet2, 1000L, groupId);

// Later...
walletService.confirm(wallet1, groupId);
walletService.confirm(wallet2, groupId);
// Transactions now CONFIRMED
```

---

#### reject

Rejects/cancels a held or reserved transaction.

**Signature**:
```java
Integer reject(
    @NotNull Integer walletId,
    @NotNull UUID referenceId
)
```

**Parameters**:
- `walletId` - Wallet ID
- `referenceId` - Transaction group UUID

**Returns**: New transaction ID (REJECTED)

**Transaction**: Yes

**Exceptions**:
- `TransactionNotFoundException` - No HOLD or RESERVE transaction found

**Behavior**:
- Finds HOLD or RESERVE transaction
- Creates new transaction with:
  - `status = REJECTED`
  - `type = <same as original>`
  - `amount = <same as original>`
  - `confirm_reject_timestamp = NOW()`
- Original transaction remains (audit trail)

**Example**:
```java
try {
    walletService.hold(wallet1, 1000L, groupId);
    // Some error occurs...
    throw new RuntimeException("Error");
} catch (Exception e) {
    walletService.reject(wallet1, groupId);
    // Funds released back to available balance
}
```

---

## TransactionService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Transaction group orchestration and multi-wallet operations

### Methods

#### createTransactionGroup

Creates a new transaction group.

**Signature**:
```java
UUID createTransactionGroup()
```

**Returns**: Transaction group UUID

**Transaction**: Yes

**Example**:
```java
UUID groupId = transactionService.createTransactionGroup();
// Group status = IN_PROGRESS
```

---

#### confirmTransactionGroup

Confirms all transactions in a group atomically.

**Signature**:
```java
void confirmTransactionGroup(@NotNull UUID referenceId)
```

**Parameters**:
- `referenceId` - Transaction group UUID

**Transaction**: Yes

**Exceptions**:
- `TransactionNotFoundException` - Group not found
- `TransactionGroupZeroingOutException` - Group doesn't balance to zero

**Behavior**:
1. Validates group exists
2. **Zero-sum check**: `SUM(HOLD + RESERVE amounts) = 0`
3. For each transaction (descending order):
   - Calls `walletService.confirm()`
4. Updates group status to CONFIRMED

**Example**:
```java
UUID groupId = transactionService.createTransactionGroup();
walletService.hold(sender, 1000L, groupId);
walletService.reserve(recipient, 1000L, groupId);

transactionService.confirmTransactionGroup(groupId);
// All transactions confirmed, group status = CONFIRMED
```

---

#### rejectTransactionGroup

Rejects all transactions in a group atomically.

**Signature**:
```java
void rejectTransactionGroup(
    @NotNull UUID referenceId,
    @NotEmpty String reason
)
```

**Parameters**:
- `referenceId` - Transaction group UUID
- `reason` - Rejection reason

**Transaction**: Yes

**Exceptions**:
- `TransactionNotFoundException` - Group not found

**Behavior**:
1. Validates group exists
2. For each transaction (descending order):
   - Calls `walletService.reject()`
3. Updates group status to REJECTED
4. Records reason

**Example**:
```java
try {
    UUID groupId = transactionService.createTransactionGroup();
    walletService.hold(sender, 1000L, groupId);
    walletService.reserve(recipient, 1000L, groupId);
    transactionService.confirmTransactionGroup(groupId);
} catch (Exception e) {
    transactionService.rejectTransactionGroup(groupId, e.getMessage());
}
```

---

#### transferBetweenTwoWallets

Complete money transfer between two wallets.

**Signature**:
```java
UUID transferBetweenTwoWallets(
    @NotNull Integer senderId,
    @NotNull Integer recipientId,
    @Positive Long amount
)
```

**Parameters**:
- `senderId` - Sender wallet ID
- `recipientId` - Recipient wallet ID
- `amount` - Amount to transfer in cents

**Returns**: Transaction group UUID

**Transaction**: NOT_SUPPORTED (manages its own)

**Exceptions**:
- `InsufficientFundsException` - Sender has insufficient funds
- Other exceptions propagated

**Behavior**:
1. Creates transaction group
2. Holds amount from sender
3. Reserves amount to recipient
4. Confirms transaction group (or rejects on error)

**Example**:
```java
UUID groupId = transactionService.transferBetweenTwoWallets(
    senderWalletId,
    recipientWalletId,
    1000L
);
```

---

#### getStatusForReferenceId

Retrieves transaction group status.

**Signature**:
```java
TransactionGroupStatus getStatusForReferenceId(@NotNull UUID referenceId)
```

**Parameters**:
- `referenceId` - Transaction group UUID

**Returns**: Transaction group status

**Exceptions**:
- `EntityNotFoundException` - Group not found

**Example**:
```java
TransactionGroupStatus status =
    transactionService.getStatusForReferenceId(groupId);
```

---

#### getTransactionsByReferenceId

Retrieves all transactions in a group.

**Signature**:
```java
List<TransactionDTO> getTransactionsByReferenceId(@NotNull UUID referenceId)
```

**Parameters**:
- `referenceId` - Transaction group UUID

**Returns**: List of transaction DTOs

**Example**:
```java
List<TransactionDTO> transactions =
    transactionService.getTransactionsByReferenceId(groupId);

long totalAmount = transactions.stream()
    .mapToLong(TransactionDTO::getAmount)
    .sum();
// Should be 0 for confirmed groups
```

---

## WalletBalanceService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Balance calculations and queries

### Methods

#### getAvailableBalance

Calculates spendable balance for a wallet.

**Signature**:
```java
Long getAvailableBalance(@NotNull Integer walletId)
```

**Parameters**:
- `walletId` - Wallet ID

**Returns**: Available balance in cents

**Transaction**: Yes

**Formula**:
```
Available Balance = Confirmed Balance - Hold Amounts (IN_PROGRESS groups)
```

**Example**:
```java
Long balance = walletBalanceService.getAvailableBalance(walletId);
System.out.println("Available: $" + (balance / 100.0));
```

---

#### getHoldBalanceForWallet

Returns sum of all HOLD transaction amounts.

**Signature**:
```java
Long getHoldBalanceForWallet(@NotNull Integer walletId)
```

**Parameters**:
- `walletId` - Wallet ID

**Returns**: Hold balance in cents (absolute value)

**Example**:
```java
Long hold = walletBalanceService.getHoldBalanceForWallet(walletId);
```

---

#### getReservedBalanceForWallet

Returns sum of all RESERVED transaction amounts.

**Signature**:
```java
Long getReservedBalanceForWallet(@NotNull Integer walletId)
```

**Parameters**:
- `walletId` - Wallet ID

**Returns**: Reserved balance in cents

**Example**:
```java
Long reserved = walletBalanceService.getReservedBalanceForWallet(walletId);
```

---

## TransactionSnapshotService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Transaction archiving and ledger management

### Methods

#### captureDailySnapshotForWallet

Moves completed transactions from active to snapshot storage.

**Signature**:
```java
void captureDailySnapshotForWallet(@NotNull Integer walletId)
```

**Parameters**:
- `walletId` - Wallet ID

**Transaction**: Yes

**Behavior**:
1. Queries transactions with CONFIRMED or REJECTED groups
2. Converts to `TransactionSnapshot` entities
3. Batch saves snapshots
4. Verifies count
5. Deletes original transactions if verification passes

**Safety**: IN_PROGRESS groups never moved

**Example**:
```java
transactionSnapshotService.captureDailySnapshotForWallet(walletId);
```

**Scheduling**:
```java
@Scheduled(cron = "0 0 2 * * *") // 2 AM daily
public void dailySnapshot() {
    List<Integer> walletIds = walletRepository.findAllIds();
    for (Integer walletId : walletIds) {
        transactionSnapshotService.captureDailySnapshotForWallet(walletId);
    }
}
```

---

#### archiveOldSnapshots

Archives old snapshots and creates ledger checkpoint.

**Signature**:
```java
void archiveOldSnapshots(
    @NotNull Integer walletId,
    @NotNull LocalDateTime olderThan
)
```

**Parameters**:
- `walletId` - Wallet ID
- `olderThan` - Archive snapshots older than this date

**Transaction**: Yes

**Behavior**:
1. Calculates cumulative balance of old snapshots
2. Creates ledger entry with cumulative balance
3. Tracks reference IDs in `ledger_entries_tracking`
4. Bulk inserts to archive table
5. Bulk deletes from snapshot table
6. Verifies archived count = deleted count

**Example**:
```java
LocalDateTime cutoff = LocalDateTime.now().minusMonths(3);
transactionSnapshotService.archiveOldSnapshots(walletId, cutoff);
```

**Scheduling**:
```java
@Scheduled(cron = "0 0 3 1 * *") // 3 AM on 1st of month
public void monthlyArchive() {
    LocalDateTime cutoff = LocalDateTime.now().minusMonths(3);
    List<Integer> walletIds = walletRepository.findAllIds();
    for (Integer walletId : walletIds) {
        transactionSnapshotService.archiveOldSnapshots(walletId, cutoff);
    }
}
```

---

## TransactionHistoryService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Transaction history queries

### Methods

#### getFullTransactionHistory

Retrieves complete transaction history across all tiers.

**Signature**:
```java
List<TransactionHistoryDTO> getFullTransactionHistory(@NotNull Integer walletId)
```

**Parameters**:
- `walletId` - Wallet ID

**Returns**: List of transaction history DTOs (sorted by timestamp DESC)

**Note**: Excludes ledger entries

**Example**:
```java
List<TransactionHistoryDTO> history =
    transactionHistoryService.getFullTransactionHistory(walletId);

for (TransactionHistoryDTO tx : history) {
    System.out.printf("%s: %s $%.2f%n",
        tx.getTimestamp(),
        tx.getType(),
        tx.getAmount() / 100.0
    );
}
```

---

#### getPaginatedTransactionHistory (no filters)

Retrieves paginated transaction history without filters.

**Signature**:
```java
PagedResponse<TransactionHistoryDTO> getPaginatedTransactionHistory(
    @NotNull Integer walletId,
    @Positive Integer pageNumber,
    @Positive Integer pageSize
)
```

**Parameters**:
- `walletId` - Wallet ID
- `pageNumber` - Page number (1-indexed)
- `pageSize` - Number of records per page

**Returns**: Paged response with metadata

**Example**:
```java
PagedResponse<TransactionHistoryDTO> page =
    transactionHistoryService.getPaginatedTransactionHistory(
        walletId, 1, 20
    );

System.out.println("Page 1 of " + page.getTotalPages());
System.out.println("Total records: " + page.getTotalRecords());

for (TransactionHistoryDTO tx : page.getData()) {
    // Process transaction
}
```

---

#### getPaginatedTransactionHistory (with filters)

Retrieves paginated transaction history with type and status filters.

**Signature**:
```java
PagedResponse<TransactionHistoryDTO> getPaginatedTransactionHistory(
    @NotNull Integer walletId,
    @Positive Integer pageNumber,
    @Positive Integer pageSize,
    @NotNull List<String> typeFilters,
    @NotNull List<String> statusFilters
)
```

**Parameters**:
- `walletId` - Wallet ID
- `pageNumber` - Page number (1-indexed)
- `pageSize` - Number of records per page
- `typeFilters` - Transaction types (empty = all)
- `statusFilters` - Transaction statuses (empty = all)

**Returns**: Paged response with metadata

**Example**:
```java
PagedResponse<TransactionHistoryDTO> confirmed Credits =
    transactionHistoryService.getPaginatedTransactionHistory(
        walletId,
        1,
        50,
        List.of("CREDIT"),
        List.of("CONFIRMED")
    );
```

---

## TransactionStatisticService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Statistical queries for transactions

### Methods

#### getDailyCreditOperations

Fetches CONFIRMED CREDIT operations for a specific day.

**Signature**:
```java
List<TransactionDTO> getDailyCreditOperations(
    @NotNull Integer walletId,
    @NotNull LocalDateTime date
)
```

**Parameters**:
- `walletId` - Wallet ID
- `date` - Date to query

**Returns**: List of transaction DTOs

**Example**:
```java
LocalDateTime today = LocalDateTime.now();
List<TransactionDTO> credits =
    transactionStatisticService.getDailyCreditOperations(walletId, today);

long total = credits.stream()
    .mapToLong(TransactionDTO::getAmount)
    .sum();
System.out.println("Today's credits: $" + (total / 100.0));
```

---

#### getDailyDebitOperations

Fetches CONFIRMED DEBIT operations for a specific day.

**Signature**:
```java
List<TransactionDTO> getDailyDebitOperations(
    @NotNull Integer walletId,
    @NotNull LocalDateTime date
)
```

**Parameters**:
- `walletId` - Wallet ID
- `date` - Date to query

**Returns**: List of transaction DTOs

**Example**:
```java
LocalDateTime today = LocalDateTime.now();
List<TransactionDTO> debits =
    transactionStatisticService.getDailyDebitOperations(walletId, today);
```

---

#### getCreditOperationsInRange

Fetches CONFIRMED CREDIT operations within date range.

**Signature**:
```java
List<TransactionDTO> getCreditOperationsInRange(
    @NotNull Integer walletId,
    @NotNull LocalDateTime fromDate,
    @NotNull LocalDateTime toDate
)
```

**Parameters**:
- `walletId` - Wallet ID
- `fromDate` - Start date (inclusive)
- `toDate` - End date (inclusive)

**Returns**: List of transaction DTOs

**Example**:
```java
LocalDateTime start = LocalDateTime.now().minusDays(30);
LocalDateTime end = LocalDateTime.now();

List<TransactionDTO> credits =
    transactionStatisticService.getCreditOperationsInRange(walletId, start, end);
```

---

#### getDebitOperationsInRange

Fetches CONFIRMED DEBIT operations within date range.

**Signature**:
```java
List<TransactionDTO> getDebitOperationsInRange(
    @NotNull Integer walletId,
    @NotNull LocalDateTime fromDate,
    @NotNull LocalDateTime toDate
)
```

**Parameters**:
- `walletId` - Wallet ID
- `fromDate` - Start date (inclusive)
- `toDate` - End date (inclusive)

**Returns**: List of transaction DTOs

**Example**:
```java
LocalDateTime start = LocalDateTime.now().minusDays(30);
LocalDateTime end = LocalDateTime.now();

List<TransactionDTO> debits =
    transactionStatisticService.getDebitOperationsInRange(walletId, start, end);
```

---

## SystemStatisticService

**Package**: `com.nosota.mwallet.service`

**Purpose**: System-wide statistics

### Methods

#### getReconciliationBalanceOfAllConfirmedGroups

Calculates total initial balance of all wallets in the system.

**Signature**:
```java
Long getReconciliationBalanceOfAllConfirmedGroups()
```

**Returns**: Reconciliation balance in cents

**Transaction**: Yes

**Purpose**: System-wide audit - should equal sum of initial wallet balances

**Example**:
```java
Long reconciliation =
    systemStatisticService.getReconciliationBalanceOfAllConfirmedGroups();

System.out.println("System balance: $" + (reconciliation / 100.0));
```

**Note**: Heavy operation - use sparingly

---

## WalletOwnershipService

**Package**: `com.nosota.mwallet.service`

**Purpose**: Wallet ownership management

### Methods

#### assignOwnership

Assigns or updates wallet ownership.

**Signature**:
```java
WalletOwnerDTO assignOwnership(
    @NotNull Integer walletId,
    @NotNull OwnerType ownerType,
    @NotBlank String ownerRef
)
```

**Parameters**:
- `walletId` - Wallet ID
- `ownerType` - Owner type (USER, SYSTEM)
- `ownerRef` - External owner reference (e.g., user UUID)

**Returns**: Wallet owner DTO

**Transaction**: Yes

**Behavior**: Upsert pattern (creates or updates)

**Example**:
```java
WalletOwnerDTO ownership = walletOwnershipService.assignOwnership(
    walletId,
    OwnerType.USER,
    "user-uuid-123"
);
```

---

#### findOwnerRefByWalletId

Retrieves owner reference for a wallet.

**Signature**:
```java
Optional<String> findOwnerRefByWalletId(@NotNull Integer walletId)
```

**Parameters**:
- `walletId` - Wallet ID

**Returns**: Optional owner reference

**Example**:
```java
Optional<String> ownerRef =
    walletOwnershipService.findOwnerRefByWalletId(walletId);

ownerRef.ifPresent(ref ->
    System.out.println("Owned by: " + ref)
);
```

---

## Common Patterns

### Pattern 1: Simple Transfer

```java
UUID groupId = transactionService.transferBetweenTwoWallets(
    senderWalletId,
    recipientWalletId,
    1000L
);
```

### Pattern 2: Multi-Wallet Transfer (Custom)

```java
UUID groupId = transactionService.createTransactionGroup();

try {
    // Hold from source
    walletService.hold(sourceWallet, 3000L, groupId);

    // Reserve to destinations
    walletService.reserve(dest1, 1000L, groupId);
    walletService.reserve(dest2, 1000L, groupId);
    walletService.reserve(dest3, 1000L, groupId);

    // Confirm all (validates zero-sum)
    transactionService.confirmTransactionGroup(groupId);

} catch (Exception e) {
    transactionService.rejectTransactionGroup(groupId, e.getMessage());
    throw e;
}
```

### Pattern 3: Check Balance Before Operation

```java
Long available = walletBalanceService.getAvailableBalance(walletId);

if (available >= requiredAmount) {
    // Proceed with operation
    UUID groupId = transactionService.createTransactionGroup();
    walletService.hold(walletId, requiredAmount, groupId);
    // ...
} else {
    throw new InsufficientFundsException("Not enough balance");
}
```

### Pattern 4: Rollback on Error

```java
UUID groupId = transactionService.createTransactionGroup();

try {
    walletService.hold(sender, amount, groupId);
    walletService.reserve(recipient, amount, groupId);

    // External validation
    if (!externalService.validate(data)) {
        throw new ValidationException("External validation failed");
    }

    transactionService.confirmTransactionGroup(groupId);

} catch (Exception e) {
    transactionService.rejectTransactionGroup(groupId, e.getMessage());
    throw e;
}
```

## Related Documentation

- [Architecture Overview](../architecture/overview.md) - System architecture
- [Transaction Lifecycle](../architecture/transaction-lifecycle.md) - Transaction flows
- [Development Setup](../development/setup.md) - Environment setup
- [Testing Guide](../development/testing.md) - Testing strategies
