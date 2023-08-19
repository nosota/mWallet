The strategy you're referring to is often used in financial systems and can be thought of as an append-only,
event-sourced model. Each transaction is an event that's appended to a log (or a table, in the context of relational
databases). The actual balance of a wallet is not a single mutable field but is derived by aggregating these events.

### Architecture:

1. **Wallet Table**: Represents each wallet with an immutable ID.
2. **Transaction Table**: Represents transactions on the wallets. Each transaction has an associated wallet ID, a type (
   credit or debit), an amount, and a timestamp.

To find the balance of a wallet at any given time, you'd sum the amounts from all transactions associated with that
wallet up to that time.

### Benefits:

1. **Immutability**: Since you never update or delete records from the transaction table, you gain an immutable history
   of all transactions.
2. **Concurrency**: Multiple transactions can be inserted concurrently without contention, as there are no locks on
   updating a single balance field.
3. **Audit Trail**: An append-only system naturally provides an audit trail.

### Caveats:

1. **Performance**: As the transaction table grows, summing transactions can become slower. One way around this is to
   maintain a periodic snapshot of balances. For example, you can have a daily snapshot and when you need the current
   balance, you'd sum transactions that occurred after the latest snapshot.
2. **Ordering**: Ensure you handle the ordering of transactions correctly. Timestamps are not always sufficient due to
   the potential for clock skew in distributed systems. Consider sequence numbers or other ordering mechanisms if this
   becomes an issue.

This architecture provides the non-blocking, concurrent behavior you're looking for while also ensuring data consistency
and offering a clear audit trail.
