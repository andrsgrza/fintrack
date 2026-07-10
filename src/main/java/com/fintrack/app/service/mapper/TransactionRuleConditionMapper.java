package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionRuleCondition} and its DTO {@link TransactionRuleConditionDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionRuleConditionMapper extends EntityMapper<TransactionRuleConditionDTO, TransactionRuleCondition> {
    @Mapping(target = "transactionRule", source = "transactionRule", qualifiedByName = "transactionRuleName")
    TransactionRuleConditionDTO toDto(TransactionRuleCondition s);

    @Mapping(target = "transactionRule", ignore = true)
    TransactionRuleCondition toEntity(TransactionRuleConditionDTO transactionRuleConditionDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "transactionRule", ignore = true)
    void partialUpdate(@MappingTarget TransactionRuleCondition entity, TransactionRuleConditionDTO dto);

    @Named("transactionRuleName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    TransactionRuleDTO toDtoTransactionRuleName(TransactionRule transactionRule);
}
