package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionCandidateInternalTransferMode;
import java.io.Serializable;

/** Read-only transfer intent projection for one temporary Batch row. */
public class TransactionBatchInternalTransferViewDTO implements Serializable {

    private boolean enabled;
    private boolean stale;
    private TransactionCandidateInternalTransferMode mode;
    private InternalTransferCounterpartDTO counterpart;
    private TransactionBatchCreateCounterpartViewDTO createCounterpart;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    public TransactionCandidateInternalTransferMode getMode() {
        return mode;
    }

    public void setMode(TransactionCandidateInternalTransferMode mode) {
        this.mode = mode;
    }

    public InternalTransferCounterpartDTO getCounterpart() {
        return counterpart;
    }

    public void setCounterpart(InternalTransferCounterpartDTO counterpart) {
        this.counterpart = counterpart;
    }

    public TransactionBatchCreateCounterpartViewDTO getCreateCounterpart() {
        return createCounterpart;
    }

    public void setCreateCounterpart(TransactionBatchCreateCounterpartViewDTO createCounterpart) {
        this.createCounterpart = createCounterpart;
    }
}
