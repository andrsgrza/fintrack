package com.fintrack.app.service.rules;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Applies TransactionRule output suggestions to TransactionCandidate drafts with FILL_EMPTY_ONLY semantics.
 *
 * This helper mutates the supplied candidate in memory. Callers remain responsible for authorization,
 * source/status validation, rule evaluation, persistence, and response mapping.
 */
@Service
public class TransactionCandidateRuleApplicationService {

    public TransactionCandidateRuleApplicationResult applyFillEmptyOnly(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean preserveUserSelectedClassification,
        Function<Long, Category> categoryResolver,
        Function<Long, Tag> tagResolver
    ) {
        boolean categoryApplied = applySuggestedCategory(candidate, evaluation, categoryResolver);
        List<Long> tagIdsApplied = applySuggestedTags(candidate, evaluation, tagResolver);
        TransactionCandidateClassificationReviewStatus recommendedClassificationReviewStatus = recommendedClassificationReviewStatus(
            preserveUserSelectedClassification,
            categoryApplied,
            tagIdsApplied,
            evaluation
        );
        return new TransactionCandidateRuleApplicationResult(
            categoryApplied,
            tagIdsApplied,
            recommendedClassificationReviewStatus,
            categoryApplied || !tagIdsApplied.isEmpty()
        );
    }

    private boolean applySuggestedCategory(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        Function<Long, Category> categoryResolver
    ) {
        if (
            candidate.getCategory() == null &&
            evaluation.suggestedCategory() != null &&
            !evaluation.suggestedCategory().conflictsWithCurrentValue()
        ) {
            candidate.setCategory(categoryResolver.apply(evaluation.suggestedCategory().categoryId()));
            return true;
        }
        return false;
    }

    private List<Long> applySuggestedTags(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        Function<Long, Tag> tagResolver
    ) {
        List<Long> tagIdsApplied = new ArrayList<>();
        Set<Long> tagIds = currentTagIds(candidate);
        for (TagSuggestion suggestedTag : evaluation.suggestedTags()) {
            if (suggestedTag.alreadyPresent() || suggestedTag.duplicateOfEarlierSuggestion() || tagIds.contains(suggestedTag.tagId())) {
                continue;
            }
            Tag tag = tagResolver.apply(suggestedTag.tagId());
            addTag(candidate, tag);
            tagIds.add(tag.getId());
            tagIdsApplied.add(tag.getId());
        }
        return tagIdsApplied;
    }

    private TransactionCandidateClassificationReviewStatus recommendedClassificationReviewStatus(
        boolean preserveUserSelectedClassification,
        boolean categoryApplied,
        List<Long> tagIdsApplied,
        TransactionRuleEvaluationResult evaluation
    ) {
        if (preserveUserSelectedClassification) {
            return TransactionCandidateClassificationReviewStatus.USER_SELECTED;
        }
        if (categoryApplied || !tagIdsApplied.isEmpty() || evaluation.hasSuggestions()) {
            return TransactionCandidateClassificationReviewStatus.SUGGESTED;
        }
        return TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE;
    }

    private Set<Long> currentTagIds(TransactionCandidate candidate) {
        if (candidate.getTags() == null || candidate.getTags().isEmpty()) {
            return new HashSet<>();
        }
        return candidate.getTags().stream().map(Tag::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private void addTag(TransactionCandidate candidate, Tag tag) {
        Set<Tag> tags = candidate.getTags();
        if (tags == null) {
            tags = new LinkedHashSet<>();
            candidate.setTags(tags);
        }
        try {
            tags.add(tag);
        } catch (UnsupportedOperationException e) {
            tags = new LinkedHashSet<>(tags);
            candidate.setTags(tags);
            tags.add(tag);
        }
    }
}
