package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;

/** Explicitly removes only the temporary transfer intent from one OPEN batch row. */
public class TransactionBatchInternalTransferClearCommand implements Serializable {

    @NotNull
    @PositiveOrZero
    private Long expectedCandidateVersion;

    public Long getExpectedCandidateVersion() {
        return expectedCandidateVersion;
    }

    public void setExpectedCandidateVersion(Long expectedCandidateVersion) {
        this.expectedCandidateVersion = expectedCandidateVersion;
    }
}
