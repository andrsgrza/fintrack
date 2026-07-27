package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConditionDTO;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DescriptionNormalizationRuleConditionMapper
    extends EntityMapper<DescriptionNormalizationRuleConditionDTO, DescriptionNormalizationRuleCondition> {
    @Mapping(
        target = "descriptionNormalizationRule",
        source = "descriptionNormalizationRule",
        qualifiedByName = "descriptionNormalizationRuleName"
    )
    DescriptionNormalizationRuleConditionDTO toDto(DescriptionNormalizationRuleCondition s);

    @Mapping(target = "descriptionNormalizationRule", ignore = true)
    DescriptionNormalizationRuleCondition toEntity(DescriptionNormalizationRuleConditionDTO dto);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "descriptionNormalizationRule", ignore = true)
    void partialUpdate(@MappingTarget DescriptionNormalizationRuleCondition entity, DescriptionNormalizationRuleConditionDTO dto);

    @Named("descriptionNormalizationRuleName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    DescriptionNormalizationRuleDTO toDtoDescriptionNormalizationRuleName(DescriptionNormalizationRule rule);
}
