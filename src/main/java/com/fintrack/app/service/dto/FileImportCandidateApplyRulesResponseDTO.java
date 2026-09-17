package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class FileImportCandidateApplyRulesResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private List<FileImportCandidateApplyRulesRowDTO> rows = new ArrayList<>();

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public List<FileImportCandidateApplyRulesRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<FileImportCandidateApplyRulesRowDTO> rows) {
        this.rows = rows;
    }
}
