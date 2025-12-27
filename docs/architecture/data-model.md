# Data Model

This document describes the complete database schema for the mWallet system.

## Entity Relationship Diagram

```
┌─────────────────┐
│     Wallet      │
│  ───────────    │
│  id (PK)        │
│  type           │
│  description    │
│  created_at     │
└────────┬────────┘
         │ 1
         │
         │ N
┌────────▼─────────────┐
│   WalletOwner        │
│  ─────────────       │
│  id (PK)             │
│  wallet_id (FK) ────►│
│  owner_type          │
│  owner_ref           │
│  created_at          │
│  updated_at          │
└──────────────────────┘

         ┌──────────────────────┐
         │  TransactionGroup    │
         │  ─────────────────   │
         │  id (PK, UUID)       │
         │  status              │
         │  reason              │
         │  created_at          │
         │  updated_at          │
         └──────┬───────────────┘
                │ 1
                │
                │ N
┌───────────────▼──────────────┐      ┌─────────────────┐
│      Transaction             │  N   │     Wallet      │
│  ────────────────            │◄─────┤                 │
│  id (PK)                     │   1  │                 │
│  reference_id (FK) ─────────►│      └─────────────────┘
│  wallet_id (FK) ─────────────┤
│  amount                      │
│  type                        │
│  status                      │
│  hold_reserve_timestamp      │
│  confirm_reject_timestamp    │
│  description                 │
└──────────────────────────────┘

┌──────────────────────────────┐
│  TransactionSnapshot         │
│  ────────────────────        │
│  id (PK)                     │
│  reference_id (FK) ─────────►│ TransactionGroup
│  wallet_id (FK) ─────────────┤
│  amount                      │
│  type                        │
│  status                      │
│  hold_reserve_timestamp      │
│  confirm_reject_timestamp    │
│  snapshot_date               │
│  description                 │
│  is_ledger_entry             │
└──────────────────────────────┘

┌──────────────────────────────┐
│ TransactionSnapshotArchive   │
│ (Same structure, no FKs)     │
└──────────────────────────────┘

┌──────────────────────────────┐
│  LedgerEntriesTracking       │
│  ───────────────────         │
│  id (PK)                     │
│  ledger_entry_id             │
│  reference_id (UUID)         │
└──────────────────────────────┘
```

## Core Entities

### Wallet

Represents a digital wallet container.

**Table**: `wallet`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO_INCREMENT | Unique wallet identifier |
| type | VARCHAR | NOT NULL | Wallet type: USER, FEE, SYSTEM |
| description | TEXT | NULLABLE | Optional wallet description |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Wallet creation timestamp |

**Indexes**:
- `PRIMARY KEY (id)`
- `idx_wallet_type (type)`

**Java Entity**: `com.nosota.mwallet.model.Wallet`

```java
@Entity
@Table(name = "wallet")
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletType type;

    @Column
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

---

### WalletOwner

Links wallets to their owners (users or system entities).

**Table**: `wallet_owner`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| wallet_id | INTEGER | FK → wallet(id), ON DELETE CASCADE | Owned wallet |
| owner_type | VARCHAR | NOT NULL | Owner type: USER, SYSTEM |
| owner_ref | VARCHAR | NOT NULL | External owner reference (e.g., user UUID) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last update timestamp |

**Indexes**:
- `PRIMARY KEY (id)`
- `FOREIGN KEY (wallet_id) REFERENCES wallet(id) ON DELETE CASCADE`

**Java Entity**: `com.nosota.mwallet.model.WalletOwner`

---

### TransactionGroup

Groups related transactions to ensure atomicity.

**Table**: `transaction_group`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Unique group identifier |
| status | VARCHAR | NOT NULL | Group status: IN_PROGRESS, CONFIRMED, REJECTED |
| reason | TEXT | NULLABLE | Rejection reason (if status = REJECTED) |
| created_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Group creation timestamp |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT NOW() | Auto-updated via trigger |

**Indexes**:
- `PRIMARY KEY (id)`
- `idx_transaction_group_status (status)`

**Trigger**: `update_transaction_group_modtime` - Automatically updates `updated_at` on modification

**Java Entity**: `com.nosota.mwallet.model.TransactionGroup`

---

### Transaction

Represents active transactions (not yet moved to snapshots).

**Table**: `transaction`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO_INCREMENT | Unique transaction identifier |
| reference_id | UUID | FK → transaction_group(id) | Transaction group reference |
| wallet_id | INTEGER | FK → wallet(id) | Wallet being transacted |
| amount | BIGINT | NOT NULL | Amount in cents (negative for debits during HOLD) |
| type | VARCHAR | NOT NULL | Transaction type: CREDIT, DEBIT, LEDGER |
| status | VARCHAR | NOT NULL | Transaction status: HOLD, RESERVE, CONFIRMED, REJECTED |
| hold_reserve_timestamp | TIMESTAMP | NULLABLE | When transaction was held/reserved |
| confirm_reject_timestamp | TIMESTAMP | NULLABLE | When transaction was confirmed/rejected |
| description | TEXT | NULLABLE | Optional description |

**Indexes**:
- `PRIMARY KEY (id)`
- `idx_transaction_type (type)`
- `idx_transaction_status (status)`
- `FOREIGN KEY (wallet_id) REFERENCES wallet(id)`
- `FOREIGN KEY (reference_id) REFERENCES transaction_group(id)`

**Java Entity**: `com.nosota.mwallet.model.Transaction`

**Lifecycle**:
- Created during wallet operations
- Moved to `transaction_snapshot` table during daily snapshot
- Deleted from this table after successful snapshot

---

### TransactionSnapshot

Archived transactions for recent history (2nd storage tier).

**Table**: `transaction_snapshot`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO_INCREMENT | Unique snapshot identifier |
| reference_id | UUID | FK → transaction_group(id) | Transaction group reference |
| wallet_id | INTEGER | FK → wallet(id) | Wallet being transacted |
| amount | BIGINT | NOT NULL | Amount in cents |
| type | VARCHAR | NOT NULL | Transaction type: CREDIT, DEBIT, LEDGER |
| status | VARCHAR | NOT NULL | Transaction status |
| hold_reserve_timestamp | TIMESTAMP | NULLABLE | Original hold/reserve time |
| confirm_reject_timestamp | TIMESTAMP | NULLABLE | Original confirm/reject time |
| snapshot_date | TIMESTAMP | NOT NULL | When snapshot was created |
| description | TEXT | NULLABLE | Optional description |
| is_ledger_entry | BOOLEAN | NOT NULL, DEFAULT FALSE | True if this is a ledger consolidation entry |

**Indexes**:
- `PRIMARY KEY (id)`
- `idx_transaction_snapshot_type (type)`
- `idx_transaction_snapshot_status (status)`
- `idx_transaction_snapshot_date (snapshot_date)`
- `FOREIGN KEY (wallet_id) REFERENCES wallet(id)`
- `FOREIGN KEY (reference_id) REFERENCES transaction_group(id)`

**Java Entity**: `com.nosota.mwallet.model.TransactionSnapshot`

**Special Rows - Ledger Entries**:
When `is_ledger_entry = TRUE`:
- `type = LEDGER`
- `status = CONFIRMED`
- `amount` = Cumulative balance of archived transactions
- Used as balance checkpoints for performance

**Lifecycle**:
- Receives data from `transaction` table during daily snapshot
- Periodically moved to `transaction_snapshot_archive` during archiving
- Ledger entries created during archiving to represent cumulative balance

---

### TransactionSnapshotArchive

Long-term storage for old transactions (3rd storage tier).

**Table**: `transaction_snapshot_archive`

**Structure**: Identical to `transaction_snapshot`

**Key Difference**: No foreign key constraints for performance

**Indexes**: Same as `transaction_snapshot`

**Purpose**:
- Long-term storage
- Rarely queried
- Can be moved to cheaper storage in future
- Optimized for write performance (bulk inserts)

---

### LedgerEntriesTracking

Audit trail linking ledger entries to the transaction groups they consolidated.

**Table**: `ledger_entries_tracking`

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | INTEGER | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| ledger_entry_id | INTEGER | NOT NULL | ID of ledger entry in transaction_snapshot |
| reference_id | UUID | NOT NULL | Transaction group that was archived |

**Indexes**:
- `PRIMARY KEY (id)`

**Note**: No foreign keys for performance (audit table)

**Java Entity**: `com.nosota.mwallet.model.LedgerEntriesTracking`

**Purpose**:
- Track which transaction groups were consolidated into which ledger entries
- Audit trail for archiving operations
- Enables reconstruction of detailed history if needed

---

## Enumerations

### WalletType

Defines the category of wallet.

**Values**:
- `USER` - User-owned wallet for customer balances
- `FEE` - Fee collection wallet for platform fees
- `SYSTEM` - System-owned wallet for internal operations

**Java Enum**: `com.nosota.mwallet.model.WalletType`

---

### OwnerType

Defines the type of wallet owner.

**Values**:
- `USER` - Wallet owned by a user
- `SYSTEM` - Wallet owned by the system

**Java Enum**: `com.nosota.mwallet.model.OwnerType`

---

### TransactionType

Defines the direction of money flow.

**Values**:
- `CREDIT` - Money coming into the wallet (positive amount)
- `DEBIT` - Money going out of the wallet (negative amount during HOLD)
- `LEDGER` - Balance checkpoint entry (used in snapshots only)

**Java Enum**: `com.nosota.mwallet.model.TransactionType`

---

### TransactionStatus

Defines the lifecycle state of a transaction.

**Values**:
- `HOLD` - Funds held for pending debit (phase 1)
- `RESERVE` - Space reserved for pending credit (phase 1)
- `CONFIRMED` - Transaction completed successfully (phase 2)
- `REJECTED` - Transaction cancelled/rolled back (phase 2)

**Java Enum**: `com.nosota.mwallet.model.TransactionStatus`

**State Transitions**:
```
HOLD ──┬─→ CONFIRMED
       └─→ REJECTED

RESERVE ┬─→ CONFIRMED
        └─→ REJECTED
```

---

### TransactionGroupStatus

Defines the state of a transaction group.

**Values**:
- `IN_PROGRESS` - Group still being built, transactions not finalized
- `CONFIRMED` - All transactions in group confirmed
- `REJECTED` - All transactions in group rejected

**Java Enum**: `com.nosota.mwallet.model.TransactionGroupStatus`

---

## Key Relationships

### Wallet to WalletOwner
- **Relationship**: One-to-Many
- **Cascade**: DELETE CASCADE (deleting wallet deletes ownership records)
- **Purpose**: Track wallet ownership

### Wallet to Transaction
- **Relationship**: One-to-Many
- **Cascade**: None (transactions must be explicitly managed)
- **Purpose**: All transactions for a wallet

### TransactionGroup to Transaction
- **Relationship**: One-to-Many
- **Cascade**: None
- **Purpose**: Group related transactions atomically

### TransactionGroup to TransactionSnapshot
- **Relationship**: One-to-Many
- **Cascade**: None
- **Purpose**: Link archived transactions to their original group

---

## Data Integrity Rules

### Double-Entry Bookkeeping
Every `TransactionGroup` with status = CONFIRMED must satisfy:
```sql
SUM(amount) WHERE reference_id = <group_id> = 0
```

Enforced by `TransactionService.confirmTransactionGroup()`.

### Immutable Transactions
- Transactions are never updated
- State changes create new transactions
- Original transactions remain for audit

### Safe Archiving
- Count verification before deletion
- `snapshots.size() == transactions.size()`
- Transaction rollback on mismatch

### Ledger Accuracy
- Ledger entry amount = SUM of archived transactions
- Tracked in `ledger_entries_tracking`
- Enables balance verification

---

## Query Patterns

### Available Balance Calculation

```sql
-- Sum of confirmed transactions across all tiers
SELECT COALESCE(SUM(amount), 0)
FROM (
    SELECT amount FROM transaction
    WHERE wallet_id = ? AND status = 'CONFIRMED'
    UNION ALL
    SELECT amount FROM transaction_snapshot
    WHERE wallet_id = ? AND status = 'CONFIRMED'
) AS combined

-- Minus hold amounts for in-progress groups
- COALESCE((
    SELECT SUM(t.amount) FROM transaction t
    JOIN transaction_group tg ON t.reference_id = tg.id
    WHERE t.wallet_id = ?
      AND t.status = 'HOLD'
      AND tg.status = 'IN_PROGRESS'
), 0)
```

### Transaction History (All Tiers)

```sql
SELECT * FROM (
    SELECT * FROM transaction WHERE wallet_id = ?
    UNION ALL
    SELECT * FROM transaction_snapshot WHERE wallet_id = ?
    UNION ALL
    SELECT * FROM transaction_snapshot_archive WHERE wallet_id = ?
) AS all_transactions
ORDER BY confirm_reject_timestamp DESC, hold_reserve_timestamp DESC
```

### Reconciliation Balance

```sql
WITH confirmed_groups AS (
    SELECT id FROM transaction_group WHERE status = 'CONFIRMED'
)
SELECT COALESCE(SUM(amount), 0) FROM (
    SELECT reference_id, SUM(amount) AS amount
    FROM transaction
    WHERE reference_id IN (SELECT id FROM confirmed_groups)
    GROUP BY reference_id

    UNION ALL

    SELECT reference_id, SUM(amount) AS amount
    FROM transaction_snapshot
    WHERE reference_id IN (SELECT id FROM confirmed_groups)
    GROUP BY reference_id

    UNION ALL

    SELECT reference_id, SUM(amount) AS amount
    FROM transaction_snapshot_archive
    WHERE reference_id IN (SELECT id FROM confirmed_groups)
    GROUP BY reference_id
) AS grouped_amounts
```

---

## Migration History

See Flyway migrations in `service/src/main/resources/db/migration/`:

- **V1.01** - Initial schema (wallet, transaction, transaction_snapshot)
- **V1.02** - Added transaction_group and reference_id
- **V1.03** - Added wallet_owner table
- **V1.04** - Added transaction_snapshot_archive and is_ledger_entry
- **V1.05** - Renamed hold_timestamp to hold_reserve_timestamp
- **V1.06** - Added description to snapshots
- **V1.07** - Added transaction_group triggers
- **V1.08** - Added description to wallet
- **V1.09** - Added ledger_entries_tracking
- **V1.10** - Added reason to transaction_group

---

## Related Documentation

- [Architecture Overview](./overview.md) - High-level architecture
- [Transaction Lifecycle](./transaction-lifecycle.md) - Transaction state management
- [Storage Tiers](./storage-tiers.md) - Three-tier storage deep dive
