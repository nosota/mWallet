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
    - Includes a special ledger entry to represent the cumulative balance, ensuring efficient balance lookups.

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
    - Summing transactions can slow down as data grows. Periodic balance snapshots or ledger entries in the transaction
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
