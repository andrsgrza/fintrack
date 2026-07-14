package com.fintrack.app.service.rules;

public record TagSuggestion(
    Long tagId,
    String tagName,
    Long sourceRuleId,
    String sourceRuleName,
    boolean alreadyPresent,
    boolean duplicateOfEarlierSuggestion
) {}
