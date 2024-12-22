package com.nosota.mwallet.dto;

import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class TransactionHistoryDTO {
    private UUID referenceId;
    private Integer walletId;
    private String type; // e.g., 'CREDIT', 'DEBIT'
    private Long amount;
    private String status; // e.g., 'HOLD', 'CONFIRMED', 'REJECTED'
    private Timestamp timestamp; // this could be either hold_reserve_timestamp or confirm_reject_timestamp
}
