# API Design: LedgerApi vs PaymentApi

## Overview

The mWallet API is split into two focused interfaces following the **Single Responsibility Principle**:

1. **LedgerApi** - Low-level ledger operations
2. **PaymentApi** - High-level payment operations

This separation improves maintainability, testability, and scalability.

## Architecture Decision

### Before (v1.0.x): Monolithic API

```
LedgerApi
├── Wallet operations (hold, settle, release, cancel)
├── Transaction group management
├── Transfer operations
├── Balance queries
├── Settlement operations     ← Business logic
└── Refund operations         ← Business logic
```

**Problems**:
- Mixed concerns (infrastructure + business logic)
- Single interface became bloated
- Hard to understand responsibility boundaries
- Difficult to scale team ownership

### After (v1.1.0+): Separated APIs

```
LedgerApi                          PaymentApi
├── Wallet operations              ├── Settlement operations
├── Transaction group mgmt         └── Refund operations
├── Transfer operations
└── Balance queries
```

**Benefits**:
- Clear separation of concerns
- Each API has single responsibility
- Easier to test and maintain
- Better team ownership boundaries
- Scales better as features grow

## LedgerApi - Low-Level Ledger Operations

**Purpose**: Direct manipulation of ledger primitives (wallets, transactions, groups)

**Base URL**: `/api/v1/ledger`

### Endpoints

#### Wallet Operations
```
POST   /wallets/{walletId}/hold-debit      # Reserve funds (debit)
POST   /wallets/{walletId}/hold-credit     # Prepare to receive funds (credit)
POST   /wallets/{walletId}/settle          # Commit held transaction
POST   /wallets/{walletId}/release         # Rollback held transaction (success case)
POST   /wallets/{walletId}/cancel          # Rollback held transaction (failure case)
```

#### Transaction Group Management
```
POST   /groups                              # Create new transaction group
POST   /groups/{referenceId}/settle         # Settle all transactions in group
POST   /groups/{referenceId}/release        # Release all transactions in group
POST   /groups/{referenceId}/cancel         # Cancel all transactions in group
```

#### Transfer Operations
```
POST   /transfer                            # Direct wallet-to-wallet transfer
```

#### Query Operations
```
GET    /wallets/{walletId}/balance          # Get wallet balance
GET    /groups/{referenceId}/status         # Get group status
GET    /groups/{referenceId}/transactions   # Get group transactions
```

### Usage Example

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final LedgerClient ledgerClient;

    public void processOrder(Order order) {
        // 1. Create transaction group
        UUID groupId = ledgerClient.createTransactionGroup()
            .getBody().referenceId();

        // 2. Hold debit from buyer
        ledgerClient.holdDebit(
            order.getBuyerWalletId(),
            order.getTotalAmount(),
            groupId
        );

        // 3. Hold credit to escrow (for pending orders)
        ledgerClient.holdCredit(
            escrowWalletId,
            order.getTotalAmount(),
            groupId
        );

        // 4. Settle the group (atomic commit)
        ledgerClient.settleTransactionGroup(groupId);
    }
}
```

### Design Principles

1. **Low-Level Primitives**: Direct ledger operations
2. **No Business Logic**: Just transaction management
3. **Building Blocks**: Used by higher-level services
4. **Stateless**: No business state management

## PaymentApi - High-Level Payment Operations

**Purpose**: Business-level payment operations (settlement, refund)

**Base URL**: `/api/v1/payment`

### Endpoints

#### Settlement Operations
```
GET    /settlement/merchants/{merchantId}/calculate   # Calculate settlement preview
POST   /settlement/merchants/{merchantId}/execute     # Execute merchant payout
GET    /settlement/{settlementId}                     # Get settlement details
GET    /settlement/merchants/{merchantId}/history     # Get settlement history
```

#### Refund Operations
```
POST   /refund                                        # Create refund request
GET    /refund/{refundId}                            # Get refund details
GET    /refund/merchants/{merchantId}/history         # Get refund history
GET    /refund/orders/{transactionGroupId}            # Get refunds for order
```

### Usage Example

```java
@Service
@RequiredArgsConstructor
public class MerchantPayoutService {

    private final PaymentClient paymentClient;

    public void processDailyPayouts(List<Long> merchantIds) {
        for (Long merchantId : merchantIds) {
            // 1. Calculate what merchant will receive
            SettlementResponse preview = paymentClient
                .calculateSettlement(merchantId)
                .getBody();

            log.info("Merchant {} will receive: {} (after {} fee)",
                merchantId, preview.netAmount(), preview.feeAmount());

            // 2. Execute the payout
            SettlementResponse settlement = paymentClient
                .executeSettlement(merchantId)
                .getBody();

            log.info("Settlement {} executed for merchant {}",
                settlement.id(), merchantId);
        }
    }

    public void processRefund(RefundRequest request) {
        // Create refund for customer
        RefundResponse refund = paymentClient
            .createRefund(request)
            .getBody();

        if (refund.status().equals("PENDING_FUNDS")) {
            log.warn("Refund {} pending - merchant has insufficient balance",
                refund.id());
        }
    }
}
```

### Design Principles

1. **Business Operations**: High-level payment workflows
2. **Complex Logic**: Orchestrates multiple ledger operations
3. **State Management**: Tracks settlement/refund states
4. **Domain Concepts**: Uses business terminology

## Settlement Flow

Settlement is the process of paying out accumulated ESCROW funds to merchants:

```
┌─────────────────────────────────────────────────────┐
│ 1. Orders settle → Funds accumulate in ESCROW      │
│    (via LedgerApi transaction groups)              │
└─────────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│ 2. Calculate settlement preview                     │
│    GET /api/v1/payment/settlement/merchants/{id}/calculate│
│    → Shows: totalAmount, feeAmount, netAmount       │
└─────────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│ 3. Execute settlement                               │
│    POST /api/v1/payment/settlement/merchants/{id}/execute │
│    → Transfers: ESCROW → MERCHANT (net)             │
│    → Transfers: ESCROW → SYSTEM (fee)               │
└─────────────────────────────────────────────────────┘
```

**Key Points**:
- Settlement uses LedgerApi under the hood
- Creates atomic transaction group for payout
- Tracks which orders were included (prevents double-settlement)
- Configurable commission rate via `settlement.commission-rate`

## Refund Flow

Refund returns money to buyers AFTER settlement has occurred:

```
┌─────────────────────────────────────────────────────┐
│ 1. Settlement executed → Merchant received money    │
└─────────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│ 2. Customer requests refund                         │
│    POST /api/v1/payment/refund                      │
│    → Requires: transactionGroupId, amount, reason   │
└─────────────────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────┐
│ 3. System processes refund                          │
│    IF merchant balance sufficient:                  │
│       → Status: EXECUTED                            │
│       → Transfers: MERCHANT → BUYER                 │
│    ELSE:                                            │
│       → Status: PENDING_FUNDS                       │
│       → Queued for auto-execution when funds available│
└─────────────────────────────────────────────────────┘
```

**Key Points**:
- Refund is different from RELEASE/CANCEL (which happen BEFORE settlement)
- Requires settlement to be in EXECUTED status
- Supports partial refunds (if enabled)
- Handles merchant insufficient balance gracefully
- Configurable via `refund.*` properties

## Configuration

### Settlement Configuration

```yaml
settlement:
  commission-rate: 0.03        # 3% platform fee
  min-amount: 1000             # Minimum 10.00 to settle (in cents)
  hold-age-days: 0             # Settle immediately (0 = no hold period)
```

### Refund Configuration

```yaml
refund:
  return-commission-to-buyer: false    # Keep commission on refund
  partial-refund-enabled: true         # Allow partial refunds
  multiple-refunds-enabled: true       # Allow multiple refunds per order
  max-days-after-settlement: 90        # Refund window (0 = unlimited)
  require-settled-status: true         # Only refund settled orders
  allow-negative-balance: false        # Prevent merchant negative balance
  auto-execute-pending: true           # Auto-execute when funds available
  pending-funds-expiry-days: 30        # Expire pending refunds after 30 days
```

## Migration from v1.0.x to v1.1.0+

### Breaking Changes

**URL paths changed**:
```
Old: POST /api/v1/ledger/settlement/merchants/{id}/execute
New: POST /api/v1/payment/settlement/merchants/{id}/execute

Old: POST /api/v1/ledger/refund
New: POST /api/v1/payment/refund
```

### Migration Steps

1. **Update dependencies**:
   ```xml
   <dependency>
       <groupId>com.nosota</groupId>
       <artifactId>mwallet-api</artifactId>
       <version>1.1.0</version> <!-- Update version -->
   </dependency>
   ```

2. **Add PaymentClient bean**:
   ```java
   @Bean
   public PaymentClient paymentClient(WebClient mwalletWebClient) {
       return new PaymentClient(mwalletWebClient);
   }
   ```

3. **Update service code**:
   ```java
   // Old (v1.0.x)
   ledgerClient.executeSettlement(merchantId);
   ledgerClient.createRefund(request);

   // New (v1.1.0+)
   paymentClient.executeSettlement(merchantId);
   paymentClient.createRefund(request);
   ```

## API Versioning Strategy

- APIs are versioned in URL path: `/api/v1/`
- Breaking changes require new major version: `/api/v2/`
- Both versions can coexist during migration period
- Deprecation warnings for old versions

## Related Documentation

- [Architecture Overview](./architecture.md)
- [Testing Guide](./testing.md)
- [Exception Handling](./exception-handling.md)
