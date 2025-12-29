# mWallet Architecture

## Overview

mWallet is a **tier2 internal business service** implementing a banking-compliant digital wallet system with double-entry accounting principles.

## Service Tier Classification

mWallet follows a three-tier architecture:

| Tier      | Purpose                             | Exposed To             | Authorization         |
|-----------|-------------------------------------|------------------------|-----------------------|
| **tier1** | External API for web/mobile clients | Public (via gateway)   | Required (JWT/OAuth2) |
| **tier2** | Internal business services          | Internal services only | Not required          |
| **tier3** | External system integrations        | Internal services only | Not required          |

**mWallet is tier2** - an internal service consumed by other backend services.

## Multi-Module Structure

mWallet uses a multi-module Maven project structure to separate API contracts from implementation:

```
mWallet/
├── pom.xml                          # Parent POM
├── api/                             # API module - shared contracts
│   ├── pom.xml
│   └── src/main/java/com/nosota/mwallet/api/
│       ├── LedgerApi.java          # Low-level ledger operations interface
│       ├── LedgerClient.java       # WebClient implementation for consumers
│       ├── PaymentApi.java         # High-level payment operations interface
│       ├── PaymentClient.java      # WebClient implementation for consumers
│       ├── dto/                    # Data Transfer Objects
│       ├── request/                # Request objects
│       ├── response/               # Response objects
│       └── model/                  # Enums and simple models
└── service/                        # Service module - implementation
    ├── pom.xml                     # Depends on api module
    └── src/main/java/com/nosota/mwallet/
        ├── controller/
        │   ├── LedgerController.java    # Implements LedgerApi
        │   └── PaymentController.java   # Implements PaymentApi
        ├── service/                # Business logic
        ├── repository/             # Data access
        ├── model/                  # JPA entities
        ├── exception/              # Exception handlers
        └── config/                 # Spring configuration
```

## API Module Design

The `api` module defines contracts that both **server** (controllers) and **clients** (consuming services) implement:

### Key Principles

1. **Interface-First Design**: Both server and client implement the same interface
2. **Spring MVC Annotations**: Interface contains full REST endpoint definitions
3. **Type Safety**: Compile-time checking for API consumers
4. **Versioning**: API version in URL path (`/api/v1/`)

### Example: Consumer Usage

```java
// In consuming service's pom.xml
<dependency>
    <groupId>com.nosota</groupId>
    <artifactId>mwallet-api</artifactId>
    <version>${mwallet.version}</version>
</dependency>

// In consuming service's configuration
@Configuration
public class MWalletClientConfig {

    @Bean
    public WebClient mwalletWebClient(WebClient.Builder builder,
                                      @Value("${services.mwallet.url}") String baseUrl) {
        return builder.baseUrl(baseUrl).build();
    }

    @Bean
    public LedgerClient ledgerClient(WebClient mwalletWebClient) {
        return new LedgerClient(mwalletWebClient);
    }

    @Bean
    public PaymentClient paymentClient(WebClient mwalletWebClient) {
        return new PaymentClient(mwalletWebClient);
    }
}

// In consuming service's business logic
@Service
@RequiredArgsConstructor
public class OrderService {

    private final LedgerClient ledgerClient;
    private final PaymentClient paymentClient;

    public void processOrder(Order order) {
        // Use type-safe API client
        UUID groupId = ledgerClient.createTransactionGroup().getBody().referenceId();
        ledgerClient.holdDebit(order.getBuyerWalletId(), order.getAmount(), groupId);
        ledgerClient.holdCredit(order.getMerchantWalletId(), order.getAmount(), groupId);
        ledgerClient.settleTransactionGroup(groupId);
    }

    public void settleToMerchant(Long merchantId) {
        paymentClient.executeSettlement(merchantId);
    }
}
```

## Core Domain Concepts

### 1. Wallets

Wallets hold balances and track ownership:

- **USER**: End-user wallets (buyers)
- **MERCHANT**: Merchant wallets (sellers)
- **ESCROW**: Temporary holding for pending transactions
- **SYSTEM**: Platform wallets for fees and commissions

### 2. Transaction Lifecycle

mWallet implements a two-phase commit pattern:

```
1. HOLD → 2. SETTLE (commit) or RELEASE/CANCEL (rollback)
```

**HOLD Phase**:
- Reserves funds (debit hold) or prepares to receive (credit hold)
- No actual money movement yet
- Can be cancelled without consequences

**SETTLE Phase**:
- Commits the transaction
- Money actually moves between accounts
- Irreversible (requires refund to undo)

**RELEASE/CANCEL Phase**:
- Rollback mechanism
- Releases held funds back to original state
- Used when transaction fails

### 3. Transaction Groups

Transactions are organized into groups for atomic operations:

```java
UUID groupId = createTransactionGroup();
holdDebit(buyerWallet, 100, groupId);    // Buyer pays 100
holdCredit(merchantWallet, 97, groupId); // Merchant receives 97
holdCredit(systemWallet, 3, groupId);    // System fee 3
settleTransactionGroup(groupId);         // Atomic commit of all 3
```

**Properties**:
- All transactions in a group succeed or fail together
- Group must balance (sum = 0) to settle
- Links transactions to merchants/buyers for settlement and refund

### 4. Double-Entry Accounting

Every transaction has two sides:

```
DEBIT (−) from one wallet = CREDIT (+) to another wallet
```

The system enforces:
- Sum of all debits = Sum of all credits (zero-sum)
- Complete audit trail
- No money creation or destruction

## Ledger Architecture

### Snapshot & Archive System

For performance and compliance, transactions are organized into three tables:

```
┌──────────────┐
│ transaction  │ ← Active transactions (recent)
└──────────────┘
       ↓ Daily snapshot
┌──────────────────────┐
│ transaction_snapshot │ ← Historical with ledger entries
└──────────────────────┘
       ↓ Periodic archive
┌──────────────────────┐
│ transaction_archive  │ ← Long-term storage
└──────────────────────┘
```

**Benefits**:
- Fast queries on active data
- Efficient historical queries with ledger balance checkpoints
- Regulatory compliance with immutable archive
- Reduced table size for active operations

## Banking Compliance

mWallet implements full banking ledger compliance:

1. **Immutability**: Transactions cannot be modified after creation
2. **Audit Trail**: Complete history of all operations
3. **Reconciliation**: Balances can be verified at any point in time
4. **Double-Entry**: Every debit has a corresponding credit
5. **Snapshot System**: Point-in-time balance verification
6. **Archive**: Long-term immutable storage for regulatory compliance

## Related Documentation

- [API Design](./api-design.md) - LedgerApi vs PaymentApi separation
- [Testing Guide](./testing.md) - Test structure and guidelines
- [Exception Handling](./exception-handling.md) - Error handling strategy
