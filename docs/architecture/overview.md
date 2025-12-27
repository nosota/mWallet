# Architecture Overview

## System Purpose

mWallet is a digital wallet management system designed to manage financial transactions with:
- **Immutability**: Complete audit trail of all transactions
- **Concurrency**: Safe concurrent transaction processing
- **Performance**: Efficient balance queries even with extensive transaction history
- **Reliability**: Double-entry bookkeeping with zero-sum reconciliation

## Core Design Principles

### 1. Event-Sourced Model

Every change to wallet state is captured as an immutable event (transaction). This provides:
- Reliable mechanism to reconstruct wallet state at any point in time
- Complete audit trail
- No data loss through updates or deletes

### 2. Append-Only Architecture

Transactions are never updated or deleted. Every action is appended as a new transaction:
- Historical records remain unaltered
- Full transparency
- Simplified concurrency model (no update locks needed)

### 3. Double-Entry Bookkeeping

Every transaction group must balance to zero (debits = credits):
- Prevents money creation or destruction bugs
- Ensures system-wide balance integrity
- Natural audit mechanism

### 4. Two-Phase Transaction Lifecycle

All transactions follow a two-phase lifecycle:

```
Phase 1: Pending
├─ HOLD (for debits) - Funds are held but not yet transferred
└─ RESERVE (for credits) - Space is reserved for incoming funds

Phase 2: Final
├─ CONFIRMED - Transaction completed successfully
└─ REJECTED - Transaction cancelled/rolled back
```

### 5. Three-Tier Storage Architecture

Data moves through three storage tiers optimized for different access patterns:

```
Tier 1: Active Transactions (transaction table)
  ↓ Daily snapshot
Tier 2: Recent History (transaction_snapshot table)
  ↓ Periodic archiving with ledger consolidation
Tier 3: Long-term Archive (transaction_snapshot_archive table)
```

## High-Level Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     Service Layer                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ Wallet           │  │ Transaction      │                 │
│  │ Management       │  │ Service          │                 │
│  │ Service          │  │ (Orchestration)  │                 │
│  └──────────────────┘  └──────────────────┘                 │
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ Wallet           │  │ Transaction      │                 │
│  │ Service          │  │ Snapshot         │                 │
│  │ (CRUD)           │  │ Service          │                 │
│  └──────────────────┘  └──────────────────┘                 │
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ Balance          │  │ History          │                 │
│  │ Service          │  │ Service          │                 │
│  └──────────────────┘  └──────────────────┘                 │
│                                                               │
│  ┌──────────────────┐  ┌──────────────────┐                 │
│  │ Statistics       │  │ System           │                 │
│  │ Service          │  │ Statistics       │                 │
│  └──────────────────┘  └──────────────────┘                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    Repository Layer                          │
│  (JPA Repositories with custom queries)                      │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                       │
│                                                               │
│  Active:      transaction, transaction_group, wallet         │
│  Snapshots:   transaction_snapshot                           │
│  Archive:     transaction_snapshot_archive                   │
│  Tracking:    ledger_entries_tracking                        │
│  Ownership:   wallet_owner                                   │
└─────────────────────────────────────────────────────────────┘
```

### Service Layer Responsibilities

#### WalletManagementService
- Wallet lifecycle management
- Creating wallets with optional initial balance

#### WalletService
- Low-level transaction operations: HOLD, RESERVE, CONFIRM, REJECT
- Direct wallet manipulation
- Single-wallet focused operations

#### TransactionService
- Transaction group orchestration
- Multi-wallet atomic operations
- Zero-sum reconciliation enforcement
- Transfer operations between wallets

#### WalletBalanceService
- Balance calculations (available, hold, reserved)
- Complex queries across multiple storage tiers
- Pessimistic locking for concurrent safety

#### TransactionSnapshotService
- Daily snapshot creation
- Archiving with ledger consolidation
- Data migration between storage tiers

#### TransactionHistoryService
- Unified transaction history queries
- Pagination support
- Filtering by type and status

#### TransactionStatisticService
- Credit/debit operation queries
- Date-based and range-based statistics
- Confirmed transactions only

#### SystemStatisticService
- System-wide reconciliation balance
- Audit and verification queries

#### WalletOwnershipService
- Wallet ownership management
- Owner lookup by wallet or owner reference

## Key Architectural Patterns

### 1. Transaction Group Pattern

Multiple related transactions grouped atomically:

```java
UUID groupId = transactionService.createTransactionGroup();
try {
    walletService.hold(wallet1, amount, groupId);
    walletService.reserve(wallet2, amount, groupId);
    transactionService.confirmTransactionGroup(groupId);
} catch (Exception e) {
    transactionService.rejectTransactionGroup(groupId, reason);
}
```

### 2. Pessimistic Locking Pattern

Critical sections use database-level locks:

```java
// WalletRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
Wallet getOneForUpdate(Integer id);
```

Used during balance checks to prevent race conditions.

### 3. Safe Archiving Pattern

Verification before deletion:

```java
// Save snapshots
List<TransactionSnapshot> snapshots = convertAndSave(transactions);

// Verify count
if (snapshots.size() != transactions.size()) {
    throw new RuntimeException("Verification failed");
}

// Delete originals (transaction will rollback on error)
deleteTransactions(transactions);
```

### 4. Ledger Checkpoint Pattern

Consolidate old transactions into balance checkpoints:

```java
// Calculate cumulative balance
Long balance = calculateBalance(walletId, olderThan);

// Create ledger entry
TransactionSnapshot ledger = new TransactionSnapshot();
ledger.setType(LEDGER);
ledger.setAmount(balance);
ledger.setIsLedgerEntry(true);

// Archive old transactions
// Balance queries can now use ledger entry instead of summing all transactions
```

### 5. Stream-Based Merging Pattern

Efficient data aggregation across storage tiers:

```java
List<TransactionDTO> result = Stream.concat(
    transactionRepository.find(...).stream().map(this::toDTO),
    snapshotRepository.find(...).stream().map(this::toDTO)
).collect(Collectors.toList());
```

## Concurrency Model

### Optimistic Approach
- Most operations use optimistic concurrency (no locks)
- Transactions are append-only (no conflicts)
- Multiple transactions can be inserted simultaneously

### Pessimistic Approach
- Used only for critical balance checks
- `WalletRepository.getOneForUpdate()` acquires exclusive lock
- Prevents race conditions during HOLD operations

### Transaction Isolation
- Database transactions at method level
- Atomic operations within transaction boundaries
- Rollback on any error

## Performance Considerations

### 1. Three-Tier Storage
- Active table stays small (only current operations)
- Snapshots contain recent history (fast queries)
- Archive holds old data (rarely queried)

### 2. Ledger Consolidation
- Old transactions replaced with balance checkpoints
- Reduces row count significantly over time
- Balance queries faster (sum fewer rows)

### 3. Strategic Indexing
- Type, status, date columns indexed
- Foreign keys indexed
- Query patterns optimized

### 4. Native SQL for Bulk Operations
- Bulk inserts for archiving
- Complex aggregations
- UNION queries across tiers

### 5. No Foreign Keys in Archive
- Archive table has no FK constraints
- Faster bulk inserts
- Application ensures referential integrity

## Reliability Mechanisms

### 1. Zero-Sum Reconciliation
- Every transaction group must balance to zero
- Enforced at confirmation time
- Prevents money creation/destruction

### 2. Verification Counts
- Count verification before deletion
- Archiving verifies all rows migrated
- Fail-fast on inconsistencies

### 3. Complete Audit Trail
- All transactions preserved (snapshots + archive)
- Ledger tracking table links consolidated transactions
- Can reconstruct any historical state

### 4. Immutable History
- Transactions never updated or deleted
- New transactions record state changes
- Original transactions remain for audit

### 5. Database Constraints
- Foreign keys on active tables
- NOT NULL constraints
- Check constraints for data integrity

## Scalability Strategy

### Horizontal Scaling
- Stateless service design
- Database connection pooling (HikariCP)
- Read replicas for balance queries (future)

### Vertical Scaling
- Three-tier storage keeps active table small
- Ledger consolidation reduces data growth
- Indexes optimize query performance

### Data Growth Management
- Daily snapshots move data off active table
- Periodic archiving to long-term storage
- Ledger consolidation reduces row count
- Archive table can be moved to cheaper storage (future)

## Future Extensibility

The architecture supports future enhancements:

1. **Multi-currency support**: Add currency field to transactions
2. **Advanced analytics**: OLAP on archive table
3. **Transaction thresholds**: Business rules in transaction service
4. **Read replicas**: Balance queries on read-only replicas
5. **Event streaming**: Publish transactions to event bus
6. **Microservices**: Service layer already decoupled
7. **Caching**: Balance caching with invalidation

## Related Documentation

- [Data Model](./data-model.md) - Complete database schema
- [Transaction Lifecycle](./transaction-lifecycle.md) - Detailed transaction flows
- [Storage Tiers](./storage-tiers.md) - Storage architecture deep dive
- [Service API Reference](../api/services.md) - Service layer documentation
