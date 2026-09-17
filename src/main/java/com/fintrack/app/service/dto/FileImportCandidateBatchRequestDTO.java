package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

public class FileImportCandidateBatchRequestDTO implements Serializable {

    private List<Long> candidateIds;

    private FileImportCandidateRulePreviewScope scope;

    /**
     * Opts into provenance-aware automatic application. Omitting this field preserves the legacy explicit
     * FILL_EMPTY_ONLY apply behavior.
     */
    private Boolean automatic;

    /**
     * Applies only when {@link #automatic} is true. Omitting it protects manual selections by default.
     */
    private Boolean protectManualChanges;

    public List<Long> getCandidateIds() {
        return candidateIds;
    }

    public void setCandidateIds(List<Long> candidateIds) {
        this.candidateIds = candidateIds;
    }

    public FileImportCandidateRulePreviewScope getScope() {
        return scope;
    }

    public void setScope(FileImportCandidateRulePreviewScope scope) {
        this.scope = scope;
    }

    public Boolean getAutomatic() {
        return automatic;
    }

    public void setAutomatic(Boolean automatic) {
        this.automatic = automatic;
    }

    public Boolean getProtectManualChanges() {
        return protectManualChanges;
    }

    public void setProtectManualChanges(Boolean protectManualChanges) {
        this.protectManualChanges = protectManualChanges;
    }
}
