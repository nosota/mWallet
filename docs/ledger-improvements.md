# Ledger System - Recommendations for Production

## CRITICAL: Ensure True Immutability

### Current Issue
Transaction entity uses `@Setter` which allows modifications after persistence.

### Recommended Solution

Replace mutable setters with immutable builder pattern:

```java
@Entity
@Getter
@Builder  // Use Lombok Builder instead of Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)  // Only for Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private UUID referenceId;

    @Column(nullable = false)
    private Integer walletId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    // ... other fields
}
```

**Update usage in WalletService:**
```java
// OLD (mutable)
Transaction holdTransaction = new Transaction();
holdTransaction.setWalletId(walletId);
holdTransaction.setAmount(-amount);
// ...

// NEW (immutable)
Transaction holdTransaction = Transaction.builder()
    .walletId(walletId)
    .amount(-amount)
    .type(TransactionType.DEBIT)
    .status(TransactionStatus.HOLD)
    .referenceId(referenceId)
    .holdReserveTimestamp(LocalDateTime.now())
    .build();
```

---

## Missing Features for Production Ledger

### 1. Audit Trail
Add audit columns to track who/when:

```sql
ALTER TABLE transaction ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE transaction ADD COLUMN created_by VARCHAR(255);  -- user/system identifier
ALTER TABLE transaction ADD COLUMN correlation_id UUID;      -- for distributed tracing
```

```java
@Entity
@EntityListeners(AuditingEntityListener.class)
public class Transaction {
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    @Column(name = "correlation_id")
    private UUID correlationId;  // for tracing across microservices
}
```

---

### 2. Database Constraints

Add NOT NULL and CHECK constraints:

```sql
-- Ensure amount is never null
ALTER TABLE transaction ALTER COLUMN amount SET NOT NULL;
ALTER TABLE transaction ALTER COLUMN reference_id SET NOT NULL;
ALTER TABLE transaction ALTER COLUMN wallet_id SET NOT NULL;

-- Add CHECK constraint for amount sign consistency
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_amount_type
    CHECK (
        (type = 'DEBIT' AND amount < 0) OR
        (type = 'CREDIT' AND amount > 0) OR
        (type = 'LEDGER')
    );

-- Add index on reference_id for fast lookups
CREATE INDEX idx_transaction_reference_id ON transaction(reference_id);

-- Add composite index for wallet balance queries
CREATE INDEX idx_transaction_wallet_status ON transaction(wallet_id, status);
```

---

### 3. Prevent Accidental Updates/Deletes

Add database trigger to block modifications:

```sql
-- Prevent UPDATEs on transaction table (immutable ledger)
CREATE OR REPLACE FUNCTION prevent_transaction_update()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Transaction records are immutable. Use reversal entries instead.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_transaction_update
    BEFORE UPDATE ON transaction
    FOR EACH ROW
    EXECUTE FUNCTION prevent_transaction_update();

-- Prevent DELETEs on transaction table
CREATE OR REPLACE FUNCTION prevent_transaction_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Transaction records cannot be deleted. Ledger must be complete and auditable.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_transaction_delete
    BEFORE DELETE ON transaction
    FOR EACH ROW
    EXECUTE FUNCTION prevent_transaction_delete();
```

---

### 4. Transaction Signing (for regulatory compliance)

Add cryptographic signature to ensure integrity:

```java
@Entity
public class Transaction {
    // ... existing fields

    @Column(name = "signature", length = 512)
    private String signature;  // SHA-256 hash of transaction data

    @PrePersist
    public void generateSignature() {
        // Calculate hash of immutable fields
        String data = String.format("%s:%d:%s:%s:%d",
            referenceId, walletId, type, status, amount);
        this.signature = DigestUtils.sha256Hex(data);
    }
}
```

---

### 5. Batch Settlement Support

For high-volume systems, implement batch processing:

```java
@Service
public class BatchSettlementService {

    /**
     * Batch settle multiple transaction groups at once.
     * Used for end-of-day settlement in payment systems.
     */
    @Transactional
    public BatchSettlementResult settleBatch(List<UUID> referenceIds) {
        // 1. Validate all groups are ready for settlement
        // 2. Check zero-sum for each group
        // 3. Settle all in single transaction
        // 4. Return summary report
    }
}
```

---

### 6. Balance Reconciliation Report

Add endpoint for regulatory reporting:

```java
@RestController
@RequestMapping("/api/v1/ledger/reports")
public class LedgerReportController {

    /**
     * Generate balance reconciliation report for audit.
     * Shows all transactions for a wallet within date range.
     */
    @GetMapping("/reconciliation")
    public ReconciliationReport getReconciliationReport(
            @RequestParam Integer walletId,
            @RequestParam LocalDateTime fromDate,
            @RequestParam LocalDateTime toDate) {

        // Return:
        // - Opening balance
        // - All transactions (SETTLED only)
        // - Closing balance
        // - Verification: sum(transactions) = closing - opening
    }
}
```

---

### 7. Idempotency Keys

Prevent duplicate transactions:

```java
@Entity
public class Transaction {
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;  // Client-provided unique key
}

// In WalletService
public Integer holdDebit(..., String idempotencyKey) {
    // Check if transaction with this idempotency key already exists
    Optional<Transaction> existing = transactionRepository
        .findByIdempotencyKey(idempotencyKey);

    if (existing.isPresent()) {
        log.warn("Duplicate transaction attempt: {}", idempotencyKey);
        return existing.get().getId();  // Return existing transaction
    }

    // Create new transaction with idempotency key
    // ...
}
```

---

### 8. Event Sourcing Integration

Emit events for external systems:

```java
@Service
public class TransactionService {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Transactional
    public void settleTransactionGroup(UUID referenceId) {
        // ... existing settlement logic

        // Emit event for external systems (analytics, notifications, etc.)
        eventPublisher.publishEvent(new TransactionGroupSettledEvent(
            referenceId,
            LocalDateTime.now(),
            transactions.size()
        ));
    }
}
```

---

## Priority Implementation Order

1. **CRITICAL**: Add database triggers to prevent UPDATE/DELETE
2. **HIGH**: Add NOT NULL constraints and indexes
3. **HIGH**: Replace @Setter with @Builder
4. **MEDIUM**: Add audit trail (created_at, created_by)
5. **MEDIUM**: Implement idempotency keys
6. **LOW**: Add transaction signing
7. **LOW**: Batch settlement support

---

## Testing Recommendations

Add specific tests for ledger invariants:

```java
@Test
void ledgerInvariants_sumOfAllTransactionsMustBeZero() {
    // Create multiple wallets and transactions
    // Verify: SUM(amount) across ALL wallets = 0
}

@Test
void ledgerInvariants_transactionsAreImmutable() {
    // Create transaction
    // Try to update it
    // Should fail with constraint violation
}

@Test
void ledgerInvariants_balanceMatchesTransactionHistory() {
    // For each wallet:
    // Verify: currentBalance = SUM(SETTLED transactions)
}
```

---

## Conclusion

Current implementation has solid foundation but needs hardening for production:
- ✅ Core ledger logic is correct
- ⚠️ Immutability not enforced at code level
- ⚠️ Missing database constraints
- ⚠️ No audit trail
- ⚠️ No idempotency protection

Implement recommendations above before production deployment.
