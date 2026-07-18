package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.IngestionStatus;
import java.io.Serializable;

public class CsvIngestionRecordReviewResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private IngestionStatus status;

    private CsvIngestionPreviewCountsDTO counts;

    private CsvIngestionPreviewRowDTO row;

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public CsvIngestionPreviewCountsDTO getCounts() {
        return counts;
    }

    public void setCounts(CsvIngestionPreviewCountsDTO counts) {
        this.counts = counts;
    }

    public CsvIngestionPreviewRowDTO getRow() {
        return row;
    }

    public void setRow(CsvIngestionPreviewRowDTO row) {
        this.row = row;
    }
}
