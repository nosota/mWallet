# Snapshot & Archiving Operations

This guide describes how to configure and operate the snapshot and archiving system in mWallet.

## Overview

mWallet uses a three-tier storage architecture that requires periodic data migration:

1. **Daily Snapshots**: Move completed transactions from active to snapshot storage
2. **Periodic Archiving**: Move old snapshots to archive with ledger consolidation

Both operations must be scheduled and executed externally (not built into the service).

## Daily Snapshot Operations

### Purpose

Move completed transactions from the `transaction` table to `transaction_snapshot` table to keep the active table small and performant.

### When to Run

**Frequency**: Daily (recommended: 2-4 AM during low-traffic period)

**Triggers**:
- Time-based (CRON schedule)
- Transaction volume threshold (> 10,000 active transactions)
- Performance degradation detection

### How It Works

The snapshot process:

1. Queries transactions with CONFIRMED or REJECTED groups
2. Copies them to `transaction_snapshot` table
3. Verifies count matches
4. Deletes original transactions (if verification passes)
5. Rollback on any error

**Safety Guarantees**:
- ✅ IN_PROGRESS groups never moved (data safety)
- ✅ Count verification before deletion
- ✅ Database transaction (atomic operation)
- ✅ Rollback on verification failure

### Implementation Options

#### Option 1: Scheduled Task (Spring @Scheduled)

```java
@Component
@Slf4j
public class SnapshotScheduler {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionSnapshotService snapshotService;

    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void dailySnapshot() {
        log.info("Starting daily snapshot process");

        List<Integer> walletIds = walletRepository.findAllIds();

        for (Integer walletId : walletIds) {
            try {
                snapshotService.captureDailySnapshotForWallet(walletId);
                log.info("Snapshot completed for wallet {}", walletId);
            } catch (Exception e) {
                log.error("Snapshot failed for wallet {}: {}",
                    walletId, e.getMessage(), e);
                // Continue with other wallets
            }
        }

        log.info("Daily snapshot process completed");
    }
}
```

**Configuration** (`application.yaml`):
```yaml
spring:
  task:
    scheduling:
      pool:
        size: 2
```

**Pros**:
- Simple implementation
- Built into application
- Automatic execution

**Cons**:
- Requires application running
- Resource intensive
- Blocks during execution

#### Option 2: External CRON Job

```bash
#!/bin/bash
# snapshot.sh

# Configuration
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="mwallet"
DB_USER="mwallet_user"
DB_PASS="mwallet_pass"

# Log file
LOG_FILE="/var/log/mwallet/snapshot-$(date +%Y%m%d).log"

echo "$(date): Starting daily snapshot" >> $LOG_FILE

# Call stored procedure or use psql
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME <<EOF
SELECT mwallet.capture_daily_snapshots();
EOF

echo "$(date): Daily snapshot completed" >> $LOG_FILE
```

**CRON Configuration**:
```cron
# Run daily at 2 AM
0 2 * * * /path/to/snapshot.sh
```

**Pros**:
- Independent of application
- Can run when application is down
- Better for large-scale operations

**Cons**:
- Requires database stored procedure
- Additional infrastructure

#### Option 3: Spring Batch Job

```java
@Configuration
public class SnapshotBatchConfig {

    @Bean
    public Job snapshotJob(JobRepository jobRepository, Step snapshotStep) {
        return new JobBuilder("snapshotJob", jobRepository)
            .start(snapshotStep)
            .build();
    }

    @Bean
    public Step snapshotStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              SnapshotTasklet snapshotTasklet) {
        return new StepBuilder("snapshotStep", jobRepository)
            .tasklet(snapshotTasklet, transactionManager)
            .build();
    }
}

@Component
public class SnapshotTasklet implements Tasklet {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionSnapshotService snapshotService;

    @Override
    public RepeatStatus execute(StepContribution contribution,
                                 ChunkContext chunkContext) {
        List<Integer> walletIds = walletRepository.findAllIds();

        for (Integer walletId : walletIds) {
            snapshotService.captureDailySnapshotForWallet(walletId);
        }

        return RepeatStatus.FINISHED;
    }
}
```

**Pros**:
- Professional batch processing
- Built-in retry and error handling
- Job history and monitoring

**Cons**:
- More complex setup
- Requires Spring Batch knowledge

### Recommended Configuration

**For Small Deployments** (< 1,000 wallets):
- Use Spring @Scheduled with CRON expression
- Run at 2 AM daily

**For Medium Deployments** (1,000-10,000 wallets):
- Use Spring Batch for better control
- Run at 2 AM daily or twice daily

**For Large Deployments** (> 10,000 wallets):
- Use external CRON with database procedure
- Run hourly or near real-time

### Monitoring Snapshot Process

#### Key Metrics

```sql
-- Active transaction count
SELECT COUNT(*) FROM transaction;

-- Oldest active transaction
SELECT MIN(hold_reserve_timestamp) FROM transaction;

-- Snapshot count
SELECT COUNT(*) FROM transaction_snapshot
WHERE is_ledger_entry = FALSE;

-- Transactions not yet snapshotted (with completed groups)
SELECT COUNT(t.id)
FROM transaction t
JOIN transaction_group tg ON t.reference_id = tg.id
WHERE tg.status IN ('CONFIRMED', 'REJECTED');
```

#### Alert Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| Active transactions | > 50,000 | > 100,000 |
| Oldest transaction age | > 2 days | > 7 days |
| Unsnapshotted completed transactions | > 10,000 | > 50,000 |

#### Health Check

```java
@Component
public class SnapshotHealthIndicator implements HealthIndicator {

    @Autowired
    private TransactionRepository transactionRepository;

    @Override
    public Health health() {
        long activeCount = transactionRepository.count();

        if (activeCount > 100_000) {
            return Health.down()
                .withDetail("active_transactions", activeCount)
                .withDetail("message", "Critical: Too many active transactions")
                .build();
        } else if (activeCount > 50_000) {
            return Health.status("WARNING")
                .withDetail("active_transactions", activeCount)
                .withDetail("message", "Warning: High active transaction count")
                .build();
        }

        return Health.up()
            .withDetail("active_transactions", activeCount)
            .build();
    }
}
```

### Troubleshooting Snapshots

#### Issue: Snapshot Taking Too Long

**Symptoms**:
- Process runs for > 1 hour
- Database CPU/IO high

**Solutions**:
1. **Batch Processing**: Process wallets in batches
   ```java
   List<Integer> walletIds = walletRepository.findAllIds();
   Lists.partition(walletIds, 100).forEach(batch -> {
       batch.parallelStream().forEach(walletId -> {
           snapshotService.captureDailySnapshotForWallet(walletId);
       });
   });
   ```

2. **Parallel Execution**: Use thread pool
   ```java
   ExecutorService executor = Executors.newFixedThreadPool(10);
   walletIds.forEach(walletId ->
       executor.submit(() ->
           snapshotService.captureDailySnapshotForWallet(walletId)
       )
   );
   executor.shutdown();
   executor.awaitTermination(1, TimeUnit.HOURS);
   ```

3. **Increase Frequency**: Run twice daily instead of once

#### Issue: Verification Failures

**Symptoms**:
- Error: "Verification failed: expected X, got Y"

**Causes**:
- Concurrent transaction creation during snapshot
- Database replication lag

**Solutions**:
1. Run during low-traffic period
2. Use pessimistic locking if needed
3. Check database replication lag

#### Issue: OUT_OF_MEMORY Errors

**Symptoms**:
- `OutOfMemoryError` during snapshot

**Causes**:
- Loading too many transactions at once

**Solutions**:
```java
// Use pagination in repository
@Query("SELECT t FROM Transaction t WHERE ...")
Page<Transaction> findTransactionsToSnapshot(Pageable pageable);

// Process in pages
int pageSize = 1000;
Page<Transaction> page;
int pageNum = 0;

do {
    page = repository.findTransactionsToSnapshot(
        PageRequest.of(pageNum++, pageSize)
    );
    // Process page
} while (page.hasNext());
```

---

## Periodic Archiving Operations

### Purpose

Move old snapshots to archive storage with ledger consolidation to:
- Keep snapshot table performant
- Reduce row count via ledger entries
- Maintain complete historical record

### When to Run

**Frequency**: Monthly or Quarterly (recommended: 1st of month at 3 AM)

**Criteria**:
- Snapshot table > 1 million rows
- Snapshots older than retention period (3-6 months)
- Query performance degradation

### How It Works

The archiving process:

1. Calculates cumulative balance of old snapshots
2. Creates ledger entry with that balance
3. Tracks which reference IDs were consolidated
4. Bulk inserts snapshots to archive table
5. Bulk deletes from snapshot table
6. Verifies archived count = deleted count

**Result**: 100+ old transactions replaced with 1 ledger entry

### Implementation

#### Spring Scheduled Task

```java
@Component
@Slf4j
public class ArchiveScheduler {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionSnapshotService snapshotService;

    @Value("${mwallet.archive.retention-months:3}")
    private int retentionMonths;

    @Scheduled(cron = "0 0 3 1 * *") // 3 AM on 1st of month
    public void monthlyArchive() {
        log.info("Starting monthly archive process");

        LocalDateTime cutoff = LocalDateTime.now().minusMonths(retentionMonths);
        List<Integer> walletIds = walletRepository.findAllIds();

        int successCount = 0;
        int errorCount = 0;

        for (Integer walletId : walletIds) {
            try {
                snapshotService.archiveOldSnapshots(walletId, cutoff);
                successCount++;
                log.info("Archive completed for wallet {}", walletId);
            } catch (Exception e) {
                errorCount++;
                log.error("Archive failed for wallet {}: {}",
                    walletId, e.getMessage(), e);
            }
        }

        log.info("Monthly archive completed: {} success, {} errors",
            successCount, errorCount);
    }
}
```

**Configuration** (`application.yaml`):
```yaml
mwallet:
  archive:
    retention-months: 3  # Keep 3 months in snapshots
```

### Retention Policy

Recommended retention periods:

| Business Type | Retention in Snapshots | Archive Age |
|---------------|------------------------|-------------|
| High Volume (e-commerce) | 1-2 months | > 2 months |
| Medium Volume | 3-6 months | > 6 months |
| Low Volume | 6-12 months | > 12 months |

**Factors**:
- Query frequency for historical data
- Compliance requirements
- Storage costs
- Performance requirements

### Monitoring Archiving Process

#### Key Metrics

```sql
-- Snapshot count (excluding ledgers)
SELECT COUNT(*) FROM transaction_snapshot
WHERE is_ledger_entry = FALSE;

-- Ledger entry count
SELECT COUNT(*) FROM transaction_snapshot
WHERE is_ledger_entry = TRUE;

-- Archive count
SELECT COUNT(*) FROM transaction_snapshot_archive;

-- Oldest snapshot
SELECT MIN(snapshot_date) FROM transaction_snapshot
WHERE is_ledger_entry = FALSE;

-- Storage sizes
SELECT
    pg_size_pretty(pg_total_relation_size('transaction_snapshot')) AS snapshot_size,
    pg_size_pretty(pg_total_relation_size('transaction_snapshot_archive')) AS archive_size;
```

#### Health Indicators

```java
@Component
public class ArchiveHealthIndicator implements HealthIndicator {

    @Autowired
    private TransactionSnapshotRepository snapshotRepository;

    @Override
    public Health health() {
        long snapshotCount = snapshotRepository.countByIsLedgerEntry(false);
        LocalDateTime oldestSnapshot = snapshotRepository.findOldestSnapshot();

        if (snapshotCount > 10_000_000) {
            return Health.down()
                .withDetail("snapshot_count", snapshotCount)
                .withDetail("message", "Critical: Archive immediately")
                .build();
        } else if (snapshotCount > 5_000_000) {
            return Health.status("WARNING")
                .withDetail("snapshot_count", snapshotCount)
                .withDetail("message", "Warning: Consider archiving")
                .build();
        }

        Duration age = Duration.between(oldestSnapshot, LocalDateTime.now());
        if (age.toDays() > 365) {
            return Health.status("WARNING")
                .withDetail("oldest_snapshot_age_days", age.toDays())
                .withDetail("message", "Warning: Old snapshots detected")
                .build();
        }

        return Health.up()
            .withDetail("snapshot_count", snapshotCount)
            .withDetail("oldest_snapshot_age_days", age.toDays())
            .build();
    }
}
```

### Ledger Entry Management

#### Understanding Ledger Entries

A ledger entry is a special transaction snapshot row:

```java
TransactionSnapshot ledger = TransactionSnapshot.builder()
    .walletId(walletId)
    .type(TransactionType.LEDGER)
    .status(TransactionStatus.CONFIRMED)
    .amount(cumulativeBalance)  // Sum of archived transactions
    .isLedgerEntry(true)
    .snapshotDate(LocalDateTime.now())
    .confirmRejectTimestamp(LocalDateTime.now())
    .description("Ledger consolidation")
    .build();
```

#### Ledger Tracking

Every ledger entry is tracked in `ledger_entries_tracking`:

```sql
SELECT
    l.id,
    l.ledger_entry_id,
    l.reference_id,
    s.amount AS ledger_amount,
    s.snapshot_date
FROM ledger_entries_tracking l
JOIN transaction_snapshot s ON l.ledger_entry_id = s.id
WHERE s.wallet_id = ?;
```

#### Verifying Ledger Accuracy

```sql
-- Sum of ledger entries
SELECT COALESCE(SUM(amount), 0) AS ledger_total
FROM transaction_snapshot
WHERE wallet_id = ? AND is_ledger_entry = TRUE;

-- Sum of archived transactions for ledger 1001
SELECT COALESCE(SUM(amount), 0) AS archived_total
FROM transaction_snapshot_archive
WHERE wallet_id = ?
  AND reference_id IN (
      SELECT reference_id
      FROM ledger_entries_tracking
      WHERE ledger_entry_id = 1001
  );

-- Should match!
```

### Troubleshooting Archiving

#### Issue: Zero Balance on Archive

**Symptoms**:
- Archive skipped: "Balance is zero, nothing to archive"

**Cause**:
- Internal transfers cancel out
- Only initial balance remains

**Solution**: This is normal behavior. No action needed.

#### Issue: Archived Count Mismatch

**Symptoms**:
- Error: "Archived 1000 but deleted 999"

**Cause**:
- Concurrent snapshot creation
- Database inconsistency

**Solution**:
1. Check database logs
2. Re-run archiving (transaction rolled back, safe to retry)
3. If persists, investigate database replication

#### Issue: Slow Archiving

**Symptoms**:
- Archive takes > 30 minutes per wallet

**Solutions**:
1. **Index Optimization**:
   ```sql
   CREATE INDEX IF NOT EXISTS idx_snapshot_wallet_timestamp
   ON transaction_snapshot(wallet_id, confirm_reject_timestamp)
   WHERE is_ledger_entry = FALSE;
   ```

2. **Batch Archiving**:
   ```java
   // Archive in smaller date ranges
   for (int month = 12; month > 3; month--) {
       LocalDateTime cutoff = LocalDateTime.now().minusMonths(month);
       snapshotService.archiveOldSnapshots(walletId, cutoff);
   }
   ```

3. **Parallel Processing**: Archive multiple wallets concurrently

---

## Best Practices

### Scheduling

1. **Run During Off-Peak Hours**
   - Snapshots: 2-4 AM
   - Archiving: 3-5 AM on weekends

2. **Stagger Operations**
   - Don't run snapshot and archive simultaneously
   - Space operations 1+ hour apart

3. **Monitor Performance**
   - Track execution time
   - Alert on > 50% increase
   - Optimize queries if needed

### Safety

1. **Always Test First**
   - Test on staging environment
   - Verify counts and balances
   - Check performance impact

2. **Enable Monitoring**
   - Log all operations
   - Track success/failure rates
   - Alert on errors

3. **Have Rollback Plan**
   - Keep database backups
   - Document recovery procedures
   - Test restore process

### Performance

1. **Optimize Database**
   - Keep indexes up to date
   - Run VACUUM regularly
   - Monitor query plans

2. **Batch Operations**
   - Process wallets in batches
   - Use parallel processing
   - Limit transaction size

3. **Scale Appropriately**
   - Increase frequency for high volume
   - Add resources if needed
   - Consider partitioning for very large datasets

### Compliance

1. **Retain Complete History**
   - Never delete from archive
   - Keep ledger tracking intact
   - Document archiving policy

2. **Audit Trail**
   - Log all operations
   - Track who/when/what
   - Keep logs for compliance period

3. **Data Integrity**
   - Verify balances after operations
   - Check reconciliation regularly
   - Document verification procedures

---

## Related Documentation

- [Storage Tiers Architecture](../architecture/storage-tiers.md) - Deep dive into storage design
- [Monitoring & Reconciliation](./monitoring.md) - System monitoring guide
- [Service API Reference](../api/services.md) - API documentation
