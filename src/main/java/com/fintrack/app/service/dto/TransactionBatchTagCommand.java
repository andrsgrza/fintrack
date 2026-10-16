package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/** Candidate-scoped tag replacement command. */
public class TransactionBatchTagCommand {

    @NotNull
    private Long expectedCandidateVersion;

    @NotNull
    private Set<Long> tagIds;

    public Long getExpectedCandidateVersion() {
        return expectedCandidateVersion;
    }

    public void setExpectedCandidateVersion(Long expectedCandidateVersion) {
        this.expectedCandidateVersion = expectedCandidateVersion;
    }

    public Set<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<Long> tagIds) {
        this.tagIds = tagIds;
    }
}
