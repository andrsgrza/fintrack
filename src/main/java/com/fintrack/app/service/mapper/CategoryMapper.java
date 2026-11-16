package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.UserDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Category} and its DTO {@link CategoryDTO}.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper extends EntityMapper<CategoryDTO, Category> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "parentCategory", source = "parentCategory", qualifiedByName = "categoryName")
    CategoryDTO toDto(Category s);

    @Mapping(target = "user", ignore = true)
    Category toEntity(CategoryDTO categoryDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "parentCategory", ignore = true)
    void partialUpdate(@MappingTarget Category entity, CategoryDTO dto);

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
}
