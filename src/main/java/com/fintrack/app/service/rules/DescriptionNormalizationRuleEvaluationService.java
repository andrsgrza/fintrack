package com.fintrack.app.service.rules;

import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.repository.DescriptionNormalizationRuleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DescriptionNormalizationRuleEvaluationService {

    private final Function<String, List<DescriptionNormalizationRule>> activeRuleLoader;
    private final TextConditionMatcher textConditionMatcher;
    private final ConditionGroupEvaluator conditionGroupEvaluator;

    @Autowired
    public DescriptionNormalizationRuleEvaluationService(
        DescriptionNormalizationRuleRepository descriptionNormalizationRuleRepository,
        TextConditionMatcher textConditionMatcher,
        ConditionGroupEvaluator conditionGroupEvaluator
    ) {
        this(
            login -> descriptionNormalizationRuleRepository.findActiveRulesForEvaluationByUserLoginOrderByPriorityAscIdAsc(login),
            textConditionMatcher,
            conditionGroupEvaluator
        );
    }

    DescriptionNormalizationRuleEvaluationService(
        Function<String, List<DescriptionNormalizationRule>> activeRuleLoader,
        TextConditionMatcher textConditionMatcher,
        ConditionGroupEvaluator conditionGroupEvaluator
    ) {
        this.activeRuleLoader = activeRuleLoader;
        this.textConditionMatcher = textConditionMatcher;
        this.conditionGroupEvaluator = conditionGroupEvaluator;
    }

    public DescriptionNormalizationRuleEvaluationResult evaluate(String userLogin, String originalDescription) {
        if (userLogin == null || userLogin.isBlank()) {
            throw new IllegalArgumentException("User login is required");
        }
        List<DescriptionNormalizationRule> rules = activeRuleLoader
            .apply(userLogin)
            .stream()
            .sorted(Comparator.comparing(DescriptionNormalizationRule::getPriority).thenComparing(DescriptionNormalizationRule::getId))
            .toList();
        for (DescriptionNormalizationRule rule : rules) {
            List<DescriptionNormalizationRuleCondition> conditions = rule
                .getConditions()
                .stream()
                .sorted(
                    Comparator.comparing(DescriptionNormalizationRuleCondition::getPosition).thenComparing(
                        DescriptionNormalizationRuleCondition::getId
                    )
                )
                .toList();
            boolean matched = conditionGroupEvaluator.matches(conditions, rule.getConditionOperator(), condition ->
                textConditionMatcher.matches(
                    originalDescription,
                    condition.getOperator(),
                    condition.getValue(),
                    Boolean.TRUE.equals(condition.getCaseSensitive())
                )
            );
            if (matched) {
                return new DescriptionNormalizationRuleEvaluationResult(true, rule.getId(), rule.getName(), rule.getResultingDescription());
            }
        }
        return DescriptionNormalizationRuleEvaluationResult.noMatch();
    }
}
