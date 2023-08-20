package com.nosota.mwallet.dto;

import com.nosota.mwallet.model.WalletOwner;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface WalletOwnerMapper {
    WalletOwnerMapper INSTANCE = Mappers.getMapper(WalletOwnerMapper.class);

    WalletOwner toEntity(WalletOwnerDTO dto);

    WalletOwnerDTO toDTO(WalletOwner entity);

    List<WalletOwner> toEntity(List<WalletOwnerDTO> dtoList);

    List<WalletOwnerDTO> toDTO(List<WalletOwner> entityList);
}
