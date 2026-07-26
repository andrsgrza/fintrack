package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleDTO;
import com.fintrack.app.service.dto.UserDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DescriptionNormalizationRuleMapper extends EntityMapper<DescriptionNormalizationRuleDTO, DescriptionNormalizationRule> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    DescriptionNormalizationRuleDTO toDto(DescriptionNormalizationRule s);

    @Mapping(target = "removeConditions", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "conditions", ignore = true)
    DescriptionNormalizationRule toEntity(DescriptionNormalizationRuleDTO dto);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "removeConditions", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "conditions", ignore = true)
    void partialUpdate(@MappingTarget DescriptionNormalizationRule entity, DescriptionNormalizationRuleDTO dto);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);
}
