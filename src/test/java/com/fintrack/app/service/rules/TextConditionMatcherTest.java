package com.fintrack.app.service.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.domain.enumeration.DescriptionNormalizationRuleOperator;
import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextConditionMatcherTest {

    private final TextConditionMatcher matcher = new TextConditionMatcher();
    private final ConditionGroupEvaluator groupEvaluator = new ConditionGroupEvaluator();

    @Test
    void matchesAllTextOperators() {
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.EXACT, "Uber Trip", true)).isTrue();
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.NOT_EQUALS, "Lyft", true)).isTrue();
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.CONTAINS, "Trip", true)).isTrue();
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.NOT_CONTAINS, "Lyft", true)).isTrue();
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.STARTS_WITH, "Uber", true)).isTrue();
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.ENDS_WITH, "Trip", true)).isTrue();
        assertThat(matcher.matches("Uber Trip 123", DescriptionNormalizationRuleOperator.REGEX, "\\d+", true)).isTrue();
    }

    @Test
    void handlesCaseSensitivity() {
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.CONTAINS, "uber", false)).isTrue();
        assertThat(matcher.matches("Uber Trip", DescriptionNormalizationRuleOperator.CONTAINS, "uber", true)).isFalse();
    }

    @Test
    void nullOrBlankActualDoesNotMatchAnyOperatorIncludingNegativeOperators() {
        assertThat(matcher.matches(null, DescriptionNormalizationRuleOperator.NOT_EQUALS, "Uber", false)).isFalse();
        assertThat(matcher.matches(" ", DescriptionNormalizationRuleOperator.NOT_CONTAINS, "Uber", false)).isFalse();
    }

    @Test
    void invalidRegexFailsValidationAndDoesNotMatch() {
        assertThatThrownBy(() -> matcher.validatePattern(DescriptionNormalizationRuleOperator.REGEX, "[")).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThat(matcher.matches("Uber", DescriptionNormalizationRuleOperator.REGEX, "[", false)).isFalse();
    }

    @Test
    void groupEvaluatorSupportsAllAnyAndRejectsEmpty() {
        assertThat(groupEvaluator.matches(List.of("a", "b"), RuleConditionLogic.ALL, value -> value.length() == 1)).isTrue();
        assertThat(groupEvaluator.matches(List.of("a", "bb"), RuleConditionLogic.ALL, value -> value.length() == 1)).isFalse();
        assertThat(groupEvaluator.matches(List.of("a", "bb"), RuleConditionLogic.ANY, value -> value.length() == 2)).isTrue();
        assertThat(groupEvaluator.matches(List.of(), RuleConditionLogic.ANY, value -> true)).isFalse();
    }
}
