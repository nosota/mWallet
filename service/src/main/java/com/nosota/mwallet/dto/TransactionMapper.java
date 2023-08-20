package com.nosota.mwallet.dto;

import com.nosota.mwallet.model.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface TransactionMapper {

    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    TransactionDTO toDTO(Transaction transaction);

    Transaction toEntity(TransactionDTO transactionDTO);

    List<TransactionDTO> toDTOList(List<Transaction> transactions);

    List<Transaction> toEntityList(List<TransactionDTO> transactionDTOs);
}
