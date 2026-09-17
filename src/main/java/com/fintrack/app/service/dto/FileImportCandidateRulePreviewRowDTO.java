package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FileImportCandidateRulePreviewRowDTO implements Serializable {

    private Long candidateId;

    private Long ingestionRecordId;

    private Integer recordIndex;

    private String action;

    private String error;

    private TransactionCandidateWorkflowSummaryDTO candidate;

    private CategorySuggestionDTO suggestedCategory;

    private List<TagSuggestionDTO> suggestedTags = new ArrayList<>();

    private List<RuleMatchResultDTO> matchedRules = new ArrayList<>();

    private List<RuleOutputConflictDTO> conflicts = new ArrayList<>();

    private List<SkippedRuleOutputDTO> skippedOutputs = new ArrayList<>();

    private boolean hasSuggestions;

    private boolean hasConflicts;

    public Long getCandidateId() {
        return candidateId;
    }

    public void setCandidateId(Long candidateId) {
        this.candidateId = candidateId;
    }

    public Long getIngestionRecordId() {
        return ingestionRecordId;
    }

    public void setIngestionRecordId(Long ingestionRecordId) {
        this.ingestionRecordId = ingestionRecordId;
    }

    public Integer getRecordIndex() {
        return recordIndex;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public TransactionCandidateWorkflowSummaryDTO getCandidate() {
        return candidate;
    }

    public void setCandidate(TransactionCandidateWorkflowSummaryDTO candidate) {
        this.candidate = candidate;
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

    public List<RuleMatchResultDTO> getMatchedRules() {
        return matchedRules;
    }

    public void setMatchedRules(List<RuleMatchResultDTO> matchedRules) {
        this.matchedRules = matchedRules;
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
