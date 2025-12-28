package com.nosota.mwallet.service;

import com.nosota.mwallet.api.dto.PagedResponse;
import com.nosota.mwallet.api.dto.TransactionHistoryDTO;
import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Validated
@AllArgsConstructor
@Slf4j
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
    public List<TransactionHistoryDTO> getFullTransactionHistory(@NotNull Integer walletId) {
        String sql = """
            SELECT id, reference_id, wallet_id, type, amount, status, hold_reserve_timestamp, confirm_reject_timestamp, description
            FROM (
                -- Fetch from main transaction table
                SELECT id, reference_id, wallet_id, type, amount, status, hold_reserve_timestamp, confirm_reject_timestamp, description 
                FROM transaction 
                WHERE wallet_id = :walletId
                UNION ALL
                -- Fetch from transaction snapshot table
                SELECT id, reference_id, wallet_id, type, amount, status, hold_reserve_timestamp, confirm_reject_timestamp, null as description
                FROM transaction_snapshot 
                WHERE wallet_id = :walletId AND is_ledger_entry = FALSE
                UNION ALL
                -- Fetch from transaction snapshot archive table
                SELECT id, reference_id, wallet_id, type, amount, status, hold_reserve_timestamp, confirm_reject_timestamp, null as description
                FROM transaction_snapshot_archive 
                WHERE wallet_id = :walletId
            ) AS combined_data
            ORDER BY confirm_reject_timestamp DESC, hold_reserve_timestamp DESC
        """;

        Query query = entityManager.createNativeQuery(sql, Tuple.class);
        query.setParameter("walletId", walletId);

        @SuppressWarnings("unchecked")
        List<Tuple> results = query.getResultList();

        List<TransactionHistoryDTO> history = new ArrayList<>();
        for (Tuple tuple : results) {
            Timestamp tm = tuple.get("confirm_reject_timestamp", Timestamp.class);
            if(tm == null) {
                tm = tuple.get("hold_reserve_timestamp", Timestamp.class);
            }
            if(tm == null) {
                throw new IllegalArgumentException("At least confirm_reject_timestamp or hold_reserve_timestamp must be not null.");
            }
            TransactionHistoryDTO dto = new TransactionHistoryDTO(
                    tuple.get("reference_id", UUID.class),
                    tuple.get("wallet_id", Integer.class),
                    tuple.get("type", String.class),
                    tuple.get("amount", Long.class),
                    tuple.get("status", String.class),
                    tm
            );
            history.add(dto);
        }

        return history;
    }

    public PagedResponse<TransactionHistoryDTO> getPaginatedTransactionHistory(@NotNull Integer walletId, @Positive int pageNumber, @Positive int pageSize) {
        return getPaginatedTransactionHistory(walletId, pageNumber, pageSize, List.of(), List.of());
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
     * @param walletId      The ID of the wallet for which the transaction history is to be fetched.
     * @param pageNumber    The page number to retrieve. Starts from 1.
     * @param pageSize      The number of transaction records per page.
     * @param statusFilters A list of transaction statuses to filter by. Empty list means no filtering by status.
     * @param typeFilters   A list of transaction types to filter by. Empty list means no filtering by type.
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
    public PagedResponse<TransactionHistoryDTO> getPaginatedTransactionHistory(
            @NotNull Integer walletId,
            @Positive int pageNumber,
            @Positive int pageSize,
            @NotNull List<TransactionType> typeFilters,
            @NotNull List<TransactionStatus> statusFilters) {

        List<String> typeFilterStrings = typeFilters.stream().map(Enum::name).collect(Collectors.toList());
        List<String> statusFilterStrings = statusFilters.stream().map(Enum::name).collect(Collectors.toList());

        String statusCondition = "";
        if (!statusFilterStrings.isEmpty()) {
            statusCondition = " AND status IN :statusFilters \n";
        }

        String typeCondition = "";
        if (!typeFilterStrings.isEmpty()) {
            typeCondition = " AND type IN :typeFilters \n";
        }

        String baseSql = """
            SELECT id, reference_id, wallet_id, type, amount, status, COALESCE(confirm_reject_timestamp, hold_reserve_timestamp) AS timestamp
            FROM transaction
            WHERE wallet_id = :walletId
            """
                + statusCondition + typeCondition +
            """
            UNION
            SELECT id, reference_id, wallet_id, type, amount, status, COALESCE(confirm_reject_timestamp, hold_reserve_timestamp) AS timestamp
            FROM transaction_snapshot
            WHERE wallet_id = :walletId AND is_ledger_entry = FALSE
            """ + statusCondition + typeCondition;

        String fetchSql = baseSql + " ORDER BY timestamp DESC";
        Query fetchQuery = entityManager.createNativeQuery(fetchSql, Tuple.class);
        fetchQuery.setParameter("walletId", walletId);
        if (!statusFilterStrings.isEmpty()) {
            fetchQuery.setParameter("statusFilters", statusFilterStrings);
        }
        if (!typeFilterStrings.isEmpty()) {
            fetchQuery.setParameter("typeFilters", typeFilterStrings);
        }
        fetchQuery.setFirstResult((pageNumber - 1) * pageSize);
        fetchQuery.setMaxResults(pageSize);

        @SuppressWarnings("unchecked")
        List<Tuple> results = fetchQuery.getResultList();

        List<TransactionHistoryDTO> data = results.stream()
                .map(tuple -> {
                    TransactionHistoryDTO dto = new TransactionHistoryDTO(
                            tuple.get("reference_id", UUID.class),
                            tuple.get("wallet_id", Integer.class),
                            tuple.get("type", String.class),
                            tuple.get("amount", Long.class),
                            tuple.get("status", String.class),
                            tuple.get("timestamp", Timestamp.class)
                    );
                    return dto;
                })
                .collect(Collectors.toList());

        String countSql = "SELECT COUNT(*) FROM (" + baseSql + ") AS combined_data";
        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("walletId", walletId);
        if (!statusFilterStrings.isEmpty()) {
            countQuery.setParameter("statusFilters", statusFilterStrings);
        }
        if (!typeFilterStrings.isEmpty()) {
            countQuery.setParameter("typeFilters", typeFilterStrings);
        }

        int totalRecords = ((Number) countQuery.getSingleResult()).intValue();
        return new PagedResponse<>(data, pageNumber, data.size(), totalRecords);
    }
}
