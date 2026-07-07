package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.User;
import com.fintrack.app.domain.UserDashboardPreference;
import com.fintrack.app.service.dto.UserDTO;
import com.fintrack.app.service.dto.UserDashboardPreferenceDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link UserDashboardPreference} and its DTO {@link UserDashboardPreferenceDTO}.
 */
@Mapper(componentModel = "spring")
public interface UserDashboardPreferenceMapper extends EntityMapper<UserDashboardPreferenceDTO, UserDashboardPreference> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    UserDashboardPreferenceDTO toDto(UserDashboardPreference s);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);
}
