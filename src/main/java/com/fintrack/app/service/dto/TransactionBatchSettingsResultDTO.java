package com.fintrack.app.service.dto;

/** Persisted settings state, deliberately separate from any follow-up evaluation outcome. */
public class TransactionBatchSettingsResultDTO {

    private Long batchId;
    private Long batchVersion;
    private boolean changed;

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getBatchVersion() {
        return batchVersion;
    }

    public void setBatchVersion(Long batchVersion) {
        this.batchVersion = batchVersion;
    }

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(boolean changed) {
        this.changed = changed;
    }
}
