package com.nosota.mwallet.dto;

import com.nosota.mwallet.model.TransactionSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface TransactionSnapshotMapper {
    TransactionSnapshotMapper INSTANCE = Mappers.getMapper(TransactionSnapshotMapper.class);

//    @Mapping(target = "snapshotDate", ignore = true)
//    @Mapping(target = "isLedgerEntry", ignore = true)
    TransactionDTO toDTO(TransactionSnapshot transaction);

    TransactionSnapshot toEntity(TransactionDTO transactionDTO);

    List<TransactionDTO> toDTOList(List<TransactionSnapshot> transactions);

    List<TransactionSnapshot> toEntityList(List<TransactionDTO> transactionDTOs);
}
