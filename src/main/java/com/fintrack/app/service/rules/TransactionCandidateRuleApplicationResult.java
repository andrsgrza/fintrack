package com.fintrack.app.service.rules;

import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import java.util.List;

public record TransactionCandidateRuleApplicationResult(
    boolean categoryApplied,
    List<Long> tagIdsApplied,
    TransactionCandidateClassificationReviewStatus recommendedClassificationReviewStatus,
    boolean hasAppliedChanges
) {
    public TransactionCandidateRuleApplicationResult {
        tagIdsApplied = tagIdsApplied == null ? List.of() : List.copyOf(tagIdsApplied);
    }
}
