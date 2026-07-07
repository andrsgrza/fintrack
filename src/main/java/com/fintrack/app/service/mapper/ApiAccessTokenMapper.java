package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.UserDTO;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ApiAccessToken} and its DTO {@link ApiAccessTokenDTO}.
 */
@Mapper(componentModel = "spring")
public interface ApiAccessTokenMapper extends EntityMapper<ApiAccessTokenDTO, ApiAccessToken> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "accounts", source = "accounts", qualifiedByName = "financialAccountNameSet")
    ApiAccessTokenDTO toDto(ApiAccessToken s);

    @Mapping(target = "removeAccounts", ignore = true)
    ApiAccessToken toEntity(ApiAccessTokenDTO apiAccessTokenDTO);

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
}
