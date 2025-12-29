package com.nosota.mwallet.model;

import com.nosota.mwallet.api.model.TransactionGroupStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

@Entity
@Table(name = "transaction_group")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TransactionGroup {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    private TransactionGroupStatus status;

    private String reason;

    /**
     * ID of the merchant associated with this transaction group.
     * Used for settlement operations to group transactions by merchant.
     * Nullable for non-merchant transactions.
     */
    @Column(name = "merchant_id")
    private Long merchantId;

    /**
     * ID of the buyer associated with this transaction group.
     * Used for refund operations to identify who receives the refund.
     * Nullable for non-buyer transactions.
     */
    @Column(name = "buyer_id")
    private Long buyerId;
}
