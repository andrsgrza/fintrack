package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

public class FileImportCandidateBatchRequestDTO implements Serializable {

    private List<Long> candidateIds;

    public List<Long> getCandidateIds() {
        return candidateIds;
    }

    public void setCandidateIds(List<Long> candidateIds) {
        this.candidateIds = candidateIds;
    }
}
