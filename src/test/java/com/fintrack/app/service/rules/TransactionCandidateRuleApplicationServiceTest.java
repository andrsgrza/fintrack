package com.fintrack.app.service.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import java.util.LinkedHashSet;
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
    void existingTagsArePreservedAndNewSuggestedTagsAreAddedOnlyOnce() {
        Tag existingTag = tag(1L);
        TransactionCandidate candidate = new TransactionCandidate().tags(new LinkedHashSet<>(Set.of(existingTag)));
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
        assertThat(result.tagIdsApplied()).containsExactly(2L);
        assertThat(result.recommendedClassificationReviewStatus()).isEqualTo(TransactionCandidateClassificationReviewStatus.USER_SELECTED);
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
