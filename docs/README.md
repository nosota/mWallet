# mWallet Documentation

Welcome to the mWallet comprehensive documentation. This directory contains detailed technical documentation about the architecture, development, and operations of the mWallet service.

## Document Structure

### Architecture Documentation

- [**Architecture Overview**](./architecture/overview.md) - High-level system architecture and design principles
- [**Data Model**](./architecture/data-model.md) - Complete database schema and entity relationships
- [**Transaction Lifecycle**](./architecture/transaction-lifecycle.md) - Transaction flow and state management
- [**Storage Tiers**](./architecture/storage-tiers.md) - Three-tier storage architecture and archiving strategy

### Development Documentation

- [**Development Setup**](./development/setup.md) - Local development environment setup
- [**Testing Guide**](./development/testing.md) - Testing strategy and test execution

### API Documentation

- [**Service API Reference**](./api/services.md) - Complete service layer API documentation

### Operations Documentation

- [**Snapshot & Archiving**](./operations/snapshot-archiving.md) - Daily snapshot and archiving operations
- [**Monitoring & Reconciliation**](./operations/monitoring.md) - System monitoring and balance reconciliation

## Quick Links

### For New Developers
1. Start with [Architecture Overview](./architecture/overview.md)
2. Read [Data Model](./architecture/data-model.md)
3. Set up your environment: [Development Setup](./development/setup.md)
4. Review [Service API Reference](./api/services.md)

### For Operations Teams
1. Understand [Storage Tiers](./architecture/storage-tiers.md)
2. Configure [Snapshot & Archiving](./operations/snapshot-archiving.md)
3. Set up [Monitoring & Reconciliation](./operations/monitoring.md)

### For Architects
1. Review [Architecture Overview](./architecture/overview.md)
2. Study [Transaction Lifecycle](./architecture/transaction-lifecycle.md)
3. Understand [Storage Tiers](./architecture/storage-tiers.md)

## Project Overview

mWallet is a sophisticated digital wallet management system built with Spring Boot that implements:

- **Event-sourced transaction model** with complete audit trail
- **Two-phase transaction lifecycle** (HOLD/RESERVE → CONFIRM/REJECT)
- **Double-entry bookkeeping** with zero-sum reconciliation
- **Three-tier storage architecture** for performance optimization
- **Ledger checkpoint system** for long-term data management
- **Multi-wallet atomic transfers** with transaction groups

### Technology Stack

- **Java**: 23
- **Spring Boot**: 3.4.5
- **Database**: PostgreSQL
- **Migrations**: Flyway
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Mapping**: MapStruct
- **Utilities**: Lombok

### Core Capabilities

1. **Wallet Management**: Create and manage digital wallets with initial balances
2. **Transaction Processing**: HOLD, RESERVE, CONFIRM, and REJECT operations
3. **Balance Queries**: Available, HOLD, and RESERVED balance calculations
4. **Transaction History**: Paginated history with filtering across all storage tiers
5. **Transaction Statistics**: Credit/debit operations by date and date range
6. **System Reconciliation**: System-wide balance verification
7. **Archiving**: Automated snapshot and archiving for performance optimization

## Related Documentation

- [**CLAUDE.md**](../CLAUDE.md) - Engineering policies and coding standards
- [**README.md**](../README.md) - System overview and architecture principles
- [**RELEASES.md**](../RELEASES.md) - Release notes and changelog

## Contributing

When contributing to mWallet:

1. Follow the engineering principles in [CLAUDE.md](../CLAUDE.md)
2. Update relevant documentation when making architectural changes
3. Ensure all tests pass before submitting changes
4. Add tests for new functionality

## Support

For questions or issues:
1. Check this documentation first
2. Review the [Architecture Documentation](./architecture/overview.md)
3. Consult the [Service API Reference](./api/services.md)