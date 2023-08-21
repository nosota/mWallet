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
