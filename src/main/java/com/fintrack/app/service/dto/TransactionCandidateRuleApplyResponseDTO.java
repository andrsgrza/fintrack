package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO for applying TransactionRule suggestions to a TransactionCandidate.
 */
public class TransactionCandidateRuleApplyResponseDTO implements Serializable {

    private TransactionCandidateDTO candidate;

    private TransactionCandidateRulePreviewResponseDTO evaluation;

    private boolean categoryApplied;

    private List<Long> tagIdsApplied = new ArrayList<>();

    public TransactionCandidateDTO getCandidate() {
        return candidate;
    }

    public void setCandidate(TransactionCandidateDTO candidate) {
        this.candidate = candidate;
    }

    public TransactionCandidateRulePreviewResponseDTO getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(TransactionCandidateRulePreviewResponseDTO evaluation) {
        this.evaluation = evaluation;
    }

    public boolean isCategoryApplied() {
        return categoryApplied;
    }

    public void setCategoryApplied(boolean categoryApplied) {
        this.categoryApplied = categoryApplied;
    }

    public List<Long> getTagIdsApplied() {
        return tagIdsApplied;
    }

    public void setTagIdsApplied(List<Long> tagIdsApplied) {
        this.tagIdsApplied = tagIdsApplied;
    }
}
