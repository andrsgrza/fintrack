package com.fintrack.app.service.rules;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionCandidateTag;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
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
        return applyFillEmptyOnly(candidate, evaluation, preserveUserSelectedClassification, true, true, categoryResolver, tagResolver);
    }

    /**
     * Applies only the requested explicit output domains while retaining FILL_EMPTY_ONLY behavior.
     */
    public TransactionCandidateRuleApplicationResult applyFillEmptyOnly(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean preserveUserSelectedClassification,
        boolean applyCategory,
        boolean applyTags,
        Function<Long, Category> categoryResolver,
        Function<Long, Tag> tagResolver
    ) {
        boolean categoryApplied = applyCategory && applySuggestedCategory(candidate, evaluation, categoryResolver);
        List<Long> tagIdsApplied = applyTags ? applySuggestedTags(candidate, evaluation, tagResolver) : List.of();
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

    /**
     * Synchronizes selected rule-output scopes using server-owned provenance. This is deliberately separate from
     * {@link #applyFillEmptyOnly(TransactionCandidate, TransactionRuleEvaluationResult, boolean, Function, Function)}:
     * explicit Apply actions retain their conservative behavior, while configured automation may replace obsolete
     * AUTOMATIC values and, when requested, MANUAL values.
     */
    public TransactionCandidateRuleApplicationResult applyAutomatically(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean applyCategory,
        boolean applyTags,
        boolean protectManualChanges,
        Function<Long, Category> categoryResolver,
        Function<Long, Tag> tagResolver
    ) {
        boolean categoryApplied = applyCategory && synchronizeCategory(candidate, evaluation, protectManualChanges, categoryResolver);
        TagSynchronizationResult tagSynchronization = applyTags
            ? synchronizeTags(candidate, evaluation, protectManualChanges, tagResolver)
            : TagSynchronizationResult.unchanged();
        boolean protectedManualClassification = protectManualChanges && candidate.hasManualClassification();
        boolean hasScopedSuggestions =
            (applyCategory && evaluation.suggestedCategory() != null) || (applyTags && !evaluation.suggestedTags().isEmpty());

        return new TransactionCandidateRuleApplicationResult(
            categoryApplied,
            tagSynchronization.addedTagIds(),
            recommendedAutomaticClassificationReviewStatus(protectedManualClassification, hasScopedSuggestions),
            categoryApplied || tagSynchronization.changed()
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
            candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
            return true;
        }
        return false;
    }

    private boolean synchronizeCategory(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean protectManualChanges,
        Function<Long, Category> categoryResolver
    ) {
        boolean manualCategoryIsProtected =
            protectManualChanges && candidate.getCategorySource() == TransactionCandidateClassificationSource.MANUAL;
        if (manualCategoryIsProtected) {
            return false;
        }

        CategorySuggestion suggestion = evaluation.suggestedCategory();
        if (suggestion == null) {
            if (candidate.getCategory() == null) {
                return false;
            }
            candidate.setCategory(null);
            return true;
        }

        Category suggestedCategory = categoryResolver.apply(suggestion.categoryId());
        boolean categoryChanged =
            candidate.getCategory() == null || !Objects.equals(candidate.getCategory().getId(), suggestedCategory.getId());
        boolean provenanceChanged = candidate.getCategorySource() != TransactionCandidateClassificationSource.AUTOMATIC;
        if (categoryChanged) {
            candidate.setCategory(suggestedCategory);
        }
        candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
        return categoryChanged || provenanceChanged;
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
            candidate.addTag(tag, TransactionCandidateClassificationSource.AUTOMATIC);
            tagIds.add(tag.getId());
            tagIdsApplied.add(tag.getId());
        }
        return tagIdsApplied;
    }

    private TagSynchronizationResult synchronizeTags(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean protectManualChanges,
        Function<Long, Tag> tagResolver
    ) {
        Set<Long> suggestedTagIds = evaluation
            .suggestedTags()
            .stream()
            .map(TagSuggestion::tagId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!protectManualChanges) {
            Set<Tag> suggestedTags = suggestedTagIds.stream().map(tagResolver).collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Long> existingIds = currentTagIds(candidate);
            boolean provenanceChanged = candidate
                .getTagAssociations()
                .stream()
                .anyMatch(association -> association.getSource() != TransactionCandidateClassificationSource.AUTOMATIC);
            boolean valuesChanged = !existingIds.equals(suggestedTagIds);
            candidate.replaceTags(suggestedTags, TransactionCandidateClassificationSource.AUTOMATIC);
            List<Long> addedTagIds = suggestedTagIds.stream().filter(tagId -> !existingIds.contains(tagId)).toList();
            return new TagSynchronizationResult(addedTagIds, valuesChanged || provenanceChanged);
        }

        List<Tag> obsoleteAutomaticTags = candidate
            .getTagAssociations()
            .stream()
            .filter(association -> association.getSource() == TransactionCandidateClassificationSource.AUTOMATIC)
            .map(TransactionCandidateTag::getTag)
            .filter(tag -> tag != null && !suggestedTagIds.contains(tag.getId()))
            .toList();
        obsoleteAutomaticTags.forEach(candidate::removeTags);

        Set<Long> existingIds = currentTagIds(candidate);
        List<Long> addedTagIds = new ArrayList<>();
        for (Long suggestedTagId : suggestedTagIds) {
            Tag suggestedTag = tagResolver.apply(suggestedTagId);
            if (!existingIds.contains(suggestedTagId)) {
                candidate.addTag(suggestedTag, TransactionCandidateClassificationSource.AUTOMATIC);
                existingIds.add(suggestedTagId);
                addedTagIds.add(suggestedTagId);
            }
        }
        return new TagSynchronizationResult(addedTagIds, !obsoleteAutomaticTags.isEmpty() || !addedTagIds.isEmpty());
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

    private TransactionCandidateClassificationReviewStatus recommendedAutomaticClassificationReviewStatus(
        boolean hasProtectedManualClassification,
        boolean hasScopedSuggestions
    ) {
        if (hasProtectedManualClassification) {
            return TransactionCandidateClassificationReviewStatus.USER_SELECTED;
        }
        return hasScopedSuggestions
            ? TransactionCandidateClassificationReviewStatus.SUGGESTED
            : TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE;
    }

    private Set<Long> currentTagIds(TransactionCandidate candidate) {
        if (candidate.getTags() == null || candidate.getTags().isEmpty()) {
            return new HashSet<>();
        }
        return candidate.getTags().stream().map(Tag::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private record TagSynchronizationResult(List<Long> addedTagIds, boolean changed) {
        private static TagSynchronizationResult unchanged() {
            return new TagSynchronizationResult(List.of(), false);
        }
    }
}
