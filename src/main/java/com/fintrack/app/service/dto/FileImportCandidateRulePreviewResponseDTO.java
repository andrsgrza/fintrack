package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FileImportCandidateRulePreviewResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private List<FileImportCandidateRulePreviewRowDTO> rows = new ArrayList<>();

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public List<FileImportCandidateRulePreviewRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<FileImportCandidateRulePreviewRowDTO> rows) {
        this.rows = rows;
    }
}
