package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.UserDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ApiAccessToken} and its DTO {@link ApiAccessTokenDTO}.
 */
@Mapper(componentModel = "spring")
public interface ApiAccessTokenMapper extends EntityMapper<ApiAccessTokenDTO, ApiAccessToken> {
    @Mapping(target = "tokenHash", ignore = true)
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    ApiAccessTokenDTO toDto(ApiAccessToken s);

    @Mapping(target = "user", ignore = true)
    ApiAccessToken toEntity(ApiAccessTokenDTO apiAccessTokenDTO);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "tokenHash", ignore = true)
    @Mapping(target = "tokenPrefix", ignore = true)
    void partialUpdate(@MappingTarget ApiAccessToken entity, ApiAccessTokenDTO dto);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);
}
