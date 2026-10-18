package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;

/**
 * Explicit confirmation that the client is posting the currently displayed
 * version of a manual candidate with its persisted internal-transfer intent.
 */
public class ManualTransactionTransferPostCommand implements Serializable {

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
