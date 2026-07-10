package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiIngestion;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiIngestionDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ApiIngestion} and its DTO {@link ApiIngestionDTO}.
 */
@Mapper(componentModel = "spring")
public interface ApiIngestionMapper extends EntityMapper<ApiIngestionDTO, ApiIngestion> {
    @Mapping(target = "transactionIngestion", source = "transactionIngestion", qualifiedByName = "transactionIngestionId")
    @Mapping(target = "apiAccessToken", source = "apiAccessToken", qualifiedByName = "apiAccessTokenName")
    ApiIngestionDTO toDto(ApiIngestion s);

    @Mapping(target = "transactionIngestion", ignore = true)
    @Mapping(target = "apiAccessToken", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    ApiIngestion toEntity(ApiIngestionDTO apiIngestionDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "transactionIngestion", ignore = true)
    @Mapping(target = "apiAccessToken", ignore = true)
    @Mapping(target = "requestId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "receivedAt", ignore = true)
    void partialUpdate(@MappingTarget ApiIngestion entity, ApiIngestionDTO dto);

    @Named("transactionIngestionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TransactionIngestionDTO toDtoTransactionIngestionId(TransactionIngestion transactionIngestion);

    @Named("apiAccessTokenName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    ApiAccessTokenDTO toDtoApiAccessTokenName(ApiAccessToken apiAccessToken);
}
