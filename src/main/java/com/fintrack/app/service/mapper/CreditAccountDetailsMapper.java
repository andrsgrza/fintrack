package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.CreditAccountDetails;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.service.dto.CreditAccountDetailsDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link CreditAccountDetails} and its DTO {@link CreditAccountDetailsDTO}.
 */
@Mapper(componentModel = "spring")
public interface CreditAccountDetailsMapper extends EntityMapper<CreditAccountDetailsDTO, CreditAccountDetails> {
    @Mapping(target = "account", source = "account", qualifiedByName = "financialAccountName")
    CreditAccountDetailsDTO toDto(CreditAccountDetails s);

    @Mapping(target = "account", ignore = true)
    CreditAccountDetails toEntity(CreditAccountDetailsDTO creditAccountDetailsDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "account", ignore = true)
    void partialUpdate(@MappingTarget CreditAccountDetails entity, CreditAccountDetailsDTO dto);

    @Named("financialAccountName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    FinancialAccountDTO toDtoFinancialAccountName(FinancialAccount financialAccount);
}
