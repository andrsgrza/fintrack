package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.IngestionRecordDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link IngestionRecord} and its DTO {@link IngestionRecordDTO}.
 */
@Mapper(componentModel = "spring")
public interface IngestionRecordMapper extends EntityMapper<IngestionRecordDTO, IngestionRecord> {
    @Mapping(target = "financialTransaction", source = "financialTransaction", qualifiedByName = "financialTransactionId")
    @Mapping(target = "transactionIngestion", source = "transactionIngestion", qualifiedByName = "transactionIngestionId")
    IngestionRecordDTO toDto(IngestionRecord s);

    @Named("financialTransactionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    FinancialTransactionDTO toDtoFinancialTransactionId(FinancialTransaction financialTransaction);

    @Named("transactionIngestionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TransactionIngestionDTO toDtoTransactionIngestionId(TransactionIngestion transactionIngestion);
}
