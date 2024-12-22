package com.nosota.mwallet.dto;

import com.nosota.mwallet.model.TransactionStatus;
import com.nosota.mwallet.model.TransactionType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class TransactionDTO {
    private Integer id;
    private UUID referenceId;
    private Integer walletId;
    private Long amount;
    private TransactionStatus status;
    private TransactionType type;
    private LocalDateTime holdTimestamp;
    private LocalDateTime confirmRejectTimestamp;
    private String description;
}
