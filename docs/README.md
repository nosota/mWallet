# mWallet Documentation

Welcome to the mWallet documentation. This guide provides comprehensive information about the architecture, APIs, testing, and exception handling of the mWallet digital wallet system.

## Table of Contents

1. [Architecture Overview](./architecture.md)
2. [API Design](./api-design.md)
3. [Testing Guide](./testing.md)
4. [Exception Handling](./exception-handling.md)

## Quick Start

### For API Consumers

If you're a service that wants to consume mWallet APIs:

1. **Add dependency** to your `pom.xml`:
   ```xml
   <dependency>
       <groupId>com.nosota</groupId>
       <artifactId>mwallet-api</artifactId>
       <version>1.1.0</version>
   </dependency>
   ```

2. **Configure clients** in your Spring configuration:
   ```java
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
   ```

3. **Use in your services**:
   ```java
   @Service
   @RequiredArgsConstructor
   public class OrderService {

       private final LedgerClient ledgerClient;
       private final PaymentClient paymentClient;

       public void processOrder(Order order) {
           // Use ledgerClient for low-level operations
           UUID groupId = ledgerClient.createTransactionGroup().getBody().referenceId();
           ledgerClient.holdDebit(order.getBuyerWalletId(), order.getAmount(), groupId);
           ledgerClient.holdCredit(escrowWalletId, order.getAmount(), groupId);
           ledgerClient.settleTransactionGroup(groupId);
       }

       public void payoutToMerchant(Long merchantId) {
           // Use paymentClient for high-level operations
           paymentClient.executeSettlement(merchantId);
       }
   }
   ```

### For Contributors

If you're developing or maintaining mWallet:

1. **Understand the architecture**: Read [Architecture Overview](./architecture.md)
2. **Learn the API design**: Read [API Design](./api-design.md)
3. **Write tests**: Follow [Testing Guide](./testing.md)
4. **Handle errors properly**: Follow [Exception Handling](./exception-handling.md)

## What is mWallet?

mWallet is a **tier2 internal business service** that provides:

- Digital wallet management (USER, MERCHANT, ESCROW, SYSTEM wallets)
- Transaction processing with two-phase commit (HOLD → SETTLE/RELEASE)
- Double-entry accounting for banking compliance
- Merchant settlement (payouts with platform commission)
- Customer refunds (returns after settlement)
- Transaction history and auditing

## Key Features

### 1. Banking-Compliant Ledger

- Double-entry accounting (every debit has a corresponding credit)
- Immutable transaction history
- Snapshot and archive system for performance and compliance
- Point-in-time balance verification

### 2. Two-Phase Transaction Commit

```
HOLD Phase (reserve funds) → SETTLE (commit) or RELEASE/CANCEL (rollback)
```

Ensures atomic operations across multiple wallets.

### 3. Transaction Groups

Group related transactions for atomic operations:
```java
UUID groupId = createTransactionGroup();
holdDebit(buyerWallet, 100, groupId);
holdCredit(merchantWallet, 97, groupId);
holdCredit(systemWallet, 3, groupId);  // Platform fee
settleTransactionGroup(groupId);       // All or nothing
```

### 4. Settlement & Refund

**Settlement**: Pay out merchants for completed orders
- Transfers from ESCROW to MERCHANT (minus platform commission)
- Prevents double-settlement
- Configurable commission rate

**Refund**: Return money to buyers after settlement
- Handles merchant insufficient balance (PENDING_FUNDS status)
- Supports partial and multiple refunds
- Auto-executes when funds available

## API Overview

### LedgerApi - Low-Level Operations

**Base URL**: `/api/v1/ledger`

**Purpose**: Direct ledger manipulation (wallets, transactions, groups)

**Key Endpoints**:
- Wallet operations: `hold-debit`, `hold-credit`, `settle`, `release`, `cancel`
- Transaction groups: `create`, `settle`, `release`, `cancel`
- Transfers: Direct wallet-to-wallet transfers
- Queries: Balance, status, transaction list

### PaymentApi - High-Level Operations

**Base URL**: `/api/v1/payment`

**Purpose**: Business-level payment workflows (settlement, refund)

**Key Endpoints**:
- Settlement: `calculate`, `execute`, `get`, `history`
- Refund: `create`, `get`, `history`, `getByOrder`

## Technology Stack

- **Java**: 23
- **Spring Boot**: 3.4.5
- **Database**: PostgreSQL
- **Migrations**: Flyway
- **Testing**: JUnit 5 + Mockito + Testcontainers
- **API Communication**: WebClient (REST)
- **Build**: Maven (multi-module)

## Multi-Module Structure

```
mWallet/
├── api/        # Shared API contracts (interfaces, DTOs, clients)
└── service/    # Implementation (controllers, services, repositories)
```

**Benefits**:
- Clear separation between API contract and implementation
- Type-safe client-server communication
- Compile-time API compatibility checking
- Easy versioning and backwards compatibility

## Configuration

### Settlement Configuration

```yaml
settlement:
  commission-rate: 0.03        # 3% platform fee
  min-amount: 1000             # Minimum 10.00 to settle
  hold-age-days: 0             # Immediate settlement
```

### Refund Configuration

```yaml
refund:
  return-commission-to-buyer: false    # Keep commission on refund
  partial-refund-enabled: true         # Allow partial refunds
  multiple-refunds-enabled: true       # Allow multiple refunds per order
  max-days-after-settlement: 90        # 90-day refund window
  require-settled-status: true         # Only refund settled orders
  allow-negative-balance: false        # Prevent merchant overdraft
  auto-execute-pending: true           # Auto-execute when funds available
  pending-funds-expiry-days: 30        # Expire pending refunds after 30 days
```

## Development Workflow

### 1. Set Up Environment

```bash
# Start PostgreSQL (via Docker)
docker run -d --name mwallet-postgres \
  -e POSTGRES_DB=mwallet \
  -e POSTGRES_USER=user \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  postgres:16.6

# Build project
mvn clean install

# Run tests
mvn test
```

### 2. Make Changes

1. Update code following [CLAUDE.md](../CLAUDE.md) rules
2. Write tests following [Testing Guide](./testing.md)
3. Update documentation if architecture/API changes
4. Verify tests pass: `mvn test`

### 3. Create Release

1. Update version in `pom.xml`
2. Add release notes to `RELEASES.md`
3. Tag release: `git tag v1.x.x`
4. Push: `git push origin v1.x.x`

## Support and Contribution

### Getting Help

- Read documentation in `docs/` folder
- Check `CLAUDE.md` for coding standards
- Review `RELEASES.md` for version history

### Contributing

1. Follow Google Java Style Guide
2. Adhere to CLAUDE.md rules
3. Write comprehensive tests
4. Update documentation for architectural changes
5. Keep commits focused and well-described

## Related Documentation

- [CLAUDE.md](../CLAUDE.md) - Coding standards and rules
- [RELEASES.md](../RELEASES.md) - Version history and release notes
- [Architecture Overview](./architecture.md) - Detailed architecture guide
- [API Design](./api-design.md) - API separation rationale
- [Testing Guide](./testing.md) - How to write and run tests
- [Exception Handling](./exception-handling.md) - Error handling patterns
