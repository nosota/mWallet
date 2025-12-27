# Storage Tiers Architecture

This document provides a deep dive into the three-tier storage architecture of mWallet, explaining why it exists, how it works, and how to optimize it.

## Why Three Tiers?

### The Problem

In an append-only, event-sourced system, transaction tables grow indefinitely:

```
Day 1:     1,000 transactions → Balance query scans 1,000 rows
Day 30:   30,000 transactions → Balance query scans 30,000 rows
Day 365: 365,000 transactions → Balance query scans 365,000 rows
```

**Without optimization**:
- Balance queries become slower over time
- Indexes grow larger
- Query planner less efficient
- More disk I/O required

### The Solution

**Three-tier storage architecture** with periodic data migration and consolidation:

1. **Tier 1 (Active)**: Small, fast, recent transactions
2. **Tier 2 (Snapshots)**: Medium-sized, recent history with ledger checkpoints
3. **Tier 3 (Archive)**: Large, long-term storage, rarely queried

**Result**:
- Active table stays small (fast queries)
- Recent history accessible (snapshots)
- Complete audit trail preserved (archive)
- Ledger checkpoints reduce row scanning

## The Three Tiers

### Tier 1: Active Transactions

**Table**: `transaction`

**Purpose**: Real-time transaction processing

**Characteristics**:
- **Size**: Small (only current operations)
- **Performance**: Very fast (frequent queries)
- **Content**: Active and in-progress transactions
- **Retention**: Until transaction group CONFIRMED or REJECTED

**What Goes Here**:
- Newly created transactions (HOLD, RESERVE)
- Recent confirmations/rejections
- Any transaction with IN_PROGRESS group status

**What Gets Removed**:
- Transactions with CONFIRMED or REJECTED groups
- Moved to tier 2 during daily snapshot

**Indexes**:
```sql
PRIMARY KEY (id)
INDEX idx_transaction_type (type)
INDEX idx_transaction_status (status)
FOREIGN KEY (wallet_id) REFERENCES wallet(id)
FOREIGN KEY (reference_id) REFERENCES transaction_group(id)
```

**Typical Queries**:
```sql
-- Available balance (active only)
SELECT SUM(amount) FROM transaction
WHERE wallet_id = ? AND status = 'CONFIRMED';

-- In-progress holds
SELECT SUM(t.amount) FROM transaction t
JOIN transaction_group tg ON t.reference_id = tg.id
WHERE t.wallet_id = ? AND t.status = 'HOLD' AND tg.status = 'IN_PROGRESS';

-- Transaction group status
SELECT * FROM transaction
WHERE reference_id = ?;
```

---

### Tier 2: Transaction Snapshots

**Table**: `transaction_snapshot`

**Purpose**: Recent history with balance checkpoints

**Characteristics**:
- **Size**: Medium (weeks to months of history)
- **Performance**: Fast (indexed, optimized for reads)
- **Content**: Completed transactions + ledger entries
- **Retention**: Until archived (configurable)

**What Goes Here**:
- Completed transactions from tier 1 (daily snapshot)
- Ledger entries (balance checkpoints)

**What Gets Removed**:
- Old transactions (archived to tier 3)
- Consolidated into ledger entries

**Special Rows - Ledger Entries**:

Ledger entries are special balance checkpoint rows:

```sql
is_ledger_entry = TRUE
type = 'LEDGER'
status = 'CONFIRMED'
amount = <cumulative balance of archived transactions>
snapshot_date = <when ledger created>
```

**Purpose of Ledger Entries**:
- Replace many old transactions with single balance checkpoint
- Reduce row count significantly
- Accelerate balance queries
- Maintain accuracy (tracked in `ledger_entries_tracking`)

**Example**:

Before ledger:
```
100 transactions × 50 bytes = 5,000 bytes
Balance query: SUM 100 rows
```

After ledger:
```
1 ledger entry × 50 bytes = 50 bytes
Balance query: SUM 1 row (ledger) + new transactions
```

**Indexes**:
```sql
PRIMARY KEY (id)
INDEX idx_transaction_snapshot_type (type)
INDEX idx_transaction_snapshot_status (status)
INDEX idx_transaction_snapshot_date (snapshot_date)
FOREIGN KEY (wallet_id) REFERENCES wallet(id)
FOREIGN KEY (reference_id) REFERENCES transaction_group(id)
```

**Typical Queries**:
```sql
-- Recent transaction history
SELECT * FROM transaction_snapshot
WHERE wallet_id = ?
ORDER BY confirm_reject_timestamp DESC
LIMIT 100;

-- Balance with ledger optimization
SELECT COALESCE(SUM(amount), 0)
FROM transaction_snapshot
WHERE wallet_id = ?
  AND status = 'CONFIRMED';

-- Daily statistics
SELECT SUM(amount) FROM transaction_snapshot
WHERE wallet_id = ?
  AND type = 'CREDIT'
  AND status = 'CONFIRMED'
  AND DATE(confirm_reject_timestamp) = ?;
```

---

### Tier 3: Transaction Archive

**Table**: `transaction_snapshot_archive`

**Purpose**: Long-term storage

**Characteristics**:
- **Size**: Large (years of history)
- **Performance**: Optimized for writes, not reads
- **Content**: Old transactions from tier 2
- **Retention**: Permanent

**What Goes Here**:
- Old transactions from tier 2 (archived monthly/quarterly)
- Complete historical record

**What Gets Removed**:
- Nothing (permanent storage)

**Key Design Decision: No Foreign Keys**

```sql
-- transaction_snapshot has foreign keys:
FOREIGN KEY (wallet_id) REFERENCES wallet(id)
FOREIGN KEY (reference_id) REFERENCES transaction_group(id)

-- transaction_snapshot_archive has NONE
-- Application ensures referential integrity
```

**Why No Foreign Keys?**
- **Bulk insert performance**: Constraint checks slow down batch inserts
- **Index maintenance overhead**: FK indexes add write overhead
- **Archive immutability**: Data never changes, so integrity checks unnecessary
- **Trade-off**: Performance vs. database-level constraints

**Indexes** (same as tier 2):
```sql
PRIMARY KEY (id)
INDEX idx_transaction_snapshot_archive_type (type)
INDEX idx_transaction_snapshot_archive_status (status)
INDEX idx_transaction_snapshot_archive_date (snapshot_date)
```

**Typical Queries**:
```sql
-- Full historical audit trail (rarely needed)
SELECT * FROM transaction_snapshot_archive
WHERE wallet_id = ?
  AND confirm_reject_timestamp BETWEEN ? AND ?;

-- System-wide reconciliation
SELECT SUM(amount) FROM transaction_snapshot_archive
WHERE status = 'CONFIRMED';
```

**Query Pattern**: Archive table typically excluded from routine queries for performance.

---

## Data Flow Between Tiers

### Daily Snapshot: Tier 1 → Tier 2

**Trigger**: Scheduled job (e.g., CRON at 2 AM daily)

**Implementation**: `TransactionSnapshotService.captureDailySnapshotForWallet(Integer walletId)`

**Process Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│  1. Query completed transactions                            │
│     SELECT t.* FROM transaction t                           │
│     JOIN transaction_group tg ON t.reference_id = tg.id     │
│     WHERE t.wallet_id = ?                                   │
│       AND tg.status IN ('CONFIRMED', 'REJECTED')            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  2. Convert to TransactionSnapshot entities                 │
│     - Copy all fields                                       │
│     - Set snapshot_date = NOW()                             │
│     - Set is_ledger_entry = FALSE                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  3. Batch save snapshots                                    │
│     repository.saveAll(snapshots)                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  4. Verification check                                      │
│     IF saved.size() != source.size() THEN                   │
│        ROLLBACK (transaction)                               │
│     END IF                                                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  5. Delete original transactions                            │
│     repository.deleteAllById(transactionIds)                │
└─────────────────────────────────────────────────────────────┘
```

**Safety Mechanisms**:
- ✅ Only completed groups moved (IN_PROGRESS preserved)
- ✅ Count verification before deletion
- ✅ Database transaction (atomic operation)
- ✅ Rollback on any error

**Performance**:
- Batch operations (saveAll, deleteAllById)
- Single database round-trip for queries
- Efficient for large transaction volumes

**Frequency**: Daily (configurable)

---

### Archiving: Tier 2 → Tier 3 (with Ledger Consolidation)

**Trigger**: Scheduled job (e.g., CRON monthly)

**Implementation**: `TransactionSnapshotService.archiveOldSnapshots(Integer walletId, LocalDateTime olderThan)`

**Process Flow**:

```
┌─────────────────────────────────────────────────────────────┐
│  1. Calculate cumulative balance                            │
│     SELECT COALESCE(SUM(amount), 0)                         │
│     FROM transaction_snapshot                               │
│     WHERE wallet_id = ?                                     │
│       AND confirm_reject_timestamp < ?                      │
│       AND is_ledger_entry = FALSE                           │
│       AND status = 'CONFIRMED'                              │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  2. Exit if balance is zero (nothing to archive)            │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  3. Create ledger entry                                     │
│     TransactionSnapshot ledger = new TransactionSnapshot(); │
│     ledger.setType(LEDGER);                                 │
│     ledger.setStatus(CONFIRMED);                            │
│     ledger.setAmount(cumulativeBalance);                    │
│     ledger.setIsLedgerEntry(TRUE);                          │
│     repository.save(ledger);                                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  4. Track consolidated reference IDs                        │
│     Find distinct reference_ids being archived              │
│     For each reference_id:                                  │
│         Create LedgerEntriesTracking entry                  │
│         Link ledger_entry_id to reference_id                │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  5. Bulk insert to archive (native SQL)                     │
│     INSERT INTO transaction_snapshot_archive                │
│     SELECT * FROM transaction_snapshot                      │
│     WHERE wallet_id = ?                                     │
│       AND confirm_reject_timestamp < ?                      │
│       AND is_ledger_entry = FALSE                           │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  6. Delete archived snapshots                               │
│     DELETE FROM transaction_snapshot                        │
│     WHERE (same conditions as insert)                       │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│  7. Verification check                                      │
│     IF archived_count != deleted_count THEN                 │
│        THROW exception (rollback transaction)               │
│     END IF                                                  │
└─────────────────────────────────────────────────────────────┘
```

**Safety Mechanisms**:
- ✅ Ledger entry created first (balance preserved)
- ✅ Tracking table links ledger to source transactions
- ✅ Count verification (archived = deleted)
- ✅ Database transaction (atomic operation)
- ✅ Exception thrown on mismatch (rollback)

**Performance**:
- Native SQL for bulk operations
- Single INSERT with SELECT (efficient)
- Single DELETE with conditions
- No row-by-row processing

**Ledger Consolidation Example**:

**Before Archiving**:
```sql
SELECT * FROM transaction_snapshot WHERE wallet_id = 123;

id   | amount | type   | status    | timestamp
-----|--------|--------|-----------|------------------
1    | +1000  | CREDIT | CONFIRMED | 2024-01-01 10:00
2    | -500   | DEBIT  | CONFIRMED | 2024-01-02 11:00
3    | +300   | CREDIT | CONFIRMED | 2024-01-03 12:00
...
100  | -200   | DEBIT  | CONFIRMED | 2024-03-31 15:00
─────────────────────────────────────────────────────────────
SUM: +5000

Balance query scans 100 rows
```

**After Archiving**:
```sql
-- transaction_snapshot (tier 2)
SELECT * FROM transaction_snapshot WHERE wallet_id = 123;

id   | amount | type   | status    | is_ledger_entry | timestamp
-----|--------|--------|-----------|-----------------|------------------
1001 | +5000  | LEDGER | CONFIRMED | TRUE            | 2024-04-01 02:00

-- transaction_snapshot_archive (tier 3)
SELECT * FROM transaction_snapshot_archive WHERE wallet_id = 123;

id   | amount | type   | status    | timestamp
-----|--------|--------|-----------|------------------
1    | +1000  | CREDIT | CONFIRMED | 2024-01-01 10:00
2    | -500   | DEBIT  | CONFIRMED | 2024-01-02 11:00
...
100  | -200   | DEBIT  | CONFIRMED | 2024-03-31 15:00

Balance query scans 1 row (ledger) + new transactions
```

**Tracking**:
```sql
SELECT * FROM ledger_entries_tracking WHERE ledger_entry_id = 1001;

id | ledger_entry_id | reference_id
---|-----------------|----------------------------------
1  | 1001           | uuid-of-group-1
2  | 1001           | uuid-of-group-2
...
50 | 1001           | uuid-of-group-50
```

**Frequency**: Monthly or quarterly (configurable based on volume)

---

## Ledger Entries Deep Dive

### What is a Ledger Entry?

A **ledger entry** is a special transaction snapshot row that represents the cumulative balance of multiple archived transactions.

**Characteristics**:
```java
TransactionSnapshot ledger = TransactionSnapshot.builder()
    .type(TransactionType.LEDGER)
    .status(TransactionStatus.CONFIRMED)
    .amount(cumulativeBalance)  // Sum of archived transactions
    .isLedgerEntry(true)
    .snapshotDate(LocalDateTime.now())
    .walletId(walletId)
    .referenceId(null)  // No specific group
    .description("Ledger consolidation")
    .build();
```

### Why Ledger Entries?

**Problem**: Even in tier 2 (snapshots), transaction count grows over time.

**Solution**: Replace old transactions with a single balance checkpoint.

**Benefits**:
- ✅ Reduces row count (100 rows → 1 row)
- ✅ Faster balance queries (sum fewer rows)
- ✅ Maintains accuracy (tracked in ledger_entries_tracking)
- ✅ Enables audit trail reconstruction

### Balance Queries with Ledgers

**Without Ledger**:
```sql
SELECT SUM(amount) FROM transaction_snapshot
WHERE wallet_id = ? AND status = 'CONFIRMED';
-- Scans ALL rows
```

**With Ledger**:
```sql
SELECT SUM(amount) FROM transaction_snapshot
WHERE wallet_id = ? AND status = 'CONFIRMED';
-- Scans ledger entry + newer transactions only
```

**Performance Impact**:
```
Wallet with 1 year of transactions (365,000 rows):

Without ledger: SUM(365,000 rows) → 500ms
With ledger:    SUM(1 ledger + 30,000 new rows) → 50ms

10x faster!
```

### Ledger Tracking Table

**Purpose**: Audit trail linking ledger entries to source transactions

**Table**: `ledger_entries_tracking`

**Schema**:
```sql
CREATE TABLE ledger_entries_tracking (
    id INTEGER PRIMARY KEY,
    ledger_entry_id INTEGER NOT NULL,  -- ID in transaction_snapshot
    reference_id UUID NOT NULL         -- Original transaction group
);
```

**Usage**:
```sql
-- Find all transaction groups consolidated into ledger entry 1001
SELECT reference_id
FROM ledger_entries_tracking
WHERE ledger_entry_id = 1001;

-- Find ledger entry for a specific transaction group
SELECT ledger_entry_id
FROM ledger_entries_tracking
WHERE reference_id = 'uuid-of-group';
```

**Reconstruction**:
If you need detailed history for a ledger period:
1. Find reference IDs from `ledger_entries_tracking`
2. Query archive table for those reference IDs
3. Verify sum matches ledger amount

---

## Query Patterns Across Tiers

### Available Balance (Tier 1 + Tier 2)

```sql
-- Confirmed balance from active and snapshots
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
    SELECT SUM(t.amount)
    FROM transaction t
    JOIN transaction_group tg ON t.reference_id = tg.id
    WHERE t.wallet_id = ?
      AND t.status = 'HOLD'
      AND tg.status = 'IN_PROGRESS'
), 0)
```

**Why Not Archive?**: Performance - archive rarely contains relevant data for current balance.

### Transaction History (All Tiers)

```sql
SELECT
    wallet_id,
    amount,
    type,
    status,
    COALESCE(confirm_reject_timestamp, hold_reserve_timestamp) AS timestamp
FROM (
    SELECT * FROM transaction WHERE wallet_id = ?
    UNION ALL
    SELECT * FROM transaction_snapshot WHERE wallet_id = ?
    UNION ALL
    SELECT * FROM transaction_snapshot_archive WHERE wallet_id = ?
) AS all_transactions
WHERE is_ledger_entry = FALSE  -- Exclude ledger entries
ORDER BY timestamp DESC;
```

### Paginated History (Tier 1 + Tier 2 Only)

```sql
SELECT * FROM (
    SELECT * FROM transaction WHERE wallet_id = ?
    UNION ALL
    SELECT * FROM transaction_snapshot WHERE wallet_id = ?
) AS recent_transactions
WHERE is_ledger_entry = FALSE
ORDER BY confirm_reject_timestamp DESC
LIMIT ? OFFSET ?;
```

**Why Not Archive?**: Performance - pagination typically for recent history.

### System Reconciliation (All Tiers)

```sql
WITH confirmed_groups AS (
    SELECT id FROM transaction_group WHERE status = 'CONFIRMED'
)
SELECT COALESCE(SUM(amount), 0) FROM (
    SELECT SUM(amount) AS amount
    FROM transaction
    WHERE reference_id IN (SELECT id FROM confirmed_groups)

    UNION ALL

    SELECT SUM(amount) AS amount
    FROM transaction_snapshot
    WHERE reference_id IN (SELECT id FROM confirmed_groups)

    UNION ALL

    SELECT SUM(amount) AS amount
    FROM transaction_snapshot_archive
    WHERE reference_id IN (SELECT id FROM confirmed_groups)
) AS all_amounts;
```

**Note**: Heavy query - run periodically for audit purposes only.

---

## Configuration and Tuning

### Snapshot Frequency

**Default**: Daily

**Tuning Factors**:
- Transaction volume (high volume → more frequent)
- Query performance (slow queries → more frequent)
- Batch processing window (off-peak hours preferred)

**Recommendation**:
```
< 10,000 txn/day:   Daily snapshot @ 2 AM
10K-100K txn/day:   Twice daily @ 2 AM and 2 PM
> 100K txn/day:     Hourly or real-time (streaming)
```

### Archive Frequency

**Default**: Monthly

**Tuning Factors**:
- Snapshot table size (> 1M rows → more frequent)
- Query performance on snapshots
- Business requirements for recent history

**Recommendation**:
```
Keep 3-6 months in snapshots
Archive older data monthly or quarterly
```

### Ledger Frequency

**Default**: Same as archiving

**Tuning Factors**:
- Snapshot table size
- Balance query performance
- Historical accuracy requirements

**Recommendation**:
```
Create ledger entries during archiving
Consolidate transactions older than retention period
```

### Storage Optimization

**Active Table**:
- High-performance SSD
- Frequent vacuuming
- Smaller block size for row-level updates

**Snapshot Table**:
- Standard SSD
- Regular vacuuming
- Standard block size

**Archive Table**:
- Cheaper storage (HDD acceptable)
- Infrequent vacuuming
- Larger block size (read-optimized)
- Consider table partitioning by year

---

## Monitoring and Maintenance

### Key Metrics

**Tier 1 (Active)**:
```sql
-- Row count
SELECT COUNT(*) FROM transaction;

-- Oldest transaction
SELECT MIN(hold_reserve_timestamp) FROM transaction;

-- In-progress groups
SELECT COUNT(*) FROM transaction_group WHERE status = 'IN_PROGRESS';
```

**Tier 2 (Snapshots)**:
```sql
-- Row count
SELECT COUNT(*) FROM transaction_snapshot WHERE is_ledger_entry = FALSE;

-- Ledger entry count
SELECT COUNT(*) FROM transaction_snapshot WHERE is_ledger_entry = TRUE;

-- Oldest snapshot
SELECT MIN(snapshot_date) FROM transaction_snapshot WHERE is_ledger_entry = FALSE;
```

**Tier 3 (Archive)**:
```sql
-- Row count
SELECT COUNT(*) FROM transaction_snapshot_archive;

-- Storage size
SELECT pg_size_pretty(pg_total_relation_size('transaction_snapshot_archive'));
```

### Alert Thresholds

```
Active table > 100,000 rows → Increase snapshot frequency
Snapshot table > 1,000,000 rows → Increase archive frequency
Archive table > 10,000,000 rows → Consider partitioning
```

### Vacuum and Analyze

```sql
-- After daily snapshot
VACUUM ANALYZE transaction;
VACUUM ANALYZE transaction_snapshot;

-- After archiving
VACUUM FULL transaction_snapshot;  -- Reclaim space
ANALYZE transaction_snapshot_archive;
```

---

## Related Documentation

- [Data Model](./data-model.md) - Database schema details
- [Transaction Lifecycle](./transaction-lifecycle.md) - Transaction flows
- [Snapshot & Archiving](../operations/snapshot-archiving.md) - Operational procedures
- [Monitoring & Reconciliation](../operations/monitoring.md) - System monitoring
