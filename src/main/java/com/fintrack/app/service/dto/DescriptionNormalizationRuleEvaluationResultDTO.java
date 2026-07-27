package com.fintrack.app.service.dto;

public record DescriptionNormalizationRuleEvaluationResultDTO(boolean matched, Long ruleId, String ruleName, String resultingDescription) {
    public static DescriptionNormalizationRuleEvaluationResultDTO noMatch() {
        return new DescriptionNormalizationRuleEvaluationResultDTO(false, null, null, null);
    }
}
