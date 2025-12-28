package com.nosota.mwallet.mapper;

import com.nosota.mwallet.api.dto.TransactionDTO;
import com.nosota.mwallet.model.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * MapStruct mapper for Transaction entity to TransactionDTO conversion.
 */
@Mapper
public interface TransactionMapper {

    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    /**
     * Maps Transaction entity to TransactionDTO.
     *
     * @param transaction Transaction entity
     * @return TransactionDTO
     */
    TransactionDTO toDTO(Transaction transaction);

    /**
     * Maps list of Transaction entities to list of TransactionDTOs.
     *
     * @param transactions List of Transaction entities
     * @return List of TransactionDTOs
     */
    List<TransactionDTO> toDTOList(List<Transaction> transactions);
}
