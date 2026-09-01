package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.service.rules.RuleMatchResult;
import com.fintrack.app.service.rules.RuleOutputConflict;
import com.fintrack.app.service.rules.SkippedRuleOutput;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public class CsvIngestionClassificationPreviewRowDTO implements Serializable {

    private Long recordId;

    private Integer recordIndex;

    private String description;

    private LocalDate transactionDate;

    private String signedAmount;

    private String amount;

    private TransactionFlow flow;

    private CsvIngestionClassificationCategoryDTO suggestedCategory;

    private List<CsvIngestionClassificationTagDTO> suggestedTags;

    private List<RuleMatchResult> matchedRules;

    private List<RuleOutputConflict> conflicts;

    private List<SkippedRuleOutput> skippedOutputs;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Integer getRecordIndex() {
        return recordIndex;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getSignedAmount() {
        return signedAmount;
    }

    public void setSignedAmount(String signedAmount) {
        this.signedAmount = signedAmount;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public TransactionFlow getFlow() {
        return flow;
    }

    public void setFlow(TransactionFlow flow) {
        this.flow = flow;
    }

    public CsvIngestionClassificationCategoryDTO getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(CsvIngestionClassificationCategoryDTO suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public List<CsvIngestionClassificationTagDTO> getSuggestedTags() {
        return suggestedTags;
    }

    public void setSuggestedTags(List<CsvIngestionClassificationTagDTO> suggestedTags) {
        this.suggestedTags = suggestedTags;
    }

    public List<RuleMatchResult> getMatchedRules() {
        return matchedRules;
    }

    public void setMatchedRules(List<RuleMatchResult> matchedRules) {
        this.matchedRules = matchedRules;
    }

    public List<RuleOutputConflict> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<RuleOutputConflict> conflicts) {
        this.conflicts = conflicts;
    }

    public List<SkippedRuleOutput> getSkippedOutputs() {
        return skippedOutputs;
    }

    public void setSkippedOutputs(List<SkippedRuleOutput> skippedOutputs) {
        this.skippedOutputs = skippedOutputs;
    }
}
