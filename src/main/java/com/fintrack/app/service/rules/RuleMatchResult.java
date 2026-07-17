package com.fintrack.app.service.rules;

import com.fintrack.app.domain.enumeration.RuleConditionLogic;
import java.util.Set;

public record RuleMatchResult(
    Long ruleId,
    String ruleName,
    Integer priority,
    RuleConditionLogic conditionLogic,
    Set<RuleOutputField> proposedOutputs
) {
    public RuleMatchResult {
        proposedOutputs = proposedOutputs == null ? Set.of() : Set.copyOf(proposedOutputs);
    }
}
