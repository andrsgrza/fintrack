package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionIngestion} and its DTO {@link TransactionIngestionDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionIngestionMapper extends EntityMapper<TransactionIngestionDTO, TransactionIngestion> {
    @Mapping(target = "account", source = "account", qualifiedByName = "financialAccountName")
    TransactionIngestionDTO toDto(TransactionIngestion s);

    @Mapping(target = "account", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "recordsReceived", ignore = true)
    @Mapping(target = "recordsCreated", ignore = true)
    @Mapping(target = "recordsSkipped", ignore = true)
    @Mapping(target = "recordsRejected", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "removeFinancialTransactions", ignore = true)
    @Mapping(target = "removeRecords", ignore = true)
    TransactionIngestion toEntity(TransactionIngestionDTO transactionIngestionDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "ingestionType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "startedAt", ignore = true)
    void partialUpdate(@MappingTarget TransactionIngestion entity, TransactionIngestionDTO dto);

    @Named("financialAccountName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    FinancialAccountDTO toDtoFinancialAccountName(FinancialAccount financialAccount);
}
