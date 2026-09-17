package com.fintrack.app.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionCandidateRuleApplicationServiceTest {

    private final TransactionCandidateRuleApplicationService service = new TransactionCandidateRuleApplicationService();

    @Test
    void emptyCategoryWithSuggestedCategoryAppliesCategory() {
        TransactionCandidate candidate = new TransactionCandidate();
        TransactionRuleEvaluationResult evaluation = evaluation(categorySuggestion(10L, false), List.of());

        TransactionCandidateRuleApplicationResult result = service.applyFillEmptyOnly(
            candidate,
            evaluation,
            false,
            this::category,
            this::tag
        );

        assertThat(candidate.getCategory()).isNotNull();
        assertThat(candidate.getCategory().getId()).isEqualTo(10L);
        assertThat(candidate.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(result.categoryApplied()).isTrue();
        assertThat(result.recommendedClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.SUGGESTED);
    }

    @Test
    void existingCategoryIsNotOverwritten() {
        TransactionCandidate candidate = new TransactionCandidate().category(category(1L));
        TransactionRuleEvaluationResult evaluation = evaluation(categorySuggestion(10L, false), List.of());

        TransactionCandidateRuleApplicationResult result = service.applyFillEmptyOnly(
            candidate,
            evaluation,
            true,
            this::category,
            this::tag
        );

        assertThat(candidate.getCategory().getId()).isEqualTo(1L);
        assertThat(result.categoryApplied()).isFalse();
        assertThat(result.recommendedClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
    }

    @Test
    void categoryConflictDoesNotApplyCategory() {
        TransactionCandidate candidate = new TransactionCandidate();
        TransactionRuleEvaluationResult evaluation = evaluation(categorySuggestion(10L, true), List.of());

        TransactionCandidateRuleApplicationResult result = service.applyFillEmptyOnly(
            candidate,
            evaluation,
            false,
            this::category,
            this::tag
        );

        assertThat(candidate.getCategory()).isNull();
        assertThat(result.categoryApplied()).isFalse();
        assertThat(result.recommendedClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.SUGGESTED);
    }

    @Test
    void scopedExplicitApplyChangesOnlyItsRequestedDomain() {
        TransactionRuleEvaluationResult evaluation = evaluation(categorySuggestion(10L, false), List.of(tagSuggestion(20L, false, false)));

        TransactionCandidate categoryCandidate = new TransactionCandidate();
        TransactionCandidateRuleApplicationResult categoryResult = service.applyFillEmptyOnly(
            categoryCandidate,
            evaluation,
            false,
            true,
            false,
            this::category,
            this::tag
        );

        assertThat(categoryCandidate.getCategory().getId()).isEqualTo(10L);
        assertThat(categoryCandidate.getTags()).isEmpty();
        assertThat(categoryResult.categoryApplied()).isTrue();
        assertThat(categoryResult.tagIdsApplied()).isEmpty();

        TransactionCandidate tagsCandidate = new TransactionCandidate();
        TransactionCandidateRuleApplicationResult tagsResult = service.applyFillEmptyOnly(
            tagsCandidate,
            evaluation,
            false,
            false,
            true,
            this::category,
            this::tag
        );

        assertThat(tagsCandidate.getCategory()).isNull();
        assertThat(tagsCandidate.getTags()).extracting(Tag::getId).containsExactly(20L);
        assertThat(tagsResult.categoryApplied()).isFalse();
        assertThat(tagsResult.tagIdsApplied()).containsExactly(20L);
    }

    @Test
    void existingTagsArePreservedAndNewSuggestedTagsAreAddedOnlyOnce() {
        Tag existingTag = tag(1L);
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.addTag(existingTag, TransactionCandidateClassificationSource.AUTOMATIC);
        TransactionRuleEvaluationResult evaluation = evaluation(
            null,
            List.of(tagSuggestion(1L, false, false), tagSuggestion(2L, false, false), tagSuggestion(2L, false, true))
        );

        TransactionCandidateRuleApplicationResult result = service.applyFillEmptyOnly(
            candidate,
            evaluation,
            true,
            this::category,
            this::tag
        );

        assertThat(candidate.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(candidate.getTagSource(existingTag)).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(candidate.getTagSource(tag(2L))).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(result.tagIdsApplied()).containsExactly(2L);
        assertThat(result.recommendedClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
    }

    @Test
    void automaticApplyDoesNotDowngradeAnExistingManualTag() {
        Tag manualTag = tag(1L);
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.addTag(manualTag, TransactionCandidateClassificationSource.MANUAL);

        service.applyFillEmptyOnly(candidate, evaluation(null, List.of(tagSuggestion(1L, false, false))), false, this::category, this::tag);

        assertThat(candidate.getTagSource(manualTag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(candidate.getTagAssociations()).hasSize(1);
    }

    @Test
    void nullTagSetIsHandledSafely() {
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.setTags(null);
        TransactionRuleEvaluationResult evaluation = evaluation(null, List.of(tagSuggestion(2L, false, false)));

        TransactionCandidateRuleApplicationResult result = service.applyFillEmptyOnly(
            candidate,
            evaluation,
            false,
            this::category,
            this::tag
        );

        assertThat(candidate.getTags()).extracting(Tag::getId).containsExactly(2L);
        assertThat(result.tagIdsApplied()).containsExactly(2L);
        assertThat(result.recommendedClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.SUGGESTED);
    }

    @Test
    void immutableTagSetIsHandledSafely() {
        TransactionCandidate candidate = new TransactionCandidate().tags(Set.of(tag(1L)));
        TransactionRuleEvaluationResult evaluation = evaluation(null, List.of(tagSuggestion(2L, false, false)));

        TransactionCandidateRuleApplicationResult result = service.applyFillEmptyOnly(
            candidate,
            evaluation,
            false,
            this::category,
            this::tag
        );

        assertThat(candidate.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.tagIdsApplied()).containsExactly(2L);
    }

    @Test
    void noSuggestionsRecommendsNotApplicable() {
        TransactionCandidate candidate = new TransactionCandidate();
        TransactionRuleEvaluationResult evaluation = evaluation(null, List.of());

        TransactionCandidateRuleApplicationResult result = service.applyFillEmptyOnly(
            candidate,
            evaluation,
            false,
            this::category,
            this::tag
        );

        assertThat(result.hasAppliedChanges()).isFalse();
        assertThat(result.recommendedClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.NOT_APPLICABLE);
    }

    @Test
    void automaticCategoryScopeReplacesAndClearsAutomaticValuesWithoutChangingTags() {
        Tag manualTag = tag(9L);
        TransactionCandidate candidate = new TransactionCandidate().category(category(1L));
        candidate.setCategorySource(TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(manualTag, TransactionCandidateClassificationSource.MANUAL);

        TransactionCandidateRuleApplicationResult replaced = service.applyAutomatically(
            candidate,
            evaluation(categorySuggestion(2L, true), List.of(tagSuggestion(3L, false, false))),
            true,
            false,
            true,
            this::category,
            this::tag
        );

        assertThat(replaced.categoryApplied()).isTrue();
        assertThat(candidate.getCategory().getId()).isEqualTo(2L);
        assertThat(candidate.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(candidate.getTags()).extracting(Tag::getId).containsExactly(9L);
        assertThat(candidate.getTagSource(manualTag)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);

        service.applyAutomatically(candidate, evaluation(null, List.of()), true, false, true, this::category, this::tag);

        assertThat(candidate.getCategory()).isNull();
        assertThat(candidate.getCategorySource()).isNull();
        assertThat(candidate.getTags()).extracting(Tag::getId).containsExactly(9L);
    }

    @Test
    void automaticCategoryScopeProtectsOrReplacesManualCategoryAccordingToTheRequest() {
        TransactionCandidate protectedCandidate = new TransactionCandidate().category(category(1L));
        protectedCandidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        TransactionRuleEvaluationResult suggestion = evaluation(categorySuggestion(2L, true), List.of());

        service.applyAutomatically(protectedCandidate, suggestion, true, false, true, this::category, this::tag);

        assertThat(protectedCandidate.getCategory().getId()).isEqualTo(1L);
        assertThat(protectedCandidate.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);

        service.applyAutomatically(protectedCandidate, suggestion, true, false, false, this::category, this::tag);

        assertThat(protectedCandidate.getCategory().getId()).isEqualTo(2L);
        assertThat(protectedCandidate.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);

        TransactionCandidate manualWithoutSuggestion = new TransactionCandidate().category(category(3L));
        manualWithoutSuggestion.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        service.applyAutomatically(manualWithoutSuggestion, evaluation(null, List.of()), true, false, false, this::category, this::tag);
        assertThat(manualWithoutSuggestion.getCategory()).isNull();
        assertThat(manualWithoutSuggestion.getCategorySource()).isNull();
    }

    @Test
    void automaticTagScopeSynchronizesAutomaticTagsAndPreservesManualTagsWhenProtected() {
        Tag automaticObsolete = tag(1L);
        Tag automaticRetained = tag(2L);
        Tag manualRetained = tag(3L);
        TransactionCandidate candidate = new TransactionCandidate().category(category(7L));
        candidate.setCategorySource(TransactionCandidateClassificationSource.MANUAL);
        candidate.addTag(automaticObsolete, TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(automaticRetained, TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(manualRetained, TransactionCandidateClassificationSource.MANUAL);

        service.applyAutomatically(
            candidate,
            evaluation(null, List.of(tagSuggestion(2L, true, false), tagSuggestion(4L, false, false))),
            false,
            true,
            true,
            this::category,
            this::tag
        );

        assertThat(candidate.getCategory().getId()).isEqualTo(7L);
        assertThat(candidate.getCategorySource()).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(candidate.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(2L, 3L, 4L);
        assertThat(candidate.getTagSource(automaticRetained)).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
        assertThat(candidate.getTagSource(manualRetained)).isEqualTo(TransactionCandidateClassificationSource.MANUAL);
        assertThat(candidate.getTagSource(tag(4L))).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC);
    }

    @Test
    void automaticTagScopeReplacesTheEntireTagSetWhenManualProtectionIsOff() {
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.addTag(tag(1L), TransactionCandidateClassificationSource.AUTOMATIC);
        candidate.addTag(tag(2L), TransactionCandidateClassificationSource.MANUAL);
        candidate.addTag(tag(3L), TransactionCandidateClassificationSource.MANUAL);

        service.applyAutomatically(
            candidate,
            evaluation(null, List.of(tagSuggestion(2L, true, false), tagSuggestion(4L, false, false))),
            false,
            true,
            false,
            this::category,
            this::tag
        );

        assertThat(candidate.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(2L, 4L);
        assertThat(candidate.getTagAssociations()).allSatisfy(association ->
            assertThat(association.getSource()).isEqualTo(TransactionCandidateClassificationSource.AUTOMATIC)
        );
    }

    private TransactionRuleEvaluationResult evaluation(CategorySuggestion categorySuggestion, List<TagSuggestion> tagSuggestions) {
        return new TransactionRuleEvaluationResult(List.of(), categorySuggestion, tagSuggestions, List.of(), List.of());
    }

    private CategorySuggestion categorySuggestion(Long categoryId, boolean conflictsWithCurrentValue) {
        return new CategorySuggestion(categoryId, "Category " + categoryId, 1L, "Rule", conflictsWithCurrentValue, null, null);
    }

    private TagSuggestion tagSuggestion(Long tagId, boolean alreadyPresent, boolean duplicateOfEarlierSuggestion) {
        return new TagSuggestion(tagId, "Tag " + tagId, 1L, "Rule", alreadyPresent, duplicateOfEarlierSuggestion);
    }

    private Category category(Long id) {
        return new Category().id(id).name("Category " + id);
    }

    private Tag tag(Long id) {
        return new Tag().id(id).name("Tag " + id);
    }
}
