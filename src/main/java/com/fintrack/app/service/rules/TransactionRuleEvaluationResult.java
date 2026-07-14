package com.fintrack.app.service.rules;

import java.util.List;

public record TransactionRuleEvaluationResult(
    List<RuleMatchResult> matchedRules,
    CategorySuggestion suggestedCategory,
    List<TagSuggestion> suggestedTags,
    List<RuleOutputConflict> conflicts,
    List<SkippedRuleOutput> skippedOutputs
) {
    public TransactionRuleEvaluationResult {
        matchedRules = matchedRules == null ? List.of() : List.copyOf(matchedRules);
        suggestedTags = suggestedTags == null ? List.of() : List.copyOf(suggestedTags);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        skippedOutputs = skippedOutputs == null ? List.of() : List.copyOf(skippedOutputs);
    }

    public boolean hasSuggestions() {
        return (
            suggestedCategory != null ||
            suggestedTags.stream().anyMatch(tag -> !tag.alreadyPresent() && !tag.duplicateOfEarlierSuggestion())
        );
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
