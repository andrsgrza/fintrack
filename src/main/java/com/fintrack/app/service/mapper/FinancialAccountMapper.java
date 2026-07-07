package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.ApiAccessToken;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.ApiAccessTokenDTO;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.TransactionIngestionDTO;
import com.fintrack.app.service.dto.UserDTO;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link FinancialAccount} and its DTO {@link FinancialAccountDTO}.
 */
@Mapper(componentModel = "spring")
public interface FinancialAccountMapper extends EntityMapper<FinancialAccountDTO, FinancialAccount> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "budgets", source = "budgets", qualifiedByName = "budgetIdSet")
    @Mapping(target = "transactionIngestions", source = "transactionIngestions", qualifiedByName = "transactionIngestionIdSet")
    @Mapping(target = "apiAccessTokens", source = "apiAccessTokens", qualifiedByName = "apiAccessTokenIdSet")
    FinancialAccountDTO toDto(FinancialAccount s);

    @Mapping(target = "budgets", ignore = true)
    @Mapping(target = "removeBudgets", ignore = true)
    @Mapping(target = "transactionIngestions", ignore = true)
    @Mapping(target = "removeTransactionIngestions", ignore = true)
    @Mapping(target = "apiAccessTokens", ignore = true)
    @Mapping(target = "removeApiAccessTokens", ignore = true)
    FinancialAccount toEntity(FinancialAccountDTO financialAccountDTO);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);

    @Named("budgetId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    BudgetDTO toDtoBudgetId(Budget budget);

    @Named("budgetIdSet")
    default Set<BudgetDTO> toDtoBudgetIdSet(Set<Budget> budget) {
        return budget.stream().map(this::toDtoBudgetId).collect(Collectors.toSet());
    }

    @Named("transactionIngestionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TransactionIngestionDTO toDtoTransactionIngestionId(TransactionIngestion transactionIngestion);

    @Named("transactionIngestionIdSet")
    default Set<TransactionIngestionDTO> toDtoTransactionIngestionIdSet(Set<TransactionIngestion> transactionIngestion) {
        return transactionIngestion.stream().map(this::toDtoTransactionIngestionId).collect(Collectors.toSet());
    }

    @Named("apiAccessTokenId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    ApiAccessTokenDTO toDtoApiAccessTokenId(ApiAccessToken apiAccessToken);

    @Named("apiAccessTokenIdSet")
    default Set<ApiAccessTokenDTO> toDtoApiAccessTokenIdSet(Set<ApiAccessToken> apiAccessToken) {
        return apiAccessToken.stream().map(this::toDtoApiAccessTokenId).collect(Collectors.toSet());
    }
}
