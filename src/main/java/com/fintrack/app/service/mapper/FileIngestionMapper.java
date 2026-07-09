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

    @Mapping(target = "transactionIngestion", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    FileIngestion toEntity(FileIngestionDTO fileIngestionDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "transactionIngestion", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void partialUpdate(@MappingTarget FileIngestion entity, FileIngestionDTO dto);

    @Named("transactionIngestionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TransactionIngestionDTO toDtoTransactionIngestionId(TransactionIngestion transactionIngestion);
}
