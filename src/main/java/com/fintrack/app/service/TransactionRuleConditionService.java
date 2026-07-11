package com.fintrack.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.TransactionRule;
import com.fintrack.app.domain.TransactionRuleCondition;
import com.fintrack.app.domain.enumeration.RuleOperator;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.domain.enumeration.TransactionRuleField;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TransactionRuleConditionRepository;
import com.fintrack.app.repository.TransactionRuleRepository;
import com.fintrack.app.service.dto.TransactionRuleConditionDTO;
import com.fintrack.app.service.dto.TransactionRuleDTO;
import com.fintrack.app.service.mapper.TransactionRuleConditionMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fintrack.app.domain.TransactionRuleCondition}.
 */
@Service
@Transactional
public class TransactionRuleConditionService {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRuleConditionService.class);

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

    private final TransactionRuleConditionMapper transactionRuleConditionMapper;

    private final CurrentUserService currentUserService;

    private final TransactionRuleRepository transactionRuleRepository;

    private final FinancialAccountRepository financialAccountRepository;

    public TransactionRuleConditionService(
        TransactionRuleConditionRepository transactionRuleConditionRepository,
        TransactionRuleConditionMapper transactionRuleConditionMapper,
        CurrentUserService currentUserService,
        TransactionRuleRepository transactionRuleRepository,
        FinancialAccountRepository financialAccountRepository
    ) {
        this.transactionRuleConditionRepository = transactionRuleConditionRepository;
        this.transactionRuleConditionMapper = transactionRuleConditionMapper;
        this.currentUserService = currentUserService;
        this.transactionRuleRepository = transactionRuleRepository;
        this.financialAccountRepository = financialAccountRepository;
    }

    public TransactionRuleConditionDTO save(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        LOG.debug("Request to save TransactionRuleCondition : {}", transactionRuleConditionDTO);
        TransactionRuleCondition transactionRuleCondition = transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO);
        transactionRuleCondition.setTransactionRule(resolveTransactionRuleForCreate(transactionRuleConditionDTO.getTransactionRule()));
        validateCondition(transactionRuleCondition, null);
        transactionRuleCondition = transactionRuleConditionRepository.save(transactionRuleCondition);
        return transactionRuleConditionMapper.toDto(transactionRuleCondition);
    }

    public TransactionRuleConditionDTO update(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        LOG.debug("Request to update TransactionRuleCondition : {}", transactionRuleConditionDTO);
        TransactionRuleCondition existingTransactionRuleCondition = findAccessibleEntity(transactionRuleConditionDTO.getId()).orElseThrow();
        TransactionRuleCondition transactionRuleCondition = transactionRuleConditionMapper.toEntity(transactionRuleConditionDTO);
        enforceImmutableTransactionRule(existingTransactionRuleCondition, transactionRuleConditionDTO.getTransactionRule(), true);
        transactionRuleCondition.setTransactionRule(existingTransactionRuleCondition.getTransactionRule());
        validateCondition(transactionRuleCondition, existingTransactionRuleCondition.getId());
        transactionRuleCondition = transactionRuleConditionRepository.save(transactionRuleCondition);
        return transactionRuleConditionMapper.toDto(transactionRuleCondition);
    }

    public Optional<TransactionRuleConditionDTO> partialUpdate(TransactionRuleConditionDTO transactionRuleConditionDTO) {
        return partialUpdate(transactionRuleConditionDTO, null);
    }

    public Optional<TransactionRuleConditionDTO> partialUpdate(
        TransactionRuleConditionDTO transactionRuleConditionDTO,
        JsonNode patchNode
    ) {
        LOG.debug("Request to partially update TransactionRuleCondition : {}", transactionRuleConditionDTO);

        return findAccessibleEntity(transactionRuleConditionDTO.getId())
            .map(existingTransactionRuleCondition -> {
                if (patchNode != null && patchNode.has("transactionRule") && patchNode.get("transactionRule").isNull()) {
                    throw new IllegalArgumentException("Transaction rule cannot be null");
                }
                rejectNullRequiredPatchFields(patchNode);
                transactionRuleConditionMapper.partialUpdate(existingTransactionRuleCondition, transactionRuleConditionDTO);
                applyNullableFieldsForPartialUpdate(existingTransactionRuleCondition, patchNode);
                applyTransactionRuleForPartialUpdate(existingTransactionRuleCondition, transactionRuleConditionDTO, patchNode);
                validateCondition(existingTransactionRuleCondition, existingTransactionRuleCondition.getId());
                return existingTransactionRuleCondition;
            })
            .map(transactionRuleConditionRepository::save)
            .map(transactionRuleConditionMapper::toDto);
    }

    private void rejectNullRequiredPatchFields(JsonNode patchNode) {
        if (patchNode == null) {
            return;
        }
        rejectNullPatchField(patchNode, "field");
        rejectNullPatchField(patchNode, "operator");
        rejectNullPatchField(patchNode, "value");
        rejectNullPatchField(patchNode, "caseSensitive");
        rejectNullPatchField(patchNode, "position");
    }

    private void rejectNullPatchField(JsonNode patchNode, String fieldName) {
        if (patchNode.has(fieldName) && patchNode.get(fieldName).isNull()) {
            throw new IllegalArgumentException(fieldName + " cannot be null");
        }
    }

    private void applyNullableFieldsForPartialUpdate(TransactionRuleCondition condition, JsonNode patchNode) {
        if (patchNode != null && patchNode.has("secondValue") && patchNode.get("secondValue").isNull()) {
            condition.setSecondValue(null);
        }
    }

    @Transactional(readOnly = true)
    public List<TransactionRuleConditionDTO> findAll() {
        LOG.debug("Request to get all TransactionRuleConditions");
        if (currentUserService.isAdmin()) {
            return transactionRuleConditionRepository
                .findAllWithEagerRelationships()
                .stream()
                .map(transactionRuleConditionMapper::toDto)
                .collect(Collectors.toCollection(LinkedList::new));
        }
        return transactionRuleConditionRepository
            .findAllWithEagerRelationshipsByRuleUserLogin(currentUserService.getCurrentUserLogin())
            .stream()
            .map(transactionRuleConditionMapper::toDto)
            .collect(Collectors.toCollection(LinkedList::new));
    }

    public Page<TransactionRuleConditionDTO> findAllWithEagerRelationships(Pageable pageable) {
        if (currentUserService.isAdmin()) {
            return transactionRuleConditionRepository.findAllWithEagerRelationships(pageable).map(transactionRuleConditionMapper::toDto);
        }
        throw new UnsupportedOperationException("Paged access is only supported for admin users");
    }

    @Transactional(readOnly = true)
    public Optional<TransactionRuleConditionDTO> findOne(Long id) {
        LOG.debug("Request to get TransactionRuleCondition : {}", id);
        return findAccessibleEntity(id).map(transactionRuleConditionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public boolean isAccessible(Long id) {
        return findAccessibleEntity(id).isPresent();
    }

    public boolean delete(Long id) {
        LOG.debug("Request to delete TransactionRuleCondition : {}", id);
        Optional<TransactionRuleCondition> transactionRuleCondition = findAccessibleEntity(id);
        if (transactionRuleCondition.isEmpty()) {
            return false;
        }
        TransactionRuleCondition condition = transactionRuleCondition.orElseThrow();
        TransactionRule parentRule = condition.getTransactionRule();
        Long parentRuleId = parentRule.getId();
        long conditionCount = transactionRuleConditionRepository.countByTransactionRuleId(parentRuleId);

        transactionRuleConditionRepository.deleteById(id);

        if (conditionCount == 1) {
            parentRule.setActive(false);
            parentRule.setUpdatedAt(Instant.now());
            transactionRuleRepository.save(parentRule);
        }
        return true;
    }

    private Optional<TransactionRuleCondition> findAccessibleEntity(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleConditionRepository.findOneWithEagerRelationships(id);
        }
        return transactionRuleConditionRepository.findOneWithEagerRelationshipsByIdAndRuleUserLogin(
            id,
            currentUserService.getCurrentUserLogin()
        );
    }

    private void applyTransactionRuleForPartialUpdate(
        TransactionRuleCondition transactionRuleCondition,
        TransactionRuleConditionDTO transactionRuleConditionDTO,
        JsonNode patchNode
    ) {
        if (patchNode != null) {
            if (patchNode.has("transactionRule")) {
                enforceImmutableTransactionRule(transactionRuleCondition, transactionRuleConditionDTO.getTransactionRule(), false);
            }
            return;
        }
        if (transactionRuleConditionDTO.getTransactionRule() != null) {
            enforceImmutableTransactionRule(transactionRuleCondition, transactionRuleConditionDTO.getTransactionRule(), true);
        }
    }

    private TransactionRule resolveTransactionRuleForCreate(TransactionRuleDTO transactionRuleDTO) {
        if (transactionRuleDTO == null || transactionRuleDTO.getId() == null) {
            throw new IllegalArgumentException("Transaction rule is required");
        }
        return findAccessibleRule(transactionRuleDTO.getId()).orElseThrow(() ->
            new IllegalArgumentException("Transaction rule is not accessible")
        );
    }

    private void enforceImmutableTransactionRule(
        TransactionRuleCondition existingCondition,
        TransactionRuleDTO requestedRuleDTO,
        boolean requiredOnPut
    ) {
        TransactionRule existingRule = existingCondition.getTransactionRule();
        if (requestedRuleDTO == null || requestedRuleDTO.getId() == null) {
            if (requiredOnPut) {
                throw new IllegalArgumentException("Transaction rule is required");
            }
            return;
        }
        if (!requestedRuleDTO.getId().equals(existingRule.getId())) {
            throw new IllegalArgumentException("Transaction rule cannot be changed");
        }
    }

    private Optional<TransactionRule> findAccessibleRule(Long id) {
        if (currentUserService.isAdmin()) {
            return transactionRuleRepository.findOneWithToOneRelationships(id);
        }
        return transactionRuleRepository.findOneWithEagerRelationshipsByIdAndUserLogin(id, currentUserService.getCurrentUserLogin());
    }

    private void validateCondition(TransactionRuleCondition condition, Long excludeId) {
        validatePosition(condition.getPosition());
        validateValuePresent(condition.getValue());
        validateFieldOperatorCompatibility(condition.getField(), condition.getOperator());
        validateSecondValueRules(condition);
        validateValueSemantics(condition);
        validateDuplicate(condition, excludeId);
    }

    private void validatePosition(Integer position) {
        if (position == null || position < 0) {
            throw new IllegalArgumentException("Position must be greater than or equal to zero");
        }
    }

    private void validateValuePresent(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Value is required");
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

    private void validateValueSemantics(TransactionRuleCondition condition) {
        TransactionRuleField field = condition.getField();
        RuleOperator operator = condition.getOperator();
        String ownerLogin = condition.getTransactionRule().getUser().getLogin();

        validateFieldValue(field, operator, condition.getValue(), condition.getCaseSensitive(), ownerLogin);

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
        String ownerLogin
    ) {
        if (operator == RuleOperator.IN || operator == RuleOperator.NOT_IN) {
            List<String> tokens = parseListTokens(rawValue);
            for (String token : tokens) {
                validateSingleToken(field, operator, token, caseSensitive, ownerLogin);
            }
            return;
        }
        validateSingleToken(field, operator, rawValue.trim(), caseSensitive, ownerLogin);
    }

    private void validateSingleToken(
        TransactionRuleField field,
        RuleOperator operator,
        String token,
        Boolean caseSensitive,
        String ownerLogin
    ) {
        switch (field) {
            case DESCRIPTION, EXTERNAL_REFERENCE -> validateTextToken(operator, token);
            case FLOW -> validateFlowToken(token);
            case ORIGIN -> validateOriginToken(token);
            case AMOUNT -> parseAmount(token);
            case TRANSACTION_DATE, POSTING_DATE -> parseDate(token);
            case ACCOUNT -> validateAccountId(token, ownerLogin);
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

    private void validateAccountId(String token, String ownerLogin) {
        Long accountId = parseAccountId(token);
        financialAccountRepository
            .findOneWithToOneRelationshipsByIdAndUserLogin(accountId, ownerLogin)
            .orElseThrow(() -> new IllegalArgumentException("Financial account is not accessible"));
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

    private List<String> parseListTokens(String rawValue) {
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

    private void validateDuplicate(TransactionRuleCondition condition, Long excludeId) {
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
