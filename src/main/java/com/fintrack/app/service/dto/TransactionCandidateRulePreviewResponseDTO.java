package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationReviewStatus;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for previewing TransactionRule evaluation against a TransactionCandidate.
 */
public class TransactionCandidateRulePreviewResponseDTO implements Serializable {

    private Long candidateId;

    private Instant candidateUpdatedAt;

    private TransactionCandidateClassificationReviewStatus classificationReviewStatus;

    private CategorySuggestionDTO suggestedCategory;

    private List<TagSuggestionDTO> suggestedTags = new ArrayList<>();

    private List<RuleOutputConflictDTO> conflicts = new ArrayList<>();

    private List<SkippedRuleOutputDTO> skippedOutputs = new ArrayList<>();

    private List<RuleMatchResultDTO> matchedRules = new ArrayList<>();

    private boolean hasSuggestions;

    private boolean hasConflicts;

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Instant getCandidateUpdatedAt() {
        return candidateUpdatedAt;
    }

    public void setCandidateUpdatedAt(Instant candidateUpdatedAt) {
        this.candidateUpdatedAt = candidateUpdatedAt;
    }

    public TransactionCandidateClassificationReviewStatus getClassificationReviewStatus() {
        return classificationReviewStatus;
    }

    public void setClassificationReviewStatus(TransactionCandidateClassificationReviewStatus classificationReviewStatus) {
        this.classificationReviewStatus = classificationReviewStatus;
    }

    public CategorySuggestionDTO getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(CategorySuggestionDTO suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public List<TagSuggestionDTO> getSuggestedTags() {
        return suggestedTags;
    }

    public void setSuggestedTags(List<TagSuggestionDTO> suggestedTags) {
        this.suggestedTags = suggestedTags;
    }

    public List<RuleOutputConflictDTO> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<RuleOutputConflictDTO> conflicts) {
        this.conflicts = conflicts;
    }

    public List<SkippedRuleOutputDTO> getSkippedOutputs() {
        return skippedOutputs;
    }

    public void setSkippedOutputs(List<SkippedRuleOutputDTO> skippedOutputs) {
        this.skippedOutputs = skippedOutputs;
    }

    public List<RuleMatchResultDTO> getMatchedRules() {
        return matchedRules;
    }

    public void setMatchedRules(List<RuleMatchResultDTO> matchedRules) {
        this.matchedRules = matchedRules;
    }

    public boolean isHasSuggestions() {
        return hasSuggestions;
    }

    public void setHasSuggestions(boolean hasSuggestions) {
        this.hasSuggestions = hasSuggestions;
    }

    public boolean isHasConflicts() {
        return hasConflicts;
    }

    public void setHasConflicts(boolean hasConflicts) {
        this.hasConflicts = hasConflicts;
    }
}
