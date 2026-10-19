package com.fintrack.app.service.dto;

import java.io.Serializable;

/** Read-only transfer intent projection for one temporary Batch row. */
public class TransactionBatchInternalTransferViewDTO implements Serializable {

    private boolean enabled;
    private boolean stale;
    private InternalTransferCounterpartDTO counterpart;

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

    public InternalTransferCounterpartDTO getCounterpart() {
        return counterpart;
    }

    public void setCounterpart(InternalTransferCounterpartDTO counterpart) {
        this.counterpart = counterpart;
    }
}
