package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.InternalTransfer;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.InternalTransferDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link InternalTransfer} and its DTO {@link InternalTransferDTO}.
 */
@Mapper(componentModel = "spring")
public interface InternalTransferMapper extends EntityMapper<InternalTransferDTO, InternalTransfer> {
    @Mapping(target = "outgoingTransaction", source = "outgoingTransaction", qualifiedByName = "financialTransactionId")
    @Mapping(target = "incomingTransaction", source = "incomingTransaction", qualifiedByName = "financialTransactionId")
    InternalTransferDTO toDto(InternalTransfer s);

    @Named("financialTransactionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    FinancialTransactionDTO toDtoFinancialTransactionId(FinancialTransaction financialTransaction);
}
