# Monitoring & Reconciliation

This guide covers system monitoring, balance reconciliation, and operational health checks for mWallet.

## Overview

Effective monitoring ensures:
- **Data Integrity**: Balances are accurate
- **Performance**: System operates efficiently
- **Reliability**: Issues detected early
- **Compliance**: Audit requirements met

## Key Monitoring Areas

1. [System Health](#system-health-monitoring)
2. [Balance Reconciliation](#balance-reconciliation)
3. [Transaction Monitoring](#transaction-monitoring)
4. [Performance Metrics](#performance-metrics)
5. [Storage Health](#storage-health)
6. [Alert Configuration](#alert-configuration)

---

## System Health Monitoring

### Application Health Endpoints

Spring Boot Actuator provides health endpoints:

#### Configuration

```yaml
# application.yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
      base-path: /actuator
  endpoint:
    health:
      show-details: always
  health:
    db:
      enabled: true
```

#### Health Checks

```bash
# Overall health
curl http://localhost:8080/actuator/health

{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "snapshotHealth": {
      "status": "UP",
      "details": {
        "active_transactions": 1234
      }
    }
  }
}
```

### Custom Health Indicators

#### Snapshot Health Indicator

```java
@Component
public class SnapshotHealthIndicator implements HealthIndicator {

    @Autowired
    private TransactionRepository transactionRepository;

    @Override
    public Health health() {
        try {
            long activeCount = transactionRepository.count();

            if (activeCount > 100_000) {
                return Health.down()
                    .withDetail("active_transactions", activeCount)
                    .withDetail("action", "Run snapshot immediately")
                    .build();
            } else if (activeCount > 50_000) {
                return Health.status("WARNING")
                    .withDetail("active_transactions", activeCount)
                    .withDetail("action", "Consider running snapshot")
                    .build();
            }

            return Health.up()
                .withDetail("active_transactions", activeCount)
                .build();

        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

#### Balance Integrity Health Indicator

```java
@Component
public class BalanceIntegrityHealthIndicator implements HealthIndicator {

    @Autowired
    private SystemStatisticService systemStatisticService;

    @Autowired
    private WalletRepository walletRepository;

    @Override
    public Health health() {
        try {
            // System reconciliation balance
            Long systemBalance =
                systemStatisticService.getReconciliationBalanceOfAllConfirmedGroups();

            // Expected: sum of initial wallet balances
            // (This would require tracking initial balances)

            return Health.up()
                .withDetail("system_balance", systemBalance)
                .withDetail("system_balance_formatted",
                    String.format("$%.2f", systemBalance / 100.0))
                .build();

        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .build();
        }
    }
}
```

---

## Balance Reconciliation

### Daily Reconciliation Check

Verify system-wide balance integrity daily.

#### Reconciliation Query

```sql
-- System reconciliation balance (sum of all initial balances)
WITH confirmed_groups AS (
    SELECT id FROM transaction_group WHERE status = 'CONFIRMED'
),
all_transactions AS (
    SELECT reference_id, SUM(amount) AS group_total
    FROM transaction
    WHERE reference_id IN (SELECT id FROM confirmed_groups)
    GROUP BY reference_id

    UNION ALL

    SELECT reference_id, SUM(amount) AS group_total
    FROM transaction_snapshot
    WHERE reference_id IN (SELECT id FROM confirmed_groups)
    GROUP BY reference_id

    UNION ALL

    SELECT reference_id, SUM(amount) AS group_total
    FROM transaction_snapshot_archive
    WHERE reference_id IN (SELECT id FROM confirmed_groups)
    GROUP BY reference_id
)
SELECT COALESCE(SUM(group_total), 0) AS reconciliation_balance
FROM all_transactions;
```

#### Reconciliation Service

```java
@Service
@Slf4j
public class ReconciliationService {

    @Autowired
    private SystemStatisticService systemStatisticService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletBalanceService walletBalanceService;

    public ReconciliationReport performDailyReconciliation() {
        log.info("Starting daily reconciliation");

        // System-wide balance
        Long systemBalance =
            systemStatisticService.getReconciliationBalanceOfAllConfirmedGroups();

        // Sum of all wallet balances
        List<Integer> walletIds = walletRepository.findAllIds();
        Long totalWalletBalance = walletIds.stream()
            .map(walletBalanceService::getAvailableBalance)
            .reduce(0L, Long::sum);

        // They should match (or close, accounting for holds)
        Long difference = Math.abs(systemBalance - totalWalletBalance);

        ReconciliationReport report = ReconciliationReport.builder()
            .timestamp(LocalDateTime.now())
            .systemBalance(systemBalance)
            .totalWalletBalance(totalWalletBalance)
            .difference(difference)
            .status(difference == 0 ? "BALANCED" : "MISMATCH")
            .build();

        if (difference > 0) {
            log.warn("Reconciliation mismatch: {} cents difference", difference);
            // Send alert
        } else {
            log.info("Reconciliation successful: system balanced");
        }

        return report;
    }

    @Scheduled(cron = "0 0 6 * * *") // 6 AM daily
    public void scheduledReconciliation() {
        performDailyReconciliation();
    }
}
```

#### Reconciliation Report

```java
@Data
@Builder
public class ReconciliationReport {
    private LocalDateTime timestamp;
    private Long systemBalance;
    private Long totalWalletBalance;
    private Long difference;
    private String status;

    public String getFormattedSystemBalance() {
        return String.format("$%.2f", systemBalance / 100.0);
    }

    public String getFormattedTotalWalletBalance() {
        return String.format("$%.2f", totalWalletBalance / 100.0);
    }

    public String getFormattedDifference() {
        return String.format("$%.2f", difference / 100.0);
    }
}
```

### Per-Wallet Balance Verification

Verify individual wallet balances against transaction history.

```java
public void verifyWalletBalance(Integer walletId) {
    // Current available balance
    Long currentBalance = walletBalanceService.getAvailableBalance(walletId);

    // Calculate from transaction history
    Long calculatedBalance = calculateBalanceFromHistory(walletId);

    if (!currentBalance.equals(calculatedBalance)) {
        log.error("Balance mismatch for wallet {}: current={}, calculated={}",
            walletId, currentBalance, calculatedBalance);
        // Alert and investigate
    }
}

private Long calculateBalanceFromHistory(Integer walletId) {
    List<TransactionHistoryDTO> history =
        transactionHistoryService.getFullTransactionHistory(walletId);

    return history.stream()
        .filter(t -> t.getStatus().equals("CONFIRMED"))
        .mapToLong(TransactionHistoryDTO::getAmount)
        .sum();
}
```

### Ledger Entry Verification

Verify ledger entries match archived transactions.

```sql
-- For each ledger entry
SELECT
    l.ledger_entry_id,
    s.amount AS ledger_amount,
    (
        SELECT COALESCE(SUM(amount), 0)
        FROM transaction_snapshot_archive
        WHERE reference_id IN (
            SELECT reference_id
            FROM ledger_entries_tracking
            WHERE ledger_entry_id = l.ledger_entry_id
        )
    ) AS archived_amount,
    CASE
        WHEN s.amount = archived_amount THEN 'OK'
        ELSE 'MISMATCH'
    END AS status
FROM ledger_entries_tracking l
JOIN transaction_snapshot s ON l.ledger_entry_id = s.id
WHERE s.is_ledger_entry = TRUE;
```

---

## Transaction Monitoring

### Key Transaction Metrics

#### Active Transaction Group Status

```sql
-- Transaction groups by status
SELECT status, COUNT(*) AS count
FROM transaction_group
GROUP BY status;

-- Expected:
-- IN_PROGRESS: few (only active transfers)
-- CONFIRMED: many
-- REJECTED: some (failed transfers)
```

#### Long-Running Transaction Groups

```sql
-- Transaction groups IN_PROGRESS for > 1 hour
SELECT
    id,
    status,
    created_at,
    updated_at,
    EXTRACT(EPOCH FROM (NOW() - created_at)) / 3600 AS hours_old
FROM transaction_group
WHERE status = 'IN_PROGRESS'
  AND created_at < NOW() - INTERVAL '1 hour'
ORDER BY created_at ASC;

-- Should be empty or very few
-- Investigate if > 10 groups
```

#### Failed Transaction Rate

```sql
-- Rejection rate (last 24 hours)
SELECT
    COUNT(*) FILTER (WHERE status = 'REJECTED') AS rejected_count,
    COUNT(*) FILTER (WHERE status = 'CONFIRMED') AS confirmed_count,
    ROUND(
        100.0 * COUNT(*) FILTER (WHERE status = 'REJECTED') /
        NULLIF(COUNT(*), 0),
        2
    ) AS rejection_rate_percent
FROM transaction_group
WHERE created_at >= NOW() - INTERVAL '24 hours'
  AND status IN ('CONFIRMED', 'REJECTED');
```

#### Transaction Volume Metrics

```java
@Service
public class TransactionMetricsService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private TransactionGroupRepository transactionGroupRepository;

    public TransactionMetrics getDailyMetrics() {
        LocalDateTime startOfDay = LocalDateTime.now().truncatedTo(ChronoUnit.DAYS);

        long totalGroups = transactionGroupRepository.countByCreatedAtAfter(startOfDay);
        long confirmedGroups = transactionGroupRepository
            .countByStatusAndCreatedAtAfter(TransactionGroupStatus.CONFIRMED, startOfDay);
        long rejectedGroups = transactionGroupRepository
            .countByStatusAndCreatedAtAfter(TransactionGroupStatus.REJECTED, startOfDay);
        long inProgressGroups = transactionGroupRepository
            .countByStatus(TransactionGroupStatus.IN_PROGRESS);

        return TransactionMetrics.builder()
            .timestamp(LocalDateTime.now())
            .totalGroups(totalGroups)
            .confirmedGroups(confirmedGroups)
            .rejectedGroups(rejectedGroups)
            .inProgressGroups(inProgressGroups)
            .successRate(
                totalGroups > 0 ? (double) confirmedGroups / totalGroups * 100 : 0
            )
            .build();
    }
}
```

### Zero-Sum Violation Detection

Critical: No confirmed transaction groups should violate zero-sum.

```sql
-- Check for zero-sum violations in confirmed groups
WITH group_balances AS (
    SELECT
        reference_id,
        SUM(amount) AS total_amount
    FROM (
        SELECT reference_id, amount FROM transaction
        UNION ALL
        SELECT reference_id, amount FROM transaction_snapshot
    ) AS all_txns
    WHERE reference_id IN (
        SELECT id FROM transaction_group WHERE status = 'CONFIRMED'
    )
    GROUP BY reference_id
)
SELECT
    reference_id,
    total_amount
FROM group_balances
WHERE total_amount != 0;

-- Should return 0 rows
-- Alert immediately if any violations found
```

---

## Performance Metrics

### Query Performance

Monitor key query execution times.

#### Available Balance Query

```sql
-- Monitor execution time of this critical query
EXPLAIN ANALYZE
SELECT COALESCE(SUM(amount), 0)
FROM (
    SELECT amount FROM transaction
    WHERE wallet_id = 123 AND status = 'CONFIRMED'
    UNION ALL
    SELECT amount FROM transaction_snapshot
    WHERE wallet_id = 123 AND status = 'CONFIRMED'
) AS combined;

-- Target: < 50ms
-- Warning: > 100ms
-- Critical: > 500ms
```

#### Transaction History Query

```sql
-- Monitor pagination query performance
EXPLAIN ANALYZE
SELECT *
FROM (
    SELECT * FROM transaction WHERE wallet_id = 123
    UNION ALL
    SELECT * FROM transaction_snapshot WHERE wallet_id = 123
) AS history
ORDER BY confirm_reject_timestamp DESC
LIMIT 20 OFFSET 0;

-- Target: < 100ms
-- Warning: > 500ms
-- Critical: > 1000ms
```

### Database Performance Metrics

```sql
-- Table sizes
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
    pg_total_relation_size(schemaname||'.'||tablename) AS size_bytes
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY size_bytes DESC;

-- Index usage
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan AS index_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- Unused indexes
SELECT
    schemaname,
    tablename,
    indexname
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND indexname NOT LIKE '%_pkey';

-- Cache hit ratio (should be > 95%)
SELECT
    SUM(heap_blks_hit) / NULLIF(SUM(heap_blks_hit + heap_blks_read), 0) * 100
        AS cache_hit_ratio
FROM pg_statio_user_tables;
```

---

## Storage Health

### Storage Metrics

```sql
-- Database size
SELECT pg_size_pretty(pg_database_size(current_database()));

-- Table sizes
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) -
                   pg_relation_size(schemaname||'.'||tablename)) AS index_size,
    (SELECT COUNT(*) FROM pg_indexes WHERE tablename = t.tablename) AS index_count
FROM pg_tables t
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Row counts
SELECT
    'transaction' AS table_name,
    COUNT(*) AS row_count
FROM transaction
UNION ALL
SELECT
    'transaction_snapshot',
    COUNT(*)
FROM transaction_snapshot
UNION ALL
SELECT
    'transaction_snapshot_archive',
    COUNT(*)
FROM transaction_snapshot_archive;
```

### Storage Growth Rate

```java
@Service
public class StorageMonitoringService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public StorageMetrics getStorageMetrics() {
        // Query table sizes
        String query = """
            SELECT
                tablename,
                pg_total_relation_size(schemaname||'.'||tablename) AS size_bytes
            FROM pg_tables
            WHERE schemaname = 'public'
            """;

        Map<String, Long> tableSizes = jdbcTemplate.query(query, rs -> {
            Map<String, Long> sizes = new HashMap<>();
            while (rs.next()) {
                sizes.put(rs.getString("tablename"), rs.getLong("size_bytes"));
            }
            return sizes;
        });

        return StorageMetrics.builder()
            .timestamp(LocalDateTime.now())
            .transactionTableSize(tableSizes.getOrDefault("transaction", 0L))
            .snapshotTableSize(tableSizes.getOrDefault("transaction_snapshot", 0L))
            .archiveTableSize(tableSizes.getOrDefault("transaction_snapshot_archive", 0L))
            .build();
    }

    @Scheduled(fixedRate = 3600000) // Every hour
    public void recordStorageMetrics() {
        StorageMetrics metrics = getStorageMetrics();
        // Store in time-series database or log
        log.info("Storage metrics: {}", metrics);
    }
}
```

---

## Alert Configuration

### Critical Alerts

**Trigger Immediately**:

1. **Balance Mismatch**
   - Reconciliation difference > $1.00
   - Zero-sum violation detected

2. **System Down**
   - Database unreachable
   - Application health check failing

3. **Data Integrity**
   - Snapshot verification failure
   - Archive count mismatch

### Warning Alerts

**Trigger within 1 hour**:

1. **High Transaction Volume**
   - Active transactions > 100,000
   - Snapshot table > 10 million rows

2. **Performance Degradation**
   - Query time > 500ms
   - Database cache hit rate < 90%

3. **Stale Data**
   - Oldest active transaction > 7 days
   - Snapshot not run in > 48 hours

### Info Alerts

**Daily summary**:

1. **Daily Metrics**
   - Total transaction volume
   - Success/failure rates
   - Storage growth

2. **Operational Summary**
   - Snapshot execution time
   - Archive execution time
   - Reconciliation status

### Alert Implementation

#### Using Spring Boot Actuator + Prometheus

```yaml
# application.yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoint:
    prometheus:
      enabled: true
```

```java
@Component
public class CustomMetrics {

    private final MeterRegistry meterRegistry;

    public CustomMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordBalanceMismatch(Long difference) {
        meterRegistry.counter("mwallet.balance.mismatch",
            "difference", difference.toString()
        ).increment();
    }

    public void recordTransactionGroupSuccess() {
        meterRegistry.counter("mwallet.transaction.group.success").increment();
    }

    public void recordTransactionGroupFailure(String reason) {
        meterRegistry.counter("mwallet.transaction.group.failure",
            "reason", reason
        ).increment();
    }

    public void recordQueryTime(String queryType, long milliseconds) {
        meterRegistry.timer("mwallet.query.time",
            "type", queryType
        ).record(milliseconds, TimeUnit.MILLISECONDS);
    }
}
```

#### Grafana Dashboard Queries

```promql
# Transaction group success rate
rate(mwallet_transaction_group_success_total[5m]) /
(rate(mwallet_transaction_group_success_total[5m]) +
 rate(mwallet_transaction_group_failure_total[5m])) * 100

# Average query time
rate(mwallet_query_time_sum[5m]) / rate(mwallet_query_time_count[5m])

# Balance mismatches
increase(mwallet_balance_mismatch_total[1h])
```

---

## Operational Checklist

### Daily Checks

- ✅ Review reconciliation report
- ✅ Check for long-running transaction groups
- ✅ Verify snapshot execution successful
- ✅ Review error logs
- ✅ Check storage growth rate

### Weekly Checks

- ✅ Analyze transaction volume trends
- ✅ Review query performance metrics
- ✅ Check database cache hit ratio
- ✅ Verify backup success
- ✅ Review alert frequency

### Monthly Checks

- ✅ Verify archive execution successful
- ✅ Validate ledger entry accuracy
- ✅ Review and optimize slow queries
- ✅ Check storage capacity planning
- ✅ Test disaster recovery procedures

---

## Troubleshooting Guide

### Issue: Reconciliation Mismatch

**Symptoms**: System balance != sum of wallet balances

**Investigation**:
1. Check for IN_PROGRESS transaction groups
2. Verify HOLD amounts are accounted for
3. Check for orphaned transactions
4. Review recent archiving operations

**Resolution**:
```sql
-- Find orphaned transactions (no group)
SELECT * FROM transaction WHERE reference_id NOT IN (
    SELECT id FROM transaction_group
);

-- Check IN_PROGRESS groups
SELECT * FROM transaction_group
WHERE status = 'IN_PROGRESS'
ORDER BY created_at ASC;
```

### Issue: Slow Balance Queries

**Symptoms**: `getAvailableBalance()` takes > 500ms

**Investigation**:
1. Check active transaction count
2. Verify indexes are present
3. Run EXPLAIN ANALYZE
4. Check database cache hit ratio

**Resolution**:
1. Run snapshot if active transactions > 50,000
2. Rebuild indexes if needed:
   ```sql
   REINDEX TABLE transaction;
   REINDEX TABLE transaction_snapshot;
   ```
3. Update table statistics:
   ```sql
   ANALYZE transaction;
   ANALYZE transaction_snapshot;
   ```

### Issue: Zero-Sum Violation

**Symptoms**: Confirmed transaction group doesn't balance to zero

**Investigation**:
1. Query group transactions:
   ```sql
   SELECT * FROM transaction
   WHERE reference_id = '<group-uuid>';
   ```
2. Calculate sum
3. Check application logs for errors

**Resolution**:
- This should never happen (enforced by code)
- If found, critical bug - investigate immediately
- Correct manually if needed (with audit trail)

---

## Related Documentation

- [Storage Tiers Architecture](../architecture/storage-tiers.md) - Storage design
- [Snapshot & Archiving](./snapshot-archiving.md) - Snapshot operations
- [Service API Reference](../api/services.md) - API documentation
- [Architecture Overview](../architecture/overview.md) - System architecture
