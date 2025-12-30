package com.nosota.mwallet.api.dto;

import com.nosota.mwallet.api.model.TransactionStatus;
import com.nosota.mwallet.api.model.TransactionType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
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
    private LocalDateTime confirmRejectTimestamp;
    private LocalDateTime holdReserveTimestamp;
    private String description;
}
