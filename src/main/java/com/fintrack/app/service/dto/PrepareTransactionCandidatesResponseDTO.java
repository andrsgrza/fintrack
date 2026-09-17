package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class PrepareTransactionCandidatesResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private int createdCount;

    private int updatedCount;

    private int unchangedCount;

    private int skippedCount;

    private int errorCount;

    private List<PrepareTransactionCandidateRowResultDTO> rows = new ArrayList<>();

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public void setUnchangedCount(int unchangedCount) {
        this.unchangedCount = unchangedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public List<PrepareTransactionCandidateRowResultDTO> getRows() {
        return rows;
    }

    public void setRows(List<PrepareTransactionCandidateRowResultDTO> rows) {
        this.rows = rows;
    }

    public void incrementCreatedCount() {
        createdCount++;
    }

    public void incrementUpdatedCount() {
        updatedCount++;
    }

    public void incrementUnchangedCount() {
        unchangedCount++;
    }

    public void incrementSkippedCount() {
        skippedCount++;
    }

    public void incrementErrorCount() {
        errorCount++;
    }
}
