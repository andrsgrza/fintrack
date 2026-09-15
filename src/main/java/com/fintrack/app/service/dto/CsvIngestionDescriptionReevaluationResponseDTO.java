package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CsvIngestionDescriptionReevaluationResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private List<CsvIngestionDescriptionReevaluationRowDTO> rows = new ArrayList<>();

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public List<CsvIngestionDescriptionReevaluationRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<CsvIngestionDescriptionReevaluationRowDTO> rows) {
        this.rows = rows;
    }
}
