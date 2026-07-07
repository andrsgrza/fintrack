package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.FinancialSubscription;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.User;
import com.fintrack.app.service.dto.BudgetDTO;
import com.fintrack.app.service.dto.FinancialSubscriptionDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.service.dto.TagDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.dto.UserDTO;
import java.util.Set;
import java.util.stream.Collectors;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Tag} and its DTO {@link TagDTO}.
 */
@Mapper(componentModel = "spring")
public interface TagMapper extends EntityMapper<TagDTO, Tag> {
    @Mapping(target = "user", source = "user", qualifiedByName = "userLogin")
    @Mapping(target = "financialTransactions", source = "financialTransactions", qualifiedByName = "financialTransactionIdSet")
    @Mapping(target = "transactionRules", source = "transactionRules", qualifiedByName = "transactionRuleIdSet")
    @Mapping(target = "subscriptions", source = "subscriptions", qualifiedByName = "financialSubscriptionIdSet")
    @Mapping(target = "budgets", source = "budgets", qualifiedByName = "budgetIdSet")
    TagDTO toDto(Tag s);

    @Mapping(target = "financialTransactions", ignore = true)
    @Mapping(target = "removeFinancialTransactions", ignore = true)
    @Mapping(target = "transactionRules", ignore = true)
    @Mapping(target = "removeTransactionRules", ignore = true)
    @Mapping(target = "subscriptions", ignore = true)
    @Mapping(target = "removeSubscriptions", ignore = true)
    @Mapping(target = "budgets", ignore = true)
    @Mapping(target = "removeBudgets", ignore = true)
    Tag toEntity(TagDTO tagDTO);

    @Named("userLogin")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "login", source = "login")
    UserDTO toDtoUserLogin(User user);

    @Named("financialTransactionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    FinancialTransactionDTO toDtoFinancialTransactionId(FinancialTransaction financialTransaction);

    @Named("financialTransactionIdSet")
    default Set<FinancialTransactionDTO> toDtoFinancialTransactionIdSet(Set<FinancialTransaction> financialTransaction) {
        return financialTransaction.stream().map(this::toDtoFinancialTransactionId).collect(Collectors.toSet());
    }

    @Named("transactionRuleId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TransactionRuleDTO toDtoTransactionRuleId(TransactionRule transactionRule);

    @Named("transactionRuleIdSet")
    default Set<TransactionRuleDTO> toDtoTransactionRuleIdSet(Set<TransactionRule> transactionRule) {
        return transactionRule.stream().map(this::toDtoTransactionRuleId).collect(Collectors.toSet());
    }

    @Named("financialSubscriptionId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    FinancialSubscriptionDTO toDtoFinancialSubscriptionId(FinancialSubscription financialSubscription);

    @Named("financialSubscriptionIdSet")
    default Set<FinancialSubscriptionDTO> toDtoFinancialSubscriptionIdSet(Set<FinancialSubscription> financialSubscription) {
        return financialSubscription.stream().map(this::toDtoFinancialSubscriptionId).collect(Collectors.toSet());
    }

    @Named("budgetId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    BudgetDTO toDtoBudgetId(Budget budget);

    @Named("budgetIdSet")
    default Set<BudgetDTO> toDtoBudgetIdSet(Set<Budget> budget) {
        return budget.stream().map(this::toDtoBudgetId).collect(Collectors.toSet());
    }
}
