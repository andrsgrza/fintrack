package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.dto.UserDTO;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link TransactionRule} and its DTO {@link TransactionRuleDTO}.
 */
@Mapper(componentModel = "spring")
public interface TransactionRuleMapper extends EntityMapper<TransactionRuleDTO, TransactionRule> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "resultingCategory", source = "resultingCategory", qualifiedByName = "categoryName")
    @Mapping(target = "resultingTags", source = "resultingTags", qualifiedByName = "tagNameSet")
    TransactionRuleDTO toDto(TransactionRule s);

    @Mapping(target = "removeResultingTags", ignore = true)
    @Mapping(target = "removeConditions", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "resultingCategory", ignore = true)
    @Mapping(target = "resultingTags", ignore = true)
    @Mapping(target = "conditions", ignore = true)
    TransactionRule toEntity(TransactionRuleDTO transactionRuleDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "removeResultingTags", ignore = true)
    @Mapping(target = "removeConditions", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "resultingCategory", ignore = true)
    @Mapping(target = "resultingTags", ignore = true)
    @Mapping(target = "conditions", ignore = true)
    void partialUpdate(@MappingTarget TransactionRule entity, TransactionRuleDTO dto);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);

    @Named("categoryName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    CategoryDTO toDtoCategoryName(Category category);

    @Named("tagName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    TagDTO toDtoTagName(Tag tag);

    @Named("tagNameSet")
    default Set<TagDTO> toDtoTagNameSet(Set<Tag> tag) {
        return tag.stream().map(this::toDtoTagName).collect(Collectors.toSet());
    }
}
