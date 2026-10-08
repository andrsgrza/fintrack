package com.fintrack.app.service.dto;

import java.io.Serializable;

/** Counts of data that a future aggregate FinancialAccount hard delete would inspect. */
public class FinancialAccountDeletionPreviewCountsDTO implements Serializable {

    private Long financialTransactions = 0L;
    private Long manualCandidates = 0L;
    private Long transactionIngestions = 0L;
    private Long ingestionRecords = 0L;
    private Long fileImportCandidates = 0L;
    private Long creditAccountDetails = 0L;
    private Long budgetLinks = 0L;
    private Long subscriptions = 0L;
    private Long crossAccountTransfers = 0L;
    private Long ruleAccountReferences = 0L;

    public Long getFinancialTransactions() {
        return financialTransactions;
    }

    public void setFinancialTransactions(Long value) {
        this.financialTransactions = value;
    }

    public Long getManualCandidates() {
        return manualCandidates;
    }

    public void setManualCandidates(Long value) {
        this.manualCandidates = value;
    }

    public Long getTransactionIngestions() {
        return transactionIngestions;
    }

    public void setTransactionIngestions(Long value) {
        this.transactionIngestions = value;
    }

    public Long getIngestionRecords() {
        return ingestionRecords;
    }

    public void setIngestionRecords(Long value) {
        this.ingestionRecords = value;
    }

    public Long getFileImportCandidates() {
        return fileImportCandidates;
    }

    public void setFileImportCandidates(Long value) {
        this.fileImportCandidates = value;
    }

    public Long getCreditAccountDetails() {
        return creditAccountDetails;
    }

    public void setCreditAccountDetails(Long value) {
        this.creditAccountDetails = value;
    }

    public Long getBudgetLinks() {
        return budgetLinks;
    }

    public void setBudgetLinks(Long value) {
        this.budgetLinks = value;
    }

    public Long getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Long value) {
        this.subscriptions = value;
    }

    public Long getCrossAccountTransfers() {
        return crossAccountTransfers;
    }

    public void setCrossAccountTransfers(Long value) {
        this.crossAccountTransfers = value;
    }

    public Long getRuleAccountReferences() {
        return ruleAccountReferences;
    }

    public void setRuleAccountReferences(Long value) {
        this.ruleAccountReferences = value;
    }
}
