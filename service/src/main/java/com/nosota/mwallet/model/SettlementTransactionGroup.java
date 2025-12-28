package com.nosota.mwallet.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SettlementTransactionGroup - links settlements to their included transaction groups.
 *
 * <p>This is a join table that tracks which transaction groups were included
 * in which settlement operation. It provides:
 * <ul>
 *   <li>Audit trail - which orders were paid out in which settlement</li>
 *   <li>Prevents double settlement - can check if a group is already settled</li>
 *   <li>Historical data - amount from each group for reconciliation</li>
 * </ul>
 *
 * <p>Example:
 * <pre>
 * Settlement UUID-1 includes:
 *   - Transaction Group ORDER-001: 3000 cents
 *   - Transaction Group ORDER-002: 5000 cents
 *   - Transaction Group ORDER-003: 2000 cents
 *   Total: 10000 cents
 * </pre>
 */
@Entity
@Table(name = "settlement_transaction_group")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SettlementTransactionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ID of the settlement this group belongs to.
     */
    @Column(name = "settlement_id", nullable = false)
    private UUID settlementId;

    /**
     * ID of the transaction group included in the settlement.
     */
    @Column(name = "transaction_group_id", nullable = false)
    private UUID transactionGroupId;

    /**
     * Total amount from this transaction group (for audit purposes).
     * Sum of HOLD CREDIT transactions on ESCROW for this group.
     */
    @Column(name = "amount", nullable = false)
    private Long amount;

    /**
     * Timestamp when this record was created.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
