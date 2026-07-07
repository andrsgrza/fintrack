package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.FileIngestion;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.service.dto.FileIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link FileIngestion} and its DTO {@link FileIngestionDTO}.
 */
@Mapper(componentModel = "spring")
public interface FileIngestionMapper extends EntityMapper<FileIngestionDTO, FileIngestion> {
    @Mapping(target = "transactionIngestion", source = "transactionIngestion", qualifiedByName = "transactionIngestionId")
    FileIngestionDTO toDto(FileIngestion s);

    @Named("transactionIngestionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TransactionIngestionDTO toDtoTransactionIngestionId(TransactionIngestion transactionIngestion);
}
