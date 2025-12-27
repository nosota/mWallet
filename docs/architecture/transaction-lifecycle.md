# Transaction Lifecycle

This document describes the complete lifecycle of transactions in the mWallet system, from creation to archiving.

## Overview

Transactions in mWallet follow a **two-phase lifecycle** with multiple storage tiers:

1. **Phase 1: Pending** - HOLD or RESERVE state
2. **Phase 2: Final** - CONFIRMED or REJECTED state
3. **Storage Migration** - Active → Snapshot → Archive

## Two-Phase Transaction Model

### Phase 1: Pending State

Transactions begin in a pending state to support atomic multi-wallet operations.

#### HOLD (for Debits)

**Purpose**: Reserve funds for a pending debit operation

**Process**:
1. Check available balance in wallet
2. If insufficient, throw `InsufficientFundsException`
3. Create transaction with:
   - `status = HOLD`
   - `type = DEBIT`
   - `amount = <negative value>`
   - `hold_reserve_timestamp = NOW()`
   - `reference_id = <transaction group UUID>`
4. Funds are held but not yet transferred

**Available Balance Calculation**:
```
Available Balance = Confirmed Balance - HOLD amounts for IN_PROGRESS groups
```

**Example**:
```java
UUID groupId = transactionService.createTransactionGroup();
walletService.hold(senderWalletId, 1000L, groupId);
// Sender's available balance reduced by 1000 cents
// But money not yet transferred
```

#### RESERVE (for Credits)

**Purpose**: Reserve space for pending credit operation

**Process**:
1. No balance check needed (incoming funds)
2. Create transaction with:
   - `status = RESERVE`
   - `type = CREDIT`
   - `amount = <positive value>`
   - `hold_reserve_timestamp = NOW()`
   - `reference_id = <transaction group UUID>`
3. Space reserved but funds not yet available

**Available Balance Calculation**:
```
Available Balance = Confirmed Balance - HOLD amounts
(RESERVE amounts NOT included in available balance)
```

**Example**:
```java
UUID groupId = transactionService.createTransactionGroup();
walletService.reserve(recipientWalletId, 1000L, groupId);
// Recipient's balance unchanged
// Funds not yet available for spending
```

### Phase 2: Final State

Pending transactions must be finalized to complete the operation.

#### CONFIRM

**Purpose**: Complete a held or reserved transaction successfully

**Process**:
1. Find HOLD or RESERVE transaction by `wallet_id` and `reference_id`
2. If not found, throw `TransactionNotFoundException`
3. Create **new** transaction with:
   - `status = CONFIRMED`
   - `type = <same as original>`
   - `amount = <same as original>`
   - `confirm_reject_timestamp = NOW()`
   - `reference_id = <same as original>`
4. Original HOLD/RESERVE transaction remains (audit trail)

**Balance Impact**:
- CONFIRMED CREDIT: Increases available balance
- CONFIRMED DEBIT: Decreases available balance (already held)

**Example**:
```java
walletService.confirm(senderWalletId, groupId);
walletService.confirm(recipientWalletId, groupId);
// Both transactions now CONFIRMED
// Transfer complete
```

#### REJECT

**Purpose**: Cancel a held or reserved transaction

**Process**:
1. Find HOLD or RESERVE transaction by `wallet_id` and `reference_id`
2. If not found, throw `TransactionNotFoundException`
3. Create **new** transaction with:
   - `status = REJECTED`
   - `type = <same as original>`
   - `amount = <same as original>`
   - `confirm_reject_timestamp = NOW()`
   - `reference_id = <same as original>`
4. Original HOLD/RESERVE transaction remains (audit trail)

**Balance Impact**:
- REJECTED HOLD: Releases held funds back to available balance
- REJECTED RESERVE: Cancels incoming funds (no balance change)

**Example**:
```java
try {
    walletService.hold(senderWalletId, amount, groupId);
    walletService.reserve(recipientWalletId, amount, groupId);
    transactionService.confirmTransactionGroup(groupId);
} catch (Exception e) {
    transactionService.rejectTransactionGroup(groupId, e.getMessage());
    // All transactions in group now REJECTED
    // Balances restored to original state
}
```

## State Transition Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                    Transaction Lifecycle                      │
└──────────────────────────────────────────────────────────────┘

DEBIT Flow:
  [HOLD]
    │
    ├─→ [CONFIRMED] ─→ (Debit completed)
    │
    └─→ [REJECTED]  ─→ (Debit cancelled, funds released)

CREDIT Flow:
  [RESERVE]
    │
    ├─→ [CONFIRMED] ─→ (Credit completed)
    │
    └─→ [REJECTED]  ─→ (Credit cancelled)

LEDGER Flow:
  [CONFIRMED] ─→ (Balance checkpoint, snapshots only)
```

**Key Rules**:
- HOLD can only become CONFIRMED or REJECTED
- RESERVE can only become CONFIRMED or REJECTED
- Never HOLD → RESERVE or vice versa
- Original HOLD/RESERVE transactions never deleted (audit trail)
- Confirmation/rejection creates new transaction records

## Transaction Groups

### Purpose

Group related transactions to ensure atomicity across multiple wallets.

### Lifecycle

```
1. Create Group
   ↓
2. Add Transactions (HOLD/RESERVE)
   ↓
3. Validate Zero-Sum
   ↓
4. Confirm or Reject All Transactions
   ↓
5. Update Group Status
```

### Creating a Transaction Group

```java
UUID groupId = transactionService.createTransactionGroup();
// Group created with status = IN_PROGRESS
```

### Adding Transactions to Group

```java
// All transactions share the same reference_id (group UUID)
walletService.hold(wallet1, 1000L, groupId);
walletService.reserve(wallet2, 1000L, groupId);
// Group still IN_PROGRESS
```

### Confirming a Transaction Group

```java
transactionService.confirmTransactionGroup(groupId);
```

**Process**:
1. Fetch transaction group
2. Validate group exists
3. **Zero-sum reconciliation check**:
   ```sql
   SELECT COALESCE(SUM(amount), 0)
   FROM transaction
   WHERE reference_id = ? AND status IN ('HOLD', 'RESERVE')
   ```
   Must equal **zero**, otherwise throw `TransactionGroupZeroingOutException`
4. For each transaction (in descending ID order):
   - Call `walletService.confirm()`
   - Creates CONFIRMED transaction
5. Update group status to CONFIRMED
6. All operations in single transaction (rollback on error)

**Zero-Sum Rule**:
```
SUM(HOLD amounts) + SUM(RESERVE amounts) = 0

Example:
HOLD:    -1000 cents (sender)
RESERVE: +1000 cents (recipient)
─────────────────────────────
SUM:          0 cents ✓
```

### Rejecting a Transaction Group

```java
transactionService.rejectTransactionGroup(groupId, "Insufficient funds");
```

**Process**:
1. Fetch transaction group
2. For each transaction (in descending ID order):
   - Call `walletService.reject()`
   - Creates REJECTED transaction
3. Update group status to REJECTED
4. Record reason in group
5. All operations in single transaction

### Transaction Group Statuses

| Status | Description | Allowed Operations |
|--------|-------------|-------------------|
| `IN_PROGRESS` | Group being built | Add transactions, confirm, reject |
| `CONFIRMED` | All transactions confirmed | None (immutable) |
| `REJECTED` | All transactions rejected | None (immutable) |

## Complete Transfer Example

### Two-Wallet Transfer

```java
public UUID transferBetweenTwoWallets(
    Integer senderId,
    Integer recipientId,
    Long amount
) {
    UUID groupId = transactionService.createTransactionGroup();

    try {
        // Phase 1: Pending
        walletService.hold(senderId, amount, groupId);    // HOLD -amount
        walletService.reserve(recipientId, amount, groupId); // RESERVE +amount

        // Phase 2: Confirm
        transactionService.confirmTransactionGroup(groupId);
        // Creates CONFIRMED transactions for both wallets

        return groupId;

    } catch (Exception e) {
        // Rollback: reject all transactions
        transactionService.rejectTransactionGroup(groupId, e.getMessage());
        throw e;
    }
}
```

**Transaction Flow**:

```
Time | Sender Wallet                | Recipient Wallet           | Group Status
──────────────────────────────────────────────────────────────────────────────
T0   | Balance: 5000               | Balance: 0                 | -
T1   | HOLD -1000 created          | -                          | IN_PROGRESS
     | Available: 4000             |                            |
T2   | -                           | RESERVE +1000 created      | IN_PROGRESS
     |                             | Available: 0               |
T3   | Zero-sum validated (HOLD -1000 + RESERVE +1000 = 0)       | IN_PROGRESS
T4   | CONFIRMED -1000 created     | CONFIRMED +1000 created    | CONFIRMED
     | Available: 4000             | Available: 1000            |
```

### Multi-Wallet Transfer (Custom Implementation)

For transfers involving 3+ wallets, implement custom logic:

```java
public UUID transferAcrossMultipleWallets(
    Integer sourceWallet,
    List<Pair<Integer, Long>> destinations
) {
    UUID groupId = transactionService.createTransactionGroup();

    try {
        // Calculate total amount
        Long totalAmount = destinations.stream()
            .map(Pair::getSecond)
            .reduce(0L, Long::sum);

        // Hold from source
        walletService.hold(sourceWallet, totalAmount, groupId);

        // Reserve to each destination
        for (Pair<Integer, Long> dest : destinations) {
            walletService.reserve(dest.getFirst(), dest.getSecond(), groupId);
        }

        // Confirm all (validates zero-sum)
        transactionService.confirmTransactionGroup(groupId);

        return groupId;

    } catch (Exception e) {
        transactionService.rejectTransactionGroup(groupId, e.getMessage());
        throw e;
    }
}
```

**Zero-Sum Validation**:
```
HOLD:    -3000 (source)
RESERVE: +1000 (dest1)
RESERVE: +1000 (dest2)
RESERVE: +1000 (dest3)
─────────────────────
SUM:          0 ✓
```

## Storage Tier Migration

After transactions are finalized, they migrate through storage tiers for performance optimization.

### Tier 1: Active Transactions

**Table**: `transaction`

**Content**: Current and recent transactions

**Lifecycle**:
- Created during wallet operations
- Remain until transaction group is CONFIRMED or REJECTED
- Moved to snapshots during daily snapshot process

**Queries**:
- Real-time balance calculations
- Current transaction status
- In-progress transaction groups

### Tier 2: Transaction Snapshots

**Table**: `transaction_snapshot`

**Content**: Completed transactions (recent history)

**Lifecycle**:
- Receives data from `transaction` table during daily snapshot
- Only transactions with CONFIRMED or REJECTED groups moved
- Periodically archived to tier 3

**Queries**:
- Transaction history (recent)
- Balance calculations
- Statistics and reports

### Tier 3: Transaction Archive

**Table**: `transaction_snapshot_archive`

**Content**: Old transactions (long-term storage)

**Lifecycle**:
- Receives data from `transaction_snapshot` during archiving
- Consolidated via ledger entries
- Permanent storage

**Queries**:
- Historical transaction lookups
- Full audit trail
- Rarely queried directly

### Daily Snapshot Process

**Trigger**: Externally scheduled (e.g., CRON job, scheduled task)

**Method**: `TransactionSnapshotService.captureDailySnapshotForWallet(Integer walletId)`

**Process**:
1. Query transactions with CONFIRMED or REJECTED groups:
   ```sql
   SELECT t.* FROM transaction t
   JOIN transaction_group tg ON t.reference_id = tg.id
   WHERE t.wallet_id = ?
     AND tg.status IN ('CONFIRMED', 'REJECTED')
   ```
2. Convert to `TransactionSnapshot` entities
3. Set `snapshot_date = NOW()`
4. Set `is_ledger_entry = FALSE`
5. Batch save snapshots
6. **Verification**: Count saved snapshots = count source transactions
7. If verification passes, delete original transactions
8. If verification fails, rollback (transaction)

**Safety**:
- IN_PROGRESS groups never moved (prevents data loss)
- Count verification prevents partial migration
- Database transaction ensures atomicity

### Archiving Process

**Trigger**: Externally scheduled (e.g., monthly CRON job)

**Method**: `TransactionSnapshotService.archiveOldSnapshots(Integer walletId, LocalDateTime olderThan)`

**Process**:
1. Calculate cumulative balance of old snapshots:
   ```sql
   SELECT COALESCE(SUM(amount), 0)
   FROM transaction_snapshot
   WHERE wallet_id = ?
     AND confirm_reject_timestamp < ?
     AND is_ledger_entry = FALSE
   ```
2. If balance is zero, exit (nothing to archive)
3. **Create ledger entry**:
   - `type = LEDGER`
   - `status = CONFIRMED`
   - `amount = <cumulative balance>`
   - `is_ledger_entry = TRUE`
   - `snapshot_date = NOW()`
4. Save ledger entry
5. Find distinct reference IDs being archived
6. Create tracking entries in `ledger_entries_tracking`
7. Bulk insert snapshots to archive table (native SQL)
8. Bulk delete snapshots from active table
9. **Verification**: archived count = deleted count
10. If verification fails, throw exception (rollback)

**Ledger Consolidation Example**:

Before archiving (100 transactions):
```
Transaction 1: +1000
Transaction 2: -500
Transaction 3: +300
...
Transaction 100: -200
─────────────────────
Sum: +5000
```

After archiving (1 ledger entry):
```
Ledger Entry: +5000 (LEDGER type)
```

**Balance Query Optimization**:
```sql
-- Before ledger: Sum 100 rows
SELECT SUM(amount) FROM transaction_snapshot
WHERE wallet_id = ?

-- After ledger: Sum 1 row + newer transactions
SELECT SUM(amount) FROM transaction_snapshot
WHERE wallet_id = ?
  AND (is_ledger_entry = TRUE OR confirm_reject_timestamp >= <ledger_date>)
```

## Balance Calculations Across Lifecycle

### Available Balance

```java
Long available = walletBalanceService.getAvailableBalance(walletId);
```

**Formula**:
```
Available Balance = Confirmed Balance - Hold Amount

Where:
- Confirmed Balance = SUM(CONFIRMED transactions across all tiers)
- Hold Amount = SUM(HOLD amounts for IN_PROGRESS groups)
```

**SQL**:
```sql
-- Confirmed balance
SELECT COALESCE(SUM(amount), 0)
FROM (
    SELECT amount FROM transaction
    WHERE wallet_id = ? AND status = 'CONFIRMED'
    UNION ALL
    SELECT amount FROM transaction_snapshot
    WHERE wallet_id = ? AND status = 'CONFIRMED'
) AS combined

-- Minus hold amount for in-progress groups
- COALESCE((
    SELECT SUM(t.amount)
    FROM transaction t
    JOIN transaction_group tg ON t.reference_id = tg.id
    WHERE t.wallet_id = ?
      AND t.status = 'HOLD'
      AND tg.status = 'IN_PROGRESS'
), 0)
```

**Note**: Archive table excluded from available balance (performance optimization)

### Hold Balance

```java
Long hold = walletBalanceService.getHoldBalanceForWallet(walletId);
```

**Formula**:
```
Hold Balance = SUM(HOLD amounts in snapshots)
```

**Note**: Typically zero after daily snapshot (HOLD → CONFIRMED or REJECTED)

### Reserved Balance

```java
Long reserved = walletBalanceService.getReservedBalanceForWallet(walletId);
```

**Formula**:
```
Reserved Balance = SUM(RESERVE amounts in snapshots)
```

**Note**: Typically zero after daily snapshot (RESERVE → CONFIRMED or REJECTED)

## Key Invariants

### Immutability
- Transactions are **never updated** after creation
- State changes create new transactions
- Original transactions remain for audit trail

### Zero-Sum Rule
- Every CONFIRMED transaction group must sum to zero
- Enforced at confirmation time
- Prevents money creation/destruction

### Atomicity
- All transactions in a group confirmed or rejected together
- Database transactions ensure rollback on error
- No partial confirmations possible

### Audit Trail
- Complete history preserved across all tiers
- Ledger tracking links consolidated transactions
- Can reconstruct any historical state

### Safe Migration
- Count verification before deletion
- Only completed groups moved to snapshots
- Transaction rollback on verification failure

## Related Documentation

- [Data Model](./data-model.md) - Database schema details
- [Storage Tiers](./storage-tiers.md) - Storage architecture deep dive
- [Service API Reference](../api/services.md) - Service layer documentation
- [Snapshot & Archiving](../operations/snapshot-archiving.md) - Operational procedures
