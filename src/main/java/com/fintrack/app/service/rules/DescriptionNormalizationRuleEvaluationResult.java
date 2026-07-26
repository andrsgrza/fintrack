package com.fintrack.app.service.rules;

public record DescriptionNormalizationRuleEvaluationResult(boolean matched, Long ruleId, String ruleName, String resultingDescription) {
    public static DescriptionNormalizationRuleEvaluationResult noMatch() {
        return new DescriptionNormalizationRuleEvaluationResult(false, null, null, null);
    }
}
