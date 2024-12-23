package com.nosota.mwallet.dto;

import com.nosota.mwallet.model.OwnerType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class WalletOwnerDTO {
    private Integer id;
    private Integer walletId;
    private OwnerType ownerType;
    private String ownerRef;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
