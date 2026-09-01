package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

public class CsvIngestionClassificationPreviewResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private List<CsvIngestionClassificationPreviewRowDTO> rows;

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public List<CsvIngestionClassificationPreviewRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<CsvIngestionClassificationPreviewRowDTO> rows) {
        this.rows = rows;
    }
}
