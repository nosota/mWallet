package com.nosota.mwallet.service;

import com.nosota.mwallet.dto.PagedResponse;
import com.nosota.mwallet.dto.TransactionHistoryDTO;
import jakarta.persistence.*;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionHistoryService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Retrieves the complete transaction history for a specified wallet.
     *
     * <p>This method consolidates transaction records from both the primary transaction table,
     * the transaction snapshot table (excluding ledger entries) and transaction archive table presenting a merged view
     * as if sourced from a singular table. The resulting list is sorted by timestamp in descending order,
     * ensuring the most recent transactions are at the forefront.</p>
     *
     * <p>The transaction history offers a comprehensive audit trail, providing insights into
     * credits, debits, holds, and other relevant transactional events related to the wallet.</p>
     *
     * @param walletId    The ID of the wallet for which the transaction history is being fetched.
     *                    This is used as the primary filter criteria.
     *
     * @return A {@code List<TransactionHistoryDTO>} containing all transactions related to the
     *         provided wallet, sorted from most recent to oldest.
     *
     * @throws IllegalArgumentException If the provided {@code walletId} is null or invalid.
     * @throws NoResultException If there are no transactions associated with the given {@code walletId}.
     * @throws PersistenceException For any unexpected database-related errors.
     *
     * @see TransactionHistoryDTO
     */
    public List<TransactionHistoryDTO> getFullTransactionHistory(Integer walletId) {
        // Ensure the wallet ID is valid
        if (walletId == null) {
            throw new IllegalArgumentException("Wallet ID cannot be null");
        }

        String sql = """
            SELECT id, wallet_id, type, amount, status, hold_timestamp, confirm_reject_timestamp, description
            FROM (
                -- Fetch from main transaction table
                SELECT id, wallet_id, type, amount, status, hold_timestamp, confirm_reject_timestamp, description 
                FROM transaction 
                WHERE wallet_id = :walletId
                UNION ALL
                -- Fetch from transaction snapshot table
                SELECT id, wallet_id, type, amount, status, hold_timestamp, confirm_reject_timestamp, null as description
                FROM transaction_snapshot 
                WHERE wallet_id = :walletId AND is_ledger_entry = FALSE
                UNION ALL
                -- Fetch from transaction snapshot archive table
                SELECT id, wallet_id, type, amount, status, hold_timestamp, confirm_reject_timestamp, null as description
                FROM transaction_snapshot_archive 
                WHERE wallet_id = :walletId
            ) AS combined_data
            ORDER BY confirm_reject_timestamp DESC, hold_timestamp DESC
        """;

        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        query.setParameter("walletId", walletId);

        @SuppressWarnings("unchecked")
        List<Tuple> results = query.getResultList();

        List<TransactionHistoryDTO> history = new ArrayList<>();
        for (Tuple tuple : results) {
            TransactionHistoryDTO dto = new TransactionHistoryDTO();
            dto.setId(tuple.get("id", Integer.class));
            dto.setWalletId(tuple.get("wallet_id", Integer.class));
            dto.setType(tuple.get("type", String.class));
            dto.setAmount(tuple.get("amount", Long.class));
            dto.setStatus(tuple.get("status", String.class));
            dto.setTimestamp(tuple.get("hold_timestamp", Timestamp.class));
            history.add(dto);
        }

        return history;
    }


    /**
     * Fetches a paginated transaction history for a specified wallet.
     *
     * IMPORTANT: It doesn't return transactions from the transaction archive table.
     *
     * <p>This method amalgamates the transaction records from both the primary transaction table
     * and the transaction snapshot table (excluding ledger entries), providing a unified view
     * as if there's a single table behind the scenes. The results are sorted by timestamp in descending order,
     * so the latest transactions appear first.</p>
     *
     * <p>The returned object contains not only the list of transactions for the requested page but
     * also metadata about the pagination like the total number of records, current page number,
     * total pages, and page size.</p>
     *
     * @param walletId    The ID of the wallet for which the transaction history is to be fetched.
     * @param pageNumber  The page number to retrieve. Starts from 1.
     * @param pageSize    The number of transaction records per page.
     *
     * @return A {@code PagedResponse<TransactionHistoryDTO>} object containing the list of transactions
     *         for the specified page and pagination metadata.
     *
     * @throws IllegalArgumentException If provided {@code walletId} is null, or if {@code pageNumber} or {@code pageSize}
     *                                  are not greater than zero.
     * @throws NoResultException If there are no transactions for the provided {@code walletId}.
     * @throws PersistenceException For any unexpected database-related errors.
     *
     * @see PagedResponse
     * @see TransactionHistoryDTO
     */
    public PagedResponse<TransactionHistoryDTO> getPaginatedTransactionHistory(Integer walletId, int pageNumber, int pageSize) {
        String baseSql = """
            SELECT
                id, wallet_id, type, amount, status, hold_timestamp AS timestamp
            FROM
                transaction
            WHERE
                wallet_id = :walletId
            UNION
            SELECT
                id, wallet_id, type, amount, status, hold_timestamp AS timestamp
            FROM
                transaction_snapshot
            WHERE
                wallet_id = :walletId AND is_ledger_entry = FALSE
        """;

        String fetchSql = baseSql + " ORDER BY timestamp DESC";

        Query fetchQuery = entityManager.createNativeQuery(fetchSql, Tuple.class);
        fetchQuery.setParameter("walletId", walletId);
        fetchQuery.setFirstResult((pageNumber - 1) * pageSize); // Convert page number to 0-based index.
        fetchQuery.setMaxResults(pageSize);

        List<Tuple> results = fetchQuery.getResultList();

        List<TransactionHistoryDTO> data = results.stream()
                .map(tuple -> {
                    TransactionHistoryDTO dto = new TransactionHistoryDTO();
                    dto.setId(tuple.get("id", Integer.class));
                    dto.setWalletId(tuple.get("wallet_id", Integer.class));
                    dto.setType(tuple.get("type", String.class));
                    dto.setAmount(tuple.get("amount", Long.class));
                    dto.setStatus(tuple.get("status", String.class));
                    dto.setTimestamp(tuple.get("timestamp", Timestamp.class));
                    return dto;
                })
                .collect(Collectors.toList());

        // Now, let's get the total count for metadata
        String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS combined_data";
        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("walletId", walletId);

        long totalRecords = ((Number) countQuery.getSingleResult()).longValue();

        return new PagedResponse<>(data, pageNumber, pageSize, totalRecords);
    }
}

