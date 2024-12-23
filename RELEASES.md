## **Wallet System - Release Notes v1.0.3*4

### **Date:** 23.12.2024

### **Overview:**

1. Use Lombok to simplify code.
2. Use Test Containers for integration testing.
3. Make all DTO immutable.
4. Changes in method **captureDailySnapshotForWallet**:
   * Use batches for inserting/deleting data.
   * Combine the selection of TransactionGroup and Transaction into a single query with filtering at the database level.
   * Add a validation step to ensure the success of operations.
5. TBD



## **Wallet System - Release Notes v1.0.3**

### **Date:** 22.08.2023

### **Overview:**

1. Develop a reference table keeping correspondence between LEDGER record and transaction groups moved to archive.
2. Creating empty wallet must not create zero credit operation in transaction table.
3. getPaginatedTransactionHistory should use either hold_release_timestamp or confirm_reject_timestamp depending if they NULL or not. The method needs to use non nullable value for returning 'timestamp'.
4. Wallet needs a description.
5. Describe why using LEDGER records need using intermediate transaction_snapshot table and what is keys differences between goals of using two tables: transaction_snapshot and transaction_snapshot_archive.
6. Develop getRecentTransactions(walletId) that returns all transactions from transaction and snapshot tables.
7. Developed additional tests for creating transaction snapshots and archives.

---

## **Wallet System - Release Notes v1.0.2**

### **Date:** 22.08.2023

### **Overview:**

1. Develop getReconciliationBalanceOfAllConfirmedGroups() that retrieves the reconciliation balance for the entire system.
2. captureDailySnapshotForWallet() must not transfer transactions belong to transaction groups in IN_PROGRESS status.
3. confirmTransactionGroup() must calculate reconciliation of all transactions included in it. If the reconciliation amount is not zero, it should throws TransactionGroupZeroingOutException.
4. Develop createTransactionGroup(), confirmTransactionGroup() and rejectTransactionGroup()
5. getAvailableBalance must take into account HOLD amounts of incomplete transactions
6. Use validation framework to validate service args.
7. created_at and updated_at are not automatically updated in transaction_group table

---

## **Wallet System - Release Notes v1.0.1**

### **Date:** 21.08.2023

### **Overview:**

1. **Introduced `TransactionStatisticService`:** A new service aimed at providing detailed statistics related to
   transactions.

    - **getDailyCreditOperations:** This method retrieves all credit operations for a specified wallet ID on a given
      day.

    - **getDailyDebitOperations:** This method fetches all debit operations for a particular wallet ID on a specified
      date.

    - **getCreditOperationsInRange:** A new functionality that enables users to obtain credit operations for a specific
      wallet ID within a given date range.

    - **getDebitOperationsInRange:** This method allows users to get debit operations for a designated wallet ID across
      a defined date range.

#### Notes:

- These new methods ensure only CONFIRMED transactions are taken into account, ensuring accurate and reliable
  statistics.

- Archive tables have been purposefully excluded from statistics due to potential performance impacts and their
  infrequent usage.

---

## **Wallet System - Release Notes v1.0.0**

### **Date:** 21.08.2023

### **Overview:**

We are excited to announce the first official release of the Wallet System. This system provides a robust and scalable
solution for managing financial transactions and wallets for a wide variety of applications.

### **Key Features:**

1. **Wallet Creation:**
    - Support for multiple wallet types.
    - Option to initialize a wallet with an initial balance.

2. **Transaction Management:**
    - Facilitates various transaction types including DEBIT, CREDIT, HOLD, and RESERVE.
    - Supports confirming or rejecting held transactions.

3. **Balance Enquiries:**
    - Retrieve available, HOLD, and RESERVED balances.
    - Efficient querying even with an extensive transaction history, thanks to daily snapshots.

4. **Extensibility and Modular Design:**
    - Built with future expansions in mind.
    - Easy integration with external systems and platforms.

5. **Transaction History and Archiving:**
    - Daily snapshot capturing for efficient querying.
    - Archiving of older snapshots to maintain system performance.

### **Known Issues and Limitations:**

- The system currently does not have built-in functions to transfer money between three or more wallets simultaneously.
  This can be implemented externally, and a good starting point is the `TransactionService.transferBetweenTwoWallets`
  method.

### **Future Enhancements:**

- Introducing more advanced analytics and reporting tools.
- Support for multi-currency transactions and conversions.
