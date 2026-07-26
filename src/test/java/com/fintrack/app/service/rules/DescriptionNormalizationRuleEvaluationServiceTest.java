package com.fintrack.app.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.DescriptionNormalizationRule;
import com.fintrack.app.domain.DescriptionNormalizationRuleCondition;
import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescriptionNormalizationRuleEvaluationServiceTest {

    private List<DescriptionNormalizationRule> rules = List.of();

    @Test
    void firstMatchingRuleWinsByPriorityAndId() {
        DescriptionNormalizationRule later = rule(2L, "Later", 1, "Later Result", condition(2L, 0, "Uber"));
        DescriptionNormalizationRule first = rule(1L, "First", 0, "Uber", condition(1L, 0, "Uber"));
        rules = List.of(later, first);

        DescriptionNormalizationRuleEvaluationResult result = service().evaluate("user", "Uber trip");

        assertThat(result.matched()).isTrue();
        assertThat(result.ruleId()).isEqualTo(1L);
        assertThat(result.ruleName()).isEqualTo("First");
        assertThat(result.resultingDescription()).isEqualTo("Uber");
    }

    @Test
    void inactiveOrRulesWithoutConditionsAreIgnoredByRepositoryAndDefensiveEvaluation() {
        DescriptionNormalizationRule empty = rule(1L, "Empty", 0, "Uber");
        rules = List.of(empty);

        DescriptionNormalizationRuleEvaluationResult result = service().evaluate("user", "Uber trip");

        assertThat(result.matched()).isFalse();
    }

    @Test
    void allAndAnyConditionLogicWorks() {
        DescriptionNormalizationRule allRule = rule(1L, "All", 0, "Uber", condition(1L, 0, "Uber"), condition(2L, 1, "Trip"));
        allRule.setConditionOperator(RuleConditionLogic.ALL);
        rules = List.of(allRule);
        assertThat(service().evaluate("user", "Uber Trip").matched()).isTrue();
        assertThat(service().evaluate("user", "Uber").matched()).isFalse();

        allRule.setConditionOperator(RuleConditionLogic.ANY);
        assertThat(service().evaluate("user", "Uber").matched()).isTrue();
    }

    private DescriptionNormalizationRuleEvaluationService service() {
        return new DescriptionNormalizationRuleEvaluationService(login -> rules, new TextConditionMatcher(), new ConditionGroupEvaluator());
    }

    private DescriptionNormalizationRule rule(
        Long id,
        String name,
        Integer priority,
        String resultingDescription,
        DescriptionNormalizationRuleCondition... conditions
    ) {
        DescriptionNormalizationRule rule = new DescriptionNormalizationRule();
        rule.setId(id);
        rule.setName(name);
        rule.setPriority(priority);
        rule.setActive(true);
        rule.setConditionOperator(RuleConditionLogic.ANY);
        rule.setResultingDescription(resultingDescription);
        rule.setConditions(new HashSet<>(List.of(conditions)));
        return rule;
    }

    private DescriptionNormalizationRuleCondition condition(Long id, Integer position, String value) {
        DescriptionNormalizationRuleCondition condition = new DescriptionNormalizationRuleCondition();
        condition.setId(id);
        condition.setPosition(position);
        condition.setOperator(DescriptionNormalizationRuleOperator.CONTAINS);
        condition.setValue(value);
        condition.setCaseSensitive(false);
        return condition;
    }
}
