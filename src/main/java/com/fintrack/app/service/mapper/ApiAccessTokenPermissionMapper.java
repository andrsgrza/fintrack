package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.ApiAccessTokenPermission;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.ApiAccessTokenPermissionDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ApiAccessTokenPermission} and its DTO {@link ApiAccessTokenPermissionDTO}.
 */
@Mapper(componentModel = "spring")
public interface ApiAccessTokenPermissionMapper extends EntityMapper<ApiAccessTokenPermissionDTO, ApiAccessTokenPermission> {
    @Mapping(target = "apiAccessToken", source = "apiAccessToken", qualifiedByName = "apiAccessTokenName")
    ApiAccessTokenPermissionDTO toDto(ApiAccessTokenPermission s);

    @Mapping(target = "apiAccessToken", ignore = true)
    ApiAccessTokenPermission toEntity(ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "apiAccessToken", ignore = true)
    void partialUpdate(@MappingTarget ApiAccessTokenPermission entity, ApiAccessTokenPermissionDTO dto);

    @Named("apiAccessTokenName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    ApiAccessTokenDTO toDtoApiAccessTokenName(ApiAccessToken apiAccessToken);
}
