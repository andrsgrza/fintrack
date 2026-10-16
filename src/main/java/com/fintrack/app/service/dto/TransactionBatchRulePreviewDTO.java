package com.fintrack.app.service.dto;

import java.util.List;

/** Pure, receipt-free rule preview for one current candidate state. */
public class TransactionBatchRulePreviewDTO {

    private Long candidateId;
    private Long candidateVersion;
    private boolean descriptionEvaluable;
    private boolean descriptionMatched;
    private Long descriptionRuleId;
    private String suggestedDescription;
    private boolean classificationEvaluable;
    private Long suggestedCategoryId;
    private List<Long> suggestedTagIds = List.of();
    private List<Long> eligibleTagIds = List.of();

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getCandidateVersion() {
        return candidateVersion;
    }

    public void setCandidateVersion(Long candidateVersion) {
        this.candidateVersion = candidateVersion;
    }

    public boolean isDescriptionEvaluable() {
        return descriptionEvaluable;
    }

    public void setDescriptionEvaluable(boolean descriptionEvaluable) {
        this.descriptionEvaluable = descriptionEvaluable;
    }

    public boolean isDescriptionMatched() {
        return descriptionMatched;
    }

    public void setDescriptionMatched(boolean descriptionMatched) {
        this.descriptionMatched = descriptionMatched;
    }

    public Long getDescriptionRuleId() {
        return descriptionRuleId;
    }

    public void setDescriptionRuleId(Long descriptionRuleId) {
        this.descriptionRuleId = descriptionRuleId;
    }

    public String getSuggestedDescription() {
        return suggestedDescription;
    }

    public void setSuggestedDescription(String suggestedDescription) {
        this.suggestedDescription = suggestedDescription;
    }

    public boolean isClassificationEvaluable() {
        return classificationEvaluable;
    }

    public void setClassificationEvaluable(boolean classificationEvaluable) {
        this.classificationEvaluable = classificationEvaluable;
    }

    public Long getSuggestedCategoryId() {
        return suggestedCategoryId;
    }

    public void setSuggestedCategoryId(Long suggestedCategoryId) {
        this.suggestedCategoryId = suggestedCategoryId;
    }

    public List<Long> getSuggestedTagIds() {
        return suggestedTagIds;
    }

    public void setSuggestedTagIds(List<Long> suggestedTagIds) {
        this.suggestedTagIds = suggestedTagIds == null ? List.of() : List.copyOf(suggestedTagIds);
    }

    public List<Long> getEligibleTagIds() {
        return eligibleTagIds;
    }

    public void setEligibleTagIds(List<Long> eligibleTagIds) {
        this.eligibleTagIds = eligibleTagIds == null ? List.of() : List.copyOf(eligibleTagIds);
    }
}
