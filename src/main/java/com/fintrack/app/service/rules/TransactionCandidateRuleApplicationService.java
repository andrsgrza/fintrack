package com.fintrack.app.service.rules;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.TransactionCandidateTag;
import com.fintrack.app.domain.TransactionRule;
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
        return applyFillEmptyOnly(
            candidate,
            evaluation,
            preserveUserSelectedClassification,
            true,
            true,
            categoryResolver,
            tagResolver,
            ignored -> null
        );
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
        return applyFillEmptyOnly(
            candidate,
            evaluation,
            preserveUserSelectedClassification,
            applyCategory,
            applyTags,
            categoryResolver,
            tagResolver,
            ignored -> null
        );
    }

    public TransactionCandidateRuleApplicationResult applyFillEmptyOnly(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean preserveUserSelectedClassification,
        boolean applyCategory,
        boolean applyTags,
        Function<Long, Category> categoryResolver,
        Function<Long, Tag> tagResolver,
        Function<Long, TransactionRule> ruleResolver
    ) {
        boolean categoryApplied = applyCategory && applySuggestedCategory(candidate, evaluation, categoryResolver, ruleResolver);
        List<Long> tagIdsApplied = applyTags ? applySuggestedTags(candidate, evaluation, tagResolver, ruleResolver) : List.of();
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
        return applyAutomatically(
            candidate,
            evaluation,
            applyCategory,
            applyTags,
            protectManualChanges,
            categoryResolver,
            tagResolver,
            ignored -> null
        );
    }

    public TransactionCandidateRuleApplicationResult applyAutomatically(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean applyCategory,
        boolean applyTags,
        boolean protectManualChanges,
        Function<Long, Category> categoryResolver,
        Function<Long, Tag> tagResolver,
        Function<Long, TransactionRule> ruleResolver
    ) {
        boolean categoryApplied =
            applyCategory && synchronizeCategory(candidate, evaluation, protectManualChanges, categoryResolver, ruleResolver);
        TagSynchronizationResult tagSynchronization = applyTags
            ? synchronizeTags(candidate, evaluation, protectManualChanges, tagResolver, ruleResolver)
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
        Function<Long, Category> categoryResolver,
        Function<Long, TransactionRule> ruleResolver
    ) {
        if (
            candidate.getCategory() == null &&
            evaluation.suggestedCategory() != null &&
            !evaluation.suggestedCategory().conflictsWithCurrentValue()
        ) {
            candidate.setCategory(categoryResolver.apply(evaluation.suggestedCategory().categoryId()));
            candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
            candidate.setCategoryAppliedRuleAttribution(ruleResolver.apply(evaluation.suggestedCategory().sourceRuleId()));
            return true;
        }
        return false;
    }

    private boolean synchronizeCategory(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean protectManualChanges,
        Function<Long, Category> categoryResolver,
        Function<Long, TransactionRule> ruleResolver
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
        if (categoryChanged || provenanceChanged) {
            candidate.setCategoryAppliedRuleAttribution(ruleResolver.apply(suggestion.sourceRuleId()));
        }
        return categoryChanged || provenanceChanged;
    }

    private List<Long> applySuggestedTags(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        Function<Long, Tag> tagResolver,
        Function<Long, TransactionRule> ruleResolver
    ) {
        List<Long> tagIdsApplied = new ArrayList<>();
        Set<Long> tagIds = currentTagIds(candidate);
        for (TagSuggestion suggestedTag : evaluation.suggestedTags()) {
            if (suggestedTag.alreadyPresent() || suggestedTag.duplicateOfEarlierSuggestion() || tagIds.contains(suggestedTag.tagId())) {
                continue;
            }
            Tag tag = tagResolver.apply(suggestedTag.tagId());
            candidate.addTag(tag, TransactionCandidateClassificationSource.AUTOMATIC, ruleResolver.apply(suggestedTag.sourceRuleId()));
            tagIds.add(tag.getId());
            tagIdsApplied.add(tag.getId());
        }
        return tagIdsApplied;
    }

    private TagSynchronizationResult synchronizeTags(
        TransactionCandidate candidate,
        TransactionRuleEvaluationResult evaluation,
        boolean protectManualChanges,
        Function<Long, Tag> tagResolver,
        Function<Long, TransactionRule> ruleResolver
    ) {
        Set<Long> suggestedTagIds = evaluation
            .suggestedTags()
            .stream()
            .map(TagSuggestion::tagId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Tag> obsoleteAutomaticTags = candidate
            .getTagAssociations()
            .stream()
            .filter(association -> !protectManualChanges || association.getSource() == TransactionCandidateClassificationSource.AUTOMATIC)
            .map(TransactionCandidateTag::getTag)
            .filter(tag -> tag != null && !suggestedTagIds.contains(tag.getId()))
            .toList();
        obsoleteAutomaticTags.forEach(candidate::removeTags);

        Set<Long> existingIds = currentTagIds(candidate);
        List<Long> addedTagIds = new ArrayList<>();
        boolean convertedManualAssociation = false;
        for (Long suggestedTagId : suggestedTagIds) {
            Tag suggestedTag = tagResolver.apply(suggestedTagId);
            TransactionCandidateTag existing = association(candidate, suggestedTagId);
            TagSuggestion suggestion = suggestion(evaluation, suggestedTagId);
            if (!existingIds.contains(suggestedTagId)) {
                candidate.addTag(
                    suggestedTag,
                    TransactionCandidateClassificationSource.AUTOMATIC,
                    ruleResolver.apply(suggestion.sourceRuleId())
                );
                existingIds.add(suggestedTagId);
                addedTagIds.add(suggestedTagId);
            } else if (
                !protectManualChanges && existing != null && existing.getSource() != TransactionCandidateClassificationSource.AUTOMATIC
            ) {
                candidate.addTag(
                    suggestedTag,
                    TransactionCandidateClassificationSource.AUTOMATIC,
                    ruleResolver.apply(suggestion.sourceRuleId())
                );
                convertedManualAssociation = true;
            }
        }
        return new TagSynchronizationResult(
            addedTagIds,
            !obsoleteAutomaticTags.isEmpty() || !addedTagIds.isEmpty() || convertedManualAssociation
        );
    }

    private TransactionCandidateTag association(TransactionCandidate candidate, Long tagId) {
        return candidate
            .getTagAssociations()
            .stream()
            .filter(association -> association.getTag() != null && Objects.equals(association.getTag().getId(), tagId))
            .findFirst()
            .orElse(null);
    }

    private TagSuggestion suggestion(TransactionRuleEvaluationResult evaluation, Long tagId) {
        return evaluation
            .suggestedTags()
            .stream()
            .filter(suggestion -> Objects.equals(suggestion.tagId(), tagId))
            .filter(suggestion -> !suggestion.duplicateOfEarlierSuggestion())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Automatic tag has no contributing rule"));
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
