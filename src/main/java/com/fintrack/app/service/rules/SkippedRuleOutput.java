package com.fintrack.app.service.rules;

public record SkippedRuleOutput(
    RuleOutputField field,
    Long sourceRuleId,
    String sourceRuleName,
    RuleOutputSkipReason reason,
    Long valueId,
    String valueLabel
) {}
