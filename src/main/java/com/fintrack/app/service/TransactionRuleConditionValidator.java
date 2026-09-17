package com.fintrack.app.service;

import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TransactionRuleConditionValidator {

    private static final Set<RuleOperator> TEXT_OPERATORS = EnumSet.of(
        RuleOperator.EQUALS,
        RuleOperator.NOT_EQUALS,
        RuleOperator.CONTAINS,
        RuleOperator.NOT_CONTAINS,
        RuleOperator.STARTS_WITH,
        RuleOperator.ENDS_WITH,
        RuleOperator.REGEX,
        RuleOperator.IN,
        RuleOperator.NOT_IN
    );

    private static final Set<RuleOperator> ENUM_OPERATORS = EnumSet.of(
        RuleOperator.EQUALS,
        RuleOperator.NOT_EQUALS,
        RuleOperator.IN,
        RuleOperator.NOT_IN
    );

    private static final Set<RuleOperator> AMOUNT_OPERATORS = EnumSet.of(
        RuleOperator.EQUALS,
        RuleOperator.NOT_EQUALS,
        RuleOperator.GREATER_THAN,
        RuleOperator.GREATER_THAN_OR_EQUAL,
        RuleOperator.LESS_THAN,
        RuleOperator.LESS_THAN_OR_EQUAL,
        RuleOperator.BETWEEN,
        RuleOperator.IN,
        RuleOperator.NOT_IN
    );

    private static final Set<RuleOperator> DATE_OPERATORS = EnumSet.of(
        RuleOperator.EQUALS,
        RuleOperator.NOT_EQUALS,
        RuleOperator.BEFORE,
        RuleOperator.AFTER,
        RuleOperator.BETWEEN,
        RuleOperator.IN,
        RuleOperator.NOT_IN
    );

    private static final Set<RuleOperator> ACCOUNT_OPERATORS = EnumSet.of(
        RuleOperator.EQUALS,
        RuleOperator.NOT_EQUALS,
        RuleOperator.IN,
        RuleOperator.NOT_IN
    );

    private final TransactionRuleConditionRepository transactionRuleConditionRepository;

    private final FinancialAccountRepository financialAccountRepository;

    public TransactionRuleConditionValidator(
        TransactionRuleConditionRepository transactionRuleConditionRepository,
        FinancialAccountRepository financialAccountRepository
    ) {
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
        this.financialAccountRepository = financialAccountRepository;
    }

    public void validateCondition(TransactionRuleCondition condition, Long excludeId) {
        validateCondition(condition, excludeId, null);
    }

    /**
     * Validates a condition while allowing an unchanged inactive ACCOUNT reference that already existed before the
     * current edit. A newly introduced or changed inactive account reference is always rejected.
     */
    public void validateCondition(TransactionRuleCondition condition, Long excludeId, TransactionRuleCondition existingCondition) {
        validatePosition(condition.getPosition());
        validateRequiredFields(condition);
        validateValuePresent(condition.getValue());
        validateFieldOperatorCompatibility(condition.getField(), condition.getOperator());
        validateSecondValueRules(condition);
        validateValueSemantics(condition, retainsExistingAccountReference(condition, existingCondition));
        validateDuplicate(condition, excludeId);
    }

    public void validateConditionSet(List<TransactionRuleCondition> conditions) {
        validateConditionSet(conditions, List.of());
    }

    /**
     * Validates a configured condition set while retaining only unchanged historical ACCOUNT references.
     */
    public void validateConditionSet(List<TransactionRuleCondition> conditions, List<TransactionRuleCondition> existingConditions) {
        Set<String> normalizedConditions = new LinkedHashSet<>();
        for (TransactionRuleCondition condition : conditions) {
            TransactionRuleCondition existingCondition = existingConditions
                .stream()
                .filter(existing -> existing.getId() != null && existing.getId().equals(condition.getId()))
                .findFirst()
                .orElse(null);
            validateConditionWithoutRepositoryDuplicateCheck(condition, existingCondition);
            String normalizedCondition = normalizedConditionKey(condition);
            if (!normalizedConditions.add(normalizedCondition)) {
                throw new IllegalArgumentException("Duplicate condition for transaction rule");
            }
        }
    }

    private void validateConditionWithoutRepositoryDuplicateCheck(
        TransactionRuleCondition condition,
        TransactionRuleCondition existingCondition
    ) {
        validatePosition(condition.getPosition());
        validateRequiredFields(condition);
        validateValuePresent(condition.getValue());
        validateFieldOperatorCompatibility(condition.getField(), condition.getOperator());
        validateSecondValueRules(condition);
        validateValueSemantics(condition, retainsExistingAccountReference(condition, existingCondition));
    }

    public List<String> parseListTokens(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Value is required");
        }
        String[] parts = rawValue.split(",");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) {
                throw new IllegalArgumentException("Empty token in list value");
            }
            tokens.add(token);
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Value is required");
        }
        return tokens;
    }

    private void validatePosition(Integer position) {
        if (position == null || position < 0) {
            throw new IllegalArgumentException("Position must be greater than or equal to zero");
        }
    }

    private void validateRequiredFields(TransactionRuleCondition condition) {
        if (condition.getField() == null) {
            throw new IllegalArgumentException("Field is required");
        }
        if (condition.getOperator() == null) {
            throw new IllegalArgumentException("Operator is required");
        }
        if (condition.getCaseSensitive() == null) {
            throw new IllegalArgumentException("Case sensitive is required");
        }
        if (condition.getTransactionRule() == null || condition.getTransactionRule().getUser() == null) {
            throw new IllegalArgumentException("Transaction rule is required");
        }
    }

    private void validateValuePresent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value is required");
        }
        if (value.length() > 1000) {
            throw new IllegalArgumentException("Value must be less than or equal to 1000 characters");
        }
    }

    private void validateFieldOperatorCompatibility(TransactionRuleField field, RuleOperator operator) {
        Set<RuleOperator> allowed = allowedOperatorsFor(field);
        if (!allowed.contains(operator)) {
            throw new IllegalArgumentException("Operator is not allowed for field");
        }
    }

    private Set<RuleOperator> allowedOperatorsFor(TransactionRuleField field) {
        return switch (field) {
            case DESCRIPTION, EXTERNAL_REFERENCE -> TEXT_OPERATORS;
            case FLOW, ORIGIN -> ENUM_OPERATORS;
            case AMOUNT -> AMOUNT_OPERATORS;
            case TRANSACTION_DATE, POSTING_DATE -> DATE_OPERATORS;
            case ACCOUNT -> ACCOUNT_OPERATORS;
        };
    }

    private void validateSecondValueRules(TransactionRuleCondition condition) {
        boolean isBetween = condition.getOperator() == RuleOperator.BETWEEN;
        boolean hasSecondValue = !isBlank(condition.getSecondValue());

        if (condition.getSecondValue() != null && condition.getSecondValue().length() > 1000) {
            throw new IllegalArgumentException("Second value must be less than or equal to 1000 characters");
        }

        if (isBetween) {
            if (!hasSecondValue) {
                throw new IllegalArgumentException("Second value is required for BETWEEN operator");
            }
            return;
        }
        if (hasSecondValue) {
            throw new IllegalArgumentException("Second value is only allowed for BETWEEN operator");
        }
    }

    private void validateValueSemantics(TransactionRuleCondition condition, boolean allowExistingInactiveAccountReference) {
        TransactionRuleField field = condition.getField();
        RuleOperator operator = condition.getOperator();
        String ownerLogin = condition.getTransactionRule().getUser().getLogin();

        validateFieldValue(
            field,
            operator,
            condition.getValue(),
            condition.getCaseSensitive(),
            ownerLogin,
            allowExistingInactiveAccountReference
        );

        if (operator == RuleOperator.BETWEEN) {
            validateBetweenRange(field, condition.getValue(), condition.getSecondValue());
        }
    }

    private void validateBetweenRange(TransactionRuleField field, String value, String secondValue) {
        switch (field) {
            case AMOUNT -> {
                BigDecimal lower = parseAmount(value);
                BigDecimal upper = parseAmount(secondValue);
                if (lower.compareTo(upper) > 0) {
                    throw new IllegalArgumentException("Value must be less than or equal to second value");
                }
            }
            case TRANSACTION_DATE, POSTING_DATE -> {
                LocalDate lower = parseDate(value);
                LocalDate upper = parseDate(secondValue);
                if (lower.isAfter(upper)) {
                    throw new IllegalArgumentException("Value must be less than or equal to second value");
                }
            }
            default -> throw new IllegalArgumentException("BETWEEN operator is not allowed for field");
        }
    }

    private void validateFieldValue(
        TransactionRuleField field,
        RuleOperator operator,
        String rawValue,
        Boolean caseSensitive,
        String ownerLogin,
        boolean allowExistingInactiveAccountReference
    ) {
        if (operator == RuleOperator.IN || operator == RuleOperator.NOT_IN) {
            List<String> tokens = parseListTokens(rawValue);
            for (String token : tokens) {
                validateSingleToken(field, operator, token, caseSensitive, ownerLogin, allowExistingInactiveAccountReference);
            }
            return;
        }
        validateSingleToken(field, operator, rawValue.trim(), caseSensitive, ownerLogin, allowExistingInactiveAccountReference);
    }

    private void validateSingleToken(
        TransactionRuleField field,
        RuleOperator operator,
        String token,
        Boolean caseSensitive,
        String ownerLogin,
        boolean allowExistingInactiveAccountReference
    ) {
        switch (field) {
            case DESCRIPTION, EXTERNAL_REFERENCE -> validateTextToken(operator, token);
            case FLOW -> validateFlowToken(token);
            case ORIGIN -> validateOriginToken(token);
            case AMOUNT -> parseAmount(token);
            case TRANSACTION_DATE, POSTING_DATE -> parseDate(token);
            case ACCOUNT -> validateAccountId(token, ownerLogin, allowExistingInactiveAccountReference);
        }
    }

    private void validateTextToken(RuleOperator operator, String token) {
        if (operator == RuleOperator.REGEX) {
            try {
                Pattern.compile(token);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regular expression");
            }
        }
    }

    private void validateFlowToken(String token) {
        try {
            TransactionFlow.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid flow value");
        }
    }

    private void validateOriginToken(String token) {
        try {
            TransactionOrigin.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid origin value");
        }
    }

    private void validateAccountId(String token, String ownerLogin, boolean allowExistingInactiveAccountReference) {
        Long accountId = parseAccountId(token);
        var account = financialAccountRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(accountId, ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
        if (!allowExistingInactiveAccountReference) {
            FinancialAccountReferenceValidator.validateActiveForNewReference(account);
        }
    }

    private boolean retainsExistingAccountReference(TransactionRuleCondition condition, TransactionRuleCondition existingCondition) {
        return (
            existingCondition != null &&
            existingCondition.getField() == TransactionRuleField.ACCOUNT &&
            condition.getField() == TransactionRuleField.ACCOUNT &&
            java.util.Objects.equals(existingCondition.getValue(), condition.getValue())
        );
    }

    private BigDecimal parseAmount(String value) {
        String trimmed = value.trim();
        if (trimmed.contains(",") || trimmed.contains("$")) {
            throw new IllegalArgumentException("Invalid amount format");
        }
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount value");
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date value");
        }
    }

    private Long parseAccountId(String value) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid account id");
        }
    }

    private void validateDuplicate(TransactionRuleCondition condition, Long excludeId) {
        if (condition.getTransactionRule().getId() == null) {
            return;
        }
        List<TransactionRuleCondition> candidates = transactionRuleConditionRepository.findPotentialDuplicates(
            condition.getTransactionRule().getId(),
            condition.getField(),
            condition.getOperator(),
            condition.getCaseSensitive(),
            excludeId
        );

        String normalizedValue = normalizeValue(condition);
        String normalizedSecondValue = normalizeSecondValue(condition);

        for (TransactionRuleCondition candidate : candidates) {
            if (normalizedValue.equals(normalizeValue(candidate)) && normalizedSecondValue.equals(normalizeSecondValue(candidate))) {
                throw new IllegalArgumentException("Duplicate condition for transaction rule");
            }
        }
    }

    private String normalizedConditionKey(TransactionRuleCondition condition) {
        return (
            condition.getField() +
            "|" +
            condition.getOperator() +
            "|" +
            condition.getCaseSensitive() +
            "|" +
            normalizeValue(condition) +
            "|" +
            normalizeSecondValue(condition)
        );
    }

    private String normalizeValue(TransactionRuleCondition condition) {
        return normalizeStoredValue(condition.getField(), condition.getOperator(), condition.getValue(), condition.getCaseSensitive());
    }

    private String normalizeSecondValue(TransactionRuleCondition condition) {
        if (condition.getOperator() != RuleOperator.BETWEEN || isBlank(condition.getSecondValue())) {
            return "";
        }
        return normalizeStoredValue(condition.getField(), RuleOperator.BETWEEN, condition.getSecondValue(), condition.getCaseSensitive());
    }

    private String normalizeStoredValue(TransactionRuleField field, RuleOperator operator, String rawValue, Boolean caseSensitive) {
        if (operator == RuleOperator.IN || operator == RuleOperator.NOT_IN) {
            return normalizeListValue(field, rawValue, caseSensitive);
        }
        return normalizeSingleValue(field, operator, rawValue, caseSensitive);
    }

    private String normalizeListValue(TransactionRuleField field, String rawValue, Boolean caseSensitive) {
        List<String> tokens = parseListTokens(rawValue);
        Set<String> normalized = new LinkedHashSet<>();
        for (String token : tokens) {
            normalized.add(normalizeSingleValue(field, RuleOperator.EQUALS, token, caseSensitive));
        }
        return normalized.stream().sorted(Comparator.naturalOrder()).collect(Collectors.joining(","));
    }

    private String normalizeSingleValue(TransactionRuleField field, RuleOperator operator, String rawValue, Boolean caseSensitive) {
        String trimmed = rawValue.trim();
        return switch (field) {
            case DESCRIPTION, EXTERNAL_REFERENCE -> normalizeTextValue(trimmed, operator, caseSensitive);
            case FLOW -> TransactionFlow.valueOf(trimmed).name();
            case ORIGIN -> TransactionOrigin.valueOf(trimmed).name();
            case AMOUNT -> parseAmount(trimmed).stripTrailingZeros().toPlainString();
            case TRANSACTION_DATE, POSTING_DATE -> parseDate(trimmed).toString();
            case ACCOUNT -> parseAccountId(trimmed).toString();
        };
    }

    private String normalizeTextValue(String value, RuleOperator operator, Boolean caseSensitive) {
        if (operator == RuleOperator.REGEX || Boolean.TRUE.equals(caseSensitive)) {
            return value;
        }
        return value.toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
