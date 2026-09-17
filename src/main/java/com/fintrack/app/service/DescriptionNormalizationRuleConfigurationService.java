package com.fintrack.app.service;

import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import com.fintrack.app.repository.DescriptionNormalizationRuleConditionRepository;
import com.fintrack.app.repository.DescriptionNormalizationRuleRepository;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConfiguredConditionDTO;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConfiguredRequestDTO;
import com.fintrack.app.service.dto.DescriptionNormalizationRuleConfiguredResponseDTO;
import com.fintrack.app.service.rules.TextConditionMatcher;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a DescriptionNormalizationRule and its condition set as one transaction.
 *
 * The generated parent and condition write routes remain available for technical maintenance. Product
 * composition uses this command so an active rule can never be persisted without its required conditions.
 */
@Service
@Transactional
public class DescriptionNormalizationRuleConfigurationService {

    private final DescriptionNormalizationRuleRepository ruleRepository;
    private final DescriptionNormalizationRuleConditionRepository conditionRepository;
    private final CurrentUserService currentUserService;
    private final TextConditionMatcher textConditionMatcher;

    public DescriptionNormalizationRuleConfigurationService(
        DescriptionNormalizationRuleRepository ruleRepository,
        DescriptionNormalizationRuleConditionRepository conditionRepository,
        CurrentUserService currentUserService,
        TextConditionMatcher textConditionMatcher
    ) {
        this.ruleRepository = ruleRepository;
        this.conditionRepository = conditionRepository;
        this.currentUserService = currentUserService;
        this.textConditionMatcher = textConditionMatcher;
    }

    public DescriptionNormalizationRuleConfiguredResponseDTO create(DescriptionNormalizationRuleConfiguredRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("Description normalization rule payload is required");
        }

        DescriptionNormalizationRule rule = new DescriptionNormalizationRule();
        rule.setUser(currentUserService.getCurrentUser());
        rule.setPriority(nextPriorityForUser(rule.getUser().getId()));
        Instant now = Instant.now();
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        applyRequest(rule, request);

        List<DescriptionNormalizationRuleCondition> conditions = toConditions(request, rule, now);
        validate(rule, conditions);

        DescriptionNormalizationRule savedRule = ruleRepository.save(rule);
        conditions.forEach(condition -> condition.setDescriptionNormalizationRule(savedRule));
        conditionRepository.saveAll(conditions);
        return toResponse(savedRule, conditions);
    }

    private void applyRequest(DescriptionNormalizationRule rule, DescriptionNormalizationRuleConfiguredRequestDTO request) {
        rule.setName(trimToNull(request.getName()));
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setActive(request.getActive());
        rule.setConditionOperator(request.getConditionOperator());
        rule.setResultingDescription(trimToNull(request.getResultingDescription()));
    }

    private List<DescriptionNormalizationRuleCondition> toConditions(
        DescriptionNormalizationRuleConfiguredRequestDTO request,
        DescriptionNormalizationRule rule,
        Instant now
    ) {
        if (request.getConditions() == null) {
            return List.of();
        }
        List<DescriptionNormalizationRuleCondition> conditions = new ArrayList<>();
        for (int index = 0; index < request.getConditions().size(); index++) {
            DescriptionNormalizationRuleConfiguredConditionDTO source = request.getConditions().get(index);
            if (source == null) {
                throw new IllegalArgumentException("Condition is required");
            }
            DescriptionNormalizationRuleCondition condition = new DescriptionNormalizationRuleCondition();
            condition.setDescriptionNormalizationRule(rule);
            condition.setOperator(source.getOperator());
            condition.setValue(trimToNull(source.getValue()));
            condition.setCaseSensitive(source.getCaseSensitive());
            condition.setPosition(index);
            condition.setCreatedAt(now);
            condition.setUpdatedAt(now);
            conditions.add(condition);
        }
        return conditions;
    }

    private void validate(DescriptionNormalizationRule rule, List<DescriptionNormalizationRuleCondition> conditions) {
        if (rule.getName() == null) {
            throw new IllegalArgumentException("Name is required");
        }
        if (rule.getName().length() > 100) {
            throw new IllegalArgumentException("Name must be at most 100 characters");
        }
        if (rule.getDescription() != null && rule.getDescription().length() > 500) {
            throw new IllegalArgumentException("Description must be at most 500 characters");
        }
        if (rule.getResultingDescription() == null) {
            throw new IllegalArgumentException("Resulting description is required");
        }
        if (rule.getResultingDescription().length() > 500) {
            throw new IllegalArgumentException("Resulting description must be at most 500 characters");
        }
        if (rule.getConditionOperator() == null) {
            throw new IllegalArgumentException("Condition operator is required");
        }
        if (rule.getActive() == null) {
            throw new IllegalArgumentException("Active is required");
        }
        if (ruleRepository.existsByUserLoginAndNormalizedName(rule.getUser().getLogin(), rule.getName().toLowerCase(), null)) {
            throw new IllegalArgumentException("Description normalization rule name already exists");
        }
        if (Boolean.TRUE.equals(rule.getActive()) && conditions.isEmpty()) {
            throw new IllegalArgumentException("Active description normalization rule must have at least one condition");
        }

        Set<String> duplicateKeys = new HashSet<>();
        for (DescriptionNormalizationRuleCondition condition : conditions) {
            validateCondition(condition);
            String duplicateKey = condition.getOperator() + ":" + condition.getCaseSensitive() + ":" + condition.getValue().toLowerCase();
            if (!duplicateKeys.add(duplicateKey)) {
                throw new IllegalArgumentException("Description normalization rule condition already exists");
            }
        }
    }

    private void validateCondition(DescriptionNormalizationRuleCondition condition) {
        DescriptionNormalizationRuleOperator operator = condition.getOperator();
        if (operator == null) {
            throw new IllegalArgumentException("Operator is required");
        }
        if (condition.getValue() == null) {
            throw new IllegalArgumentException("Value is required");
        }
        if (condition.getValue().length() > 1000) {
            throw new IllegalArgumentException("Value must be at most 1000 characters");
        }
        if (condition.getCaseSensitive() == null) {
            throw new IllegalArgumentException("Case sensitive is required");
        }
        textConditionMatcher.validatePattern(operator, condition.getValue());
    }

    private Integer nextPriorityForUser(Long userId) {
        Integer maxPriority = ruleRepository.findMaxPriorityByUserId(userId);
        return maxPriority == null ? 0 : maxPriority + 1;
    }

    private DescriptionNormalizationRuleConfiguredResponseDTO toResponse(
        DescriptionNormalizationRule rule,
        List<DescriptionNormalizationRuleCondition> conditions
    ) {
        DescriptionNormalizationRuleConfiguredResponseDTO response = new DescriptionNormalizationRuleConfiguredResponseDTO();
        response.setId(rule.getId());
        response.setName(rule.getName());
        response.setDescription(rule.getDescription());
        response.setActive(rule.getActive());
        response.setPriority(rule.getPriority());
        response.setConditionOperator(rule.getConditionOperator());
        response.setResultingDescription(rule.getResultingDescription());
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());
        response.setConditions(conditions.stream().map(condition -> toConditionResponse(condition)).toList());
        return response;
    }

    private DescriptionNormalizationRuleConfiguredConditionDTO toConditionResponse(DescriptionNormalizationRuleCondition condition) {
        DescriptionNormalizationRuleConfiguredConditionDTO response = new DescriptionNormalizationRuleConfiguredConditionDTO();
        response.setOperator(condition.getOperator());
        response.setValue(condition.getValue());
        response.setCaseSensitive(condition.getCaseSensitive());
        response.setPosition(condition.getPosition());
        return response;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
