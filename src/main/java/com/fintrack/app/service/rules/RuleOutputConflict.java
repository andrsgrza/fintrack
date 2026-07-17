package com.fintrack.app.service.rules;

public record RuleOutputConflict(
    RuleOutputField field,
    Long currentValueId,
    String currentValueLabel,
    Long suggestedValueId,
    String suggestedValueLabel,
    Long sourceRuleId,
    String sourceRuleName,
    RuleOutputConflictReason reason
) {}
