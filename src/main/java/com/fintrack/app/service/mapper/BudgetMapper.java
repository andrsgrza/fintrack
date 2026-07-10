package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.UserDTO;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Budget} and its DTO {@link BudgetDTO}.
 */
@Mapper(componentModel = "spring")
public interface BudgetMapper extends EntityMapper<BudgetDTO, Budget> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "accounts", source = "accounts", qualifiedByName = "financialAccountNameSet")
    @Mapping(target = "categories", source = "categories", qualifiedByName = "categoryNameSet")
    @Mapping(target = "tags", source = "tags", qualifiedByName = "tagNameSet")
    BudgetDTO toDto(Budget s);

    @Mapping(target = "removeAccounts", ignore = true)
    @Mapping(target = "removeCategories", ignore = true)
    @Mapping(target = "removeTags", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "tags", ignore = true)
    Budget toEntity(BudgetDTO budgetDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "removeAccounts", ignore = true)
    @Mapping(target = "removeCategories", ignore = true)
    @Mapping(target = "removeTags", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "accounts", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "tags", ignore = true)
    void partialUpdate(@MappingTarget Budget entity, BudgetDTO dto);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);

    @Named("financialAccountName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    FinancialAccountDTO toDtoFinancialAccountName(FinancialAccount financialAccount);

    @Named("financialAccountNameSet")
    default Set<FinancialAccountDTO> toDtoFinancialAccountNameSet(Set<FinancialAccount> financialAccount) {
        return financialAccount.stream().map(this::toDtoFinancialAccountName).collect(Collectors.toSet());
    }

    @Named("categoryName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    CategoryDTO toDtoCategoryName(Category category);

    @Named("categoryNameSet")
    default Set<CategoryDTO> toDtoCategoryNameSet(Set<Category> category) {
        return category.stream().map(this::toDtoCategoryName).collect(Collectors.toSet());
    }

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
