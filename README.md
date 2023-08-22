### Overview of the Wallet System:

The Wallet System is a digital platform dedicated to managing financial transactions and maintaining a record of
balances for individual wallets. At its core, it's designed to emulate the fundamental operations of a traditional
physical wallet, but in a digital realm, enabling users to perform credits and debits, monitor their balances, and
review their transaction history.

Its primary objective is to provide a robust, efficient, and transparent mechanism for digital financial management. By
harnessing an append-only, event-sourced model, the system ensures an immutable transaction history, guaranteeing the
integrity and reliability of each transaction. This not only elevates security but also provides a clear and
comprehensive audit trail, vital for both individual users and potential oversight entities.

Serving as a foundational layer in the digital financial ecosystem, this Wallet System can be the cornerstone for a
plethora of applications, ranging from personal finance tools to expansive e-commerce platforms. Its design philosophy
emphasizes simplicity in its core functions, ensuring that the fundamental tasks it performs are done with unparalleled
efficiency and accuracy.

---

### Wallet System Architecture:

#### Core Components:

1. **Wallet Table**:
    - Represents individual wallets.
    - Each wallet has a unique and immutable ID.

2. **Transaction Table**:
    - Captures every transaction made against a wallet.
    - Attributes: associated wallet ID, transaction type (credit/debit), amount, timestamp, and a reference to the
      transaction group, if applicable.

3. **Transaction Snapshot Table**:
    - Stores periodic snapshots of transactions.
    - Includes a special ``ledger`` entry to represent the cumulative balance, ensuring efficient balance lookups.

4. **Transaction Archive Table**:
    - Used for archiving older transactions, ensuring the main transaction table remains performant.
    - No foreign key constraints to ensure data remains static once archived.

#### Approach:

- **Event-Sourced Model**: Every change to the state of a wallet is captured as an immutable event (transaction) in the
  system. This provides a reliable and consistent mechanism to replay or reconstruct the state of a wallet at any point
  in time.

- **Append-Only**: Transactions are never updated or deleted. Every action, whether it's a credit or debit, is always
  appended as a new transaction.

#### Benefits:

1. **Immutability**: A historical and unalterable record of all transactions ensures a transparent audit trail.

2. **Concurrency**: The system supports concurrent transaction insertions, eliminating the need for locks on a mutable
   balance field, and thereby enhancing performance.

3. **Consistency and Audit Trail**: An append-only system provides a built-in and clear audit trail. Since every change
   is an event, it provides full visibility into the lifecycle of a wallet.

#### Caveats and Solutions:

1. **Performance**:
    - Summing transactions can slow down as data grows. Periodic balance snapshots or ``ledger`` entries in the transaction
      snapshot table help in speeding up balance calculations.
    - Archiving mechanism moves older transactions to an archive table, maintaining the performance of the main
      transaction table.

2. **Ordering**:
    - Relying solely on timestamps may not always be adequate due to potential clock skew in distributed systems. Using
      sequence numbers or other ordering mechanisms can counteract this.

3. **Data Volume**:
    - Given the append-only nature, data will accumulate over time. The archiving strategy helps in managing older data
      without compromising on the system's integrity.

By embracing an append-only, event-sourced approach, the wallet system is poised to offer robustness, traceability, and
scalability.

---

### Extensibility and Modular Design:

While the foundational architecture of the wallet system is tailored to ensure simplicity, reliability, and efficiency
in its core functionality, it's inherently designed with extensibility in mind. Features like transaction thresholds,
advanced analytical processing using OLAP, and more can seamlessly be integrated atop the current implementation.

The project's ethos underscores the importance of a lean core, prioritizing impeccable performance in foundational
tasks. By keeping the base implementation uncluttered, we not only ensure robustness but also maintain the flexibility
to evolve and expand as per future requirements. This modular approach aids in quicker iterations, easier debugging, and
provides a solid ground for future innovations without entangling complexities.

---

### Externalized Operations: Daily Snapshots and Archiving

Our wallet system is designed with modularity in mind. It offers built-in functionalities for creating daily snapshots
and archiving older transactions, ensuring efficient performance and data compactness even as transaction volume grows.

However, the mechanism to invoke these operations has been externalized, keeping the system's core design uncluttered.
This design decision was taken with adaptability in focus. Different solutions or applications that integrate with our
wallet system might have varying operational patterns, infrastructure setups, or preferences for when and how often
these operations should be triggered.

The functionalities for daily snapshots and archiving are housed within the `TransactionSnapshotService` class.
Specifically, the methods responsible are:

- `captureDailySnapshot(Integer walletId)`: Creates a snapshot of transactions for the specified wallet.
- `archiveOldSnapshots(Integer walletId, LocalDateTime olderThan)`: Archives transactions older than a specified date
  for the given wallet and provides ``ledger`` entries for streamlined balance computation.

Setting up the invocation of these methods should be managed externally, based on the specific requirements and
operational dynamics of the solution leveraging this wallet system. It allows for a high degree of customization,
whether one wants to use CRON jobs, event-driven triggers, manual interventions, or any other scheduling mechanisms.

By allowing the integration layer or the parent solution to control these operations, we ensure that the wallet system
remains both versatile and adaptable to diverse usage scenarios.

---

### Externalized Operations: Complex Funds Transfer

In the realm of financial applications, there are multifaceted scenarios where the need arises to transfer funds across
three or more wallets. Such operations might not just involve the mere movement of money but may also encompass
intricate business rules, verification thresholds, and intensive security checks.

#### Limitation in the Wallet System

Our Wallet System is designed with a modular and extensible philosophy, focusing on core functionalities. Thus, it
deliberately excludes built-in functions for transferring funds between three or more wallets. The primary reason for
this exclusion is the high variability and complexity of multi-wallet transfers that often require bespoke solutions
tailored to the specific needs of each project.

#### Building Your Custom Complex Funds Transfer

Creating a complex funds transfer feature atop the Wallet System is feasible and can be streamlined by leveraging the
existing functionalities.

1. **Starting Point**: The `TransactionService.transferBetweenTwoWallets` method provides a foundational example. It
   illustrates the key components required for any funds transfer, namely:
    - Validating wallets.
    - Holding and reserving funds.
    - Confirming or rejecting the transactions.

   This method can serve as an archetype when designing more sophisticated multi-wallet transfers.

2. **Transaction Group Management**: When dealing with a collection of transactions as part of a single logical
   operation, it's essential to use the `TransactionGroup` entity.
    - Every group of transactions should belong to one unique `TransactionGroup`.
    - The status of the `TransactionGroup` should mirror the state of its associated transactions. For instance, if all
      transactions are successful, the group's status should be set to `CONFIRMED`.

3. **Error Handling & Rollback**: Multi-wallet transfers inherently have multiple points of failure. It's crucial to:
    - Capture and log all errors.
    - Rollback any interim transactions that might have been processed before an error occurred. The `REJECTED` status
      can be used to invalidate a previously held or reserved transaction.
    - Ensure that the status of the `TransactionGroup` accurately reflects the outcome – especially in cases of partial
      success or failure.

4. **Security & Verification**: Given that complex transfers might necessitate additional verification and security
   checks:
    - Ensure all transaction requests are validated against predefined thresholds.
    - Employ rigorous auditing to track every transaction change, especially in high-value transfers.

5. **Reconciliation Balance**: Reconciliation is a fundamental accounting process that ensures the actual money spent or earned matches the money leaving or entering the system. Money should not appear out of nowhere or disappear into nowhere.
   In the Wallet System the resulting balance represents the cumulative initial balance of all wallets created within the system.
   Internal money transfers between wallets should not affect the reconciliation balance, ensuring its constancy
   throughout the system's lifecycle. It's important to:
   - Ensure after any group of transactions the system reconciliation balance stays the same.
   - Regularly monitor the system reconciliation balance.

---

### The Interplay Between the Tables:

The `transaction` table is always the primary source of truth. However, over time, querying it directly can become
computationally intensive. This is where the `transaction_snapshot` table comes in, offering an optimized and summarized
view for frequent operational tasks.

As the `transaction_snapshot` table grows, to maintain its performance, older records are moved to the
`transaction_snapshot_archive` table.

#### How the Tables Work Together:

1. **Real-time Transaction Processing**: When a transaction is initiated, it gets recorded in the `transaction` table.
   This table provides the most current state of all transactions, whether they are completed, pending, or in any other
   state. The data here is always up-to-date and can be used for immediate operations and verifications.

2. **Periodic Snapshots for Performance**: Since ``transaction`` table is constantly growing, periodically (this can be at the end of a day, week, or any other frequency
   depending on the system's requirements), the system takes a snapshot of the transactions from the `transaction` table
   and updates the `transaction_snapshot` table. The `transaction_snapshot` table keeps recently completed transactions.
   All transactions in this table are immutable. Transactions which are in progress will never be moved to `transaction_snapshot` table.

3. **Archiving for Long-term Storage**: As transactions in the `transaction_snapshot` table get older and are no longer actively
   needed for real-time operations, they can be moved to the `transaction_snapshot_archive` table. This table acts as a long-term
   storage solution, ensuring that the `transaction_snapshot` table remains optimized for the most current data.
   During the archiving process, an essential performance optimization occurs: the transactions being archived are consolidated
   into a single `ledger entry` that summarizes their balance. Periodically creating these summary ledger entries in the
   `transaction_snapshot` table enhances balance lookup efficiency. Instead of aggregating numerous individual transactions,
   the system can swiftly reference the most recent `ledger entry`.


#### Why cannot omit `transaction_snapshot` table and create `ledger entries` directly in `transaction` table?

Creating `ledger entries` directly in the `transaction` table, rather than using an intermediary `transaction_snapshot`
table, might seem like a straightforward approach. However, there are compelling reasons to maintain a separate snapshot
table for ledger entries:

1. **Performance**: As transactions increase in number, querying the `transaction` table for balance calculations would
   become increasingly slow. A separate snapshot table enables you to periodically condense the data. By taking a
   snapshot and storing it separately, you avoid repeatedly summing large numbers of rows.

2. **Simplicity in Real-time Processing**: The `transaction` table is designed for real-time processing. Adding ledger
   entries here might complicate the transaction processing logic. Keeping them separate ensures that the transaction
   processing remains clean and optimized.

3. **Historical Data Preservation**: Over time, the `transaction` table would grow tremendously, especially in
   high-transaction environments. Moving older transactions to an archive and maintaining a snapshot ensures that while
   historical data is preserved, it doesn't impede the performance of real-time operations.

4. **Data Integrity & Consistency**: The `transaction` table is frequently written to and updated, and thus has a higher
   potential for inconsistencies or conflicts in a distributed environment. Ledger entries in the `transaction_snapshot`
   table can be created in a controlled manner, ensuring data integrity.

5. **Isolation of Operational and Analytical Workloads**: Real-time transaction processing (Operational) and balance
   calculation or report generation (Analytical) are two distinct workloads. By using a separate `transaction_snapshot`
   table, these workloads are isolated, ensuring neither impacts the performance of the other.

6. **Database Optimization**: Database tables can be optimized based on their access patterns. For
   instance, `transaction` table might be optimized for write-heavy operations, while `transaction_snapshot` can be
   optimized for reads.

In essence, while it's technically possible to mix ledger entries with real-time transactions in the `transaction`
table, doing so might compromise the system's scalability, performance, and clarity of design.

---

### Integer Representation in Financial Systems: The Case for Cents Over Decimals

Using integers (like cents) in a Wallet System instead of floating-point types such as `BigDecimal` or `double` offers
several advantages, particularly in terms of precision, performance, and simplicity. Here's a breakdown of the reasons:

1. **Precision and Accuracy**:
   - **Floating Point Issues**: Both `float` and `double` data types in many programming languages use binary
     floating-point arithmetic which can lead to precision errors. For example, simple arithmetic operations like
     subtraction or addition might not result in the expected value due to rounding errors.
   - **Exact Arithmetic with Integers**: When representing monetary values as integers (e.g., cents), all arithmetic
     operations are exact, avoiding the pitfalls of floating-point arithmetic.

2. **Simplicity**:
   - **Easier Arithmetic**: With integer-based representation, standard arithmetic operations are straightforward. You
     won't need to handle the complexities that come with floating-point arithmetic.
   - **Clearer Database Representation**: When storing values in databases, using an integer field is more
     straightforward than handling decimal points. You can always convert to a decimal representation when displaying
     the value to end users.

3. **Performance**:
   - **Optimized Arithmetic**: Integer arithmetic operations are typically faster and more optimized than their
     floating-point counterparts, especially on certain hardware.
   - **Storage Efficiency**: Integers can be more storage-efficient, especially when considering the additional
     precision and scale metadata needed for exact decimal types like `BigDecimal`.

4. **Consistency Across Platforms**:
   - **Uniform Behavior**: Integers have consistent behavior across different platforms and programming languages. In
     contrast, floating-point implementations might vary.
   - **Database Portability**: Different databases handle floating-point numbers in varying ways. Using integers ensures
     consistency across different database systems.

5. **Avoid Compounding Errors**:
   - Over time, repeated calculations using `double` or `float` can introduce and compound rounding errors. Using
     integer values helps prevent the accumulation of these errors.

6. **Industry Standard**:
   - Many financial systems and standards, like ISO 4217 which defines currency codes, advocate for the use of minor
     units (e.g., cents for USD, pence for GBP) to ensure precision.

In summary, when dealing with money in software systems, it's crucial to maintain precision, avoid rounding errors, and
ensure consistency. Using integer representations, like cents, is a proven way to achieve these goals.

---

### Entity Relationship Diagram (ERD):

Note: This is a simplified representation and might not capture all the intricate relationships and constraints of the
actual database. Ensure to review and adjust as per the detailed requirements and actual relationships of your system.

```
[ Wallet    ]
+-----------+
| id (PK)   |
| type      |
| created_at|
+-----------+
   |
   |
   |
   |1 : n
   |          
[ Transaction         ]
+---------------------+
| id (PK)             |
| wallet_id (FK)   ---|------> [ Wallet ]
| amount              |
| type                |
| status              |
| hold_timestamp      |
| confirm_reject_timestamp|
| description         |
| reference_id (FK)  -|-------> [ Transaction Group ]
+---------------------+
   |
   |
   |1 : n
   |          
[Transaction Snapshot ]
+---------------------+
| id (PK)             |
| wallet_id (FK)   ---|------> [ Wallet ]
| type                |
| amount              |
| status              |
| hold_timestamp      |
| confirm_reject_timestamp|
| snapshot_date       |
| is_ledger_entry     |
+---------------------+

[ Transaction Group ]
+-------------------+
| id (PK)           |
| status            |
| created_at        |
| updated_at        |
+-------------------+

[ Wallet Owner    ]
+-----------------+
| id (PK)         |
| wallet_id (FK) -|-----> [ Wallet ]
| owner_type      |
| owner_ref       |
| created_at      |
| updated_at      |
+-----------------+
```