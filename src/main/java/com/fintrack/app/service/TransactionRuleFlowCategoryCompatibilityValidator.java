package com.fintrack.app.service;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TransactionRuleFlowCategoryCompatibilityValidator {

    private final TransactionRuleConditionValidator transactionRuleConditionValidator;

    public TransactionRuleFlowCategoryCompatibilityValidator(TransactionRuleConditionValidator transactionRuleConditionValidator) {
        this.transactionRuleConditionValidator = transactionRuleConditionValidator;
    }

    public void validate(TransactionRule rule, List<TransactionRuleCondition> conditions) {
        Category resultingCategory = rule.getResultingCategory();
        if (
            resultingCategory == null ||
            resultingCategory.getCategoryType() == null ||
            resultingCategory.getCategoryType() == CategoryType.BOTH
        ) {
            return;
        }

        CategoryType categoryType = resultingCategory.getCategoryType();
        if (rule.getConditionLogic() != RuleConditionLogic.ALL) {
            throw new IllegalArgumentException("Expense/income categories require condition logic ALL");
        }

        Set<TransactionFlow> allowedFlows = effectiveFlows(conditions);
        if (allowedFlows.isEmpty()) {
            throw new IllegalArgumentException("Resulting category requires a compatible Flow condition");
        }
        if (categoryType == CategoryType.EXPENSE && !allowedFlows.equals(EnumSet.of(TransactionFlow.OUT))) {
            throw new IllegalArgumentException("Expense categories require Flow = OUT");
        }
        if (categoryType == CategoryType.INCOME && !allowedFlows.equals(EnumSet.of(TransactionFlow.IN))) {
            throw new IllegalArgumentException("Income categories require Flow = IN");
        }
    }

    private Set<TransactionFlow> effectiveFlows(List<TransactionRuleCondition> conditions) {
        Set<TransactionFlow> allowedFlows = EnumSet.of(TransactionFlow.IN, TransactionFlow.OUT);
        for (TransactionRuleCondition condition : conditions) {
            if (condition.getField() != TransactionRuleField.FLOW) {
                continue;
            }
            applyFlowCondition(allowedFlows, condition);
        }
        return allowedFlows;
    }

    private void applyFlowCondition(Set<TransactionFlow> allowedFlows, TransactionRuleCondition condition) {
        Set<TransactionFlow> conditionFlows = parseFlows(condition);
        switch (condition.getOperator()) {
            case EQUALS, IN -> allowedFlows.retainAll(conditionFlows);
            case NOT_EQUALS, NOT_IN -> allowedFlows.removeAll(conditionFlows);
            default -> throw new IllegalArgumentException("Operator is not allowed for field");
        }
    }

    private Set<TransactionFlow> parseFlows(TransactionRuleCondition condition) {
        Set<TransactionFlow> flows = EnumSet.noneOf(TransactionFlow.class);
        if (condition.getOperator() == RuleOperator.IN || condition.getOperator() == RuleOperator.NOT_IN) {
            for (String token : transactionRuleConditionValidator.parseListTokens(condition.getValue())) {
                flows.add(TransactionFlow.valueOf(token.trim()));
            }
            return flows;
        }
        flows.add(TransactionFlow.valueOf(condition.getValue().trim()));
        return flows;
    }
}
