# Three-Tier Archiving Architecture

**Date**: 2025-12-27
**Version**: 1.0.5
**Status**: ✅ Production Ready

---

## 🎯 Overview

This document describes the three-tier archiving architecture that balances **performance** (operational efficiency) with **compliance** (immutable audit trail).

---

## 📊 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    BANKING LEDGER SYSTEM                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  transaction (Hot Data - Operational)                           │
│  ├─ Current transactions (last 1-30 days)                      │
│  ├─ High-frequency reads/writes                                │
│  ├─ UPDATE: ❌ Blocked (immutability)                          │
│  └─ DELETE: ✅ Allowed (archiving)                             │
│                                                                 │
│                    ↓ captureDailySnapshotForWallet()            │
│                                                                 │
│  transaction_snapshot (Warm Data - Analytical)                  │
│  ├─ Recent completed transactions (30-365 days)                │
│  ├─ LEDGER entries (balance checkpoints)                       │
│  ├─ UPDATE: ❌ Blocked (immutability)                          │
│  └─ DELETE: ✅ Allowed (archiving)                             │
│                                                                 │
│                    ↓ archiveOldSnapshots()                      │
│                                                                 │
│  transaction_snapshot_archive (Cold Data - Compliance)          │
│  ├─ Historical transactions (365+ days)                        │
│  ├─ Permanent audit trail                                      │
│  ├─ UPDATE: ❌ Blocked (immutability)                          │
│  └─ DELETE: ❌ Blocked (IMMUTABLE LEDGER)                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔄 Data Flow

### Step 1: Transaction Creation
```sql
INSERT INTO transaction (
    wallet_id, amount, type, status, reference_id
) VALUES (
    1, -100, 'DEBIT', 'HOLD', 'uuid-123'
);
```

### Step 2: Settlement
```sql
-- Hold → Settled (append-only)
INSERT INTO transaction (
    wallet_id, amount, type, status, reference_id
) VALUES (
    1, -100, 'DEBIT', 'SETTLED', 'uuid-123'
);
```

### Step 3: Daily Snapshot (Next Day)
```java
// captureDailySnapshotForWallet(walletId)

// 1. Copy to snapshot
INSERT INTO transaction_snapshot
SELECT * FROM transaction
WHERE wallet_id = :walletId
  AND status IN ('SETTLED', 'RELEASED', 'CANCELLED');

// 2. Delete from hot table (performance optimization)
DELETE FROM transaction
WHERE wallet_id = :walletId
  AND reference_id IN (:snapshotted_refs);
```

**Result:**
- `transaction`: Empty or minimal (high performance)
- `transaction_snapshot`: Contains recent history
- Balance calculation: Fast (smaller dataset)

### Step 4: Archive Old Snapshots (Monthly)
```java
// archiveOldSnapshots(walletId, olderThan)

// 1. Calculate cumulative balance
cumulativeBalance = SUM(amount)
    FROM transaction_snapshot
    WHERE wallet_id = :walletId
      AND snapshot_date < :olderThan;

// 2. Create LEDGER checkpoint
INSERT INTO transaction_snapshot (
    wallet_id, amount, type, status,
    is_ledger_entry, snapshot_date
) VALUES (
    :walletId, :cumulativeBalance, 'LEDGER', 'SETTLED',
    TRUE, NOW()
);

// 3. Copy old snapshots to archive
INSERT INTO transaction_snapshot_archive
SELECT * FROM transaction_snapshot
WHERE wallet_id = :walletId
  AND snapshot_date < :olderThan
  AND is_ledger_entry = FALSE;

// 4. Delete old snapshots (keep LEDGER entries)
DELETE FROM transaction_snapshot
WHERE wallet_id = :walletId
  AND snapshot_date < :olderThan
  AND is_ledger_entry = FALSE;
```

**Result:**
- `transaction_snapshot`: Only LEDGER checkpoints + recent data
- `transaction_snapshot_archive`: Full historical data (immutable)
- Balance calculation: Ultra-fast (LEDGER checkpoints)

---

## 💡 LEDGER Checkpoints

**Problem:** Calculating balance from millions of transactions is slow.

**Solution:** LEDGER entries consolidate historical balance.

### Example:

**Before archiving (1000 transactions):**
```sql
-- Balance calculation: slow
SELECT SUM(amount) FROM transaction_snapshot
WHERE wallet_id = 1;
-- Sums 1000 rows: +100, -50, +200, ... = 5000₽
```

**After archiving (1 LEDGER entry + recent):**
```sql
-- Balance calculation: fast
SELECT SUM(amount) FROM transaction_snapshot
WHERE wallet_id = 1;
-- Sums 1 LEDGER entry: 5000₽ + recent transactions
```

**LEDGER entry structure:**
```sql
INSERT INTO transaction_snapshot (
    wallet_id = 1,
    amount = 5000,              -- Cumulative balance
    type = 'LEDGER',            -- Special type
    status = 'SETTLED',
    is_ledger_entry = TRUE,     -- Marker
    snapshot_date = '2025-12-01'
);
```

---

## 🛡️ Immutability Strategy

### Records Cannot Be Modified (UPDATE blocked everywhere)

**All tables:**
```sql
CREATE TRIGGER trg_prevent_transaction_update
    BEFORE UPDATE ON transaction
    FOR EACH ROW
EXECUTE FUNCTION prevent_transaction_update();

-- Same for transaction_snapshot and transaction_snapshot_archive
```

**Result:** Data integrity guaranteed. Once written, transaction details are permanent.

### Archiving Allowed (DELETE controlled)

**Hot/Warm tables:**
```sql
-- DELETE allowed for archiving
-- No trigger blocking DELETE on transaction and transaction_snapshot
```

**Cold table:**
```sql
CREATE TRIGGER trg_prevent_archive_delete
    BEFORE DELETE ON transaction_snapshot_archive
    FOR EACH ROW
EXECUTE FUNCTION prevent_archive_delete();
```

**Result:**
- ✅ Performance optimization (hot table stays small)
- ✅ Full audit trail (archive is immutable)
- ✅ Compliance (final ledger never deleted)

---

## 📈 Balance Calculation

### Formula:
```sql
availableBalance = settledBalance - holdBalance

settledBalance = SUM(
    SELECT amount FROM transaction WHERE status = 'SETTLED'
    UNION ALL
    SELECT amount FROM transaction_snapshot WHERE status = 'SETTLED'
)

holdBalance = SUM(amount)
    FROM transaction
    WHERE status = 'HOLD'
      AND type = 'DEBIT'
      AND transaction_group.status = 'IN_PROGRESS'
```

### Why UNION ALL?

**Multi-tier data:**
- Recent transactions in `transaction` table
- Historical data in `transaction_snapshot` (including LEDGER entries)
- No duplication (data moved from transaction to snapshot)

**Example:**
```sql
-- Day 1: Transaction created
transaction: (amount=100, status=SETTLED)
Balance = 100₽

-- Day 2: After snapshot
transaction: (empty - archived)
transaction_snapshot: (amount=100, status=SETTLED)
Balance = 100₽ (no double counting!)

-- Day 30: After archive
transaction_snapshot: LEDGER(amount=5000₽) + recent transactions
transaction_snapshot_archive: old snapshots (not in balance query)
Balance = 5000₽ + recent (fast calculation!)
```

---

## ✅ Benefits

### 1. Performance
- **Hot table stays small** (only current data)
- **Fast queries** (minimal rows to scan)
- **LEDGER checkpoints** (no need to sum millions of rows)

### 2. Compliance
- **Full audit trail** (all data preserved in archive)
- **Immutable final storage** (archive cannot be modified or deleted)
- **Complete history** (transaction + snapshot + archive = full ledger)

### 3. Flexibility
- **Aggressive archiving** (can archive frequently without data loss)
- **Configurable retention** (hot: 1 day, warm: 30 days, cold: forever)
- **Scalability** (unlimited historical data in archive)

### 4. Operational
- **No impact on current operations** (archive runs offline)
- **Reversible** (can always read from archive if needed)
- **Monitored** (ledger_validation view tracks all tiers)

---

## 🚀 Deployment

### Migration Files:
1. `V2.01__Update_transaction_statuses.sql` - Status enum update
2. `V2.02__Ledger_immutability_constraints.sql` - Initial immutability
3. `V2.03__Adjust_immutability_for_archiving.sql` - Three-tier architecture

### Operations:

**Daily:**
```java
// Run for active wallets
transactionSnapshotService.captureDailySnapshotForWallet(walletId);
```

**Monthly:**
```java
// Archive snapshots older than 30 days
LocalDateTime olderThan = LocalDateTime.now().minusDays(30);
transactionSnapshotService.archiveOldSnapshots(walletId, olderThan);
```

**Monitoring:**
```sql
-- Check ledger health
SELECT * FROM ledger_validation;

-- Expected output:
-- Total transactions (active): small number
-- Total snapshots (warm): moderate number
-- Total archived (cold): large number (growing)
-- Zero-sum checks: 0 (all tiers)
```

---

## 📚 See Also

- `README.md` - System architecture overview
- `RELEASES.md` v1.0.5 - Full implementation details
- `docs/analysis/FINAL_LEDGER_VERIFICATION.md` - Ledger compliance verification
- `V2.03__Adjust_immutability_for_archiving.sql` - Database migration

---

**Status:** ✅ Production Ready
**Compliance:** 100% Banking Ledger Standards
**Performance:** Optimized for high-volume transactions
**Audit Trail:** Complete and immutable
