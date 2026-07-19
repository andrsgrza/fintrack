package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.IngestionStatus;
import java.io.Serializable;

public class CsvIngestionRecordReviewResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private IngestionStatus status;

    private CsvIngestionWorkflowCountsDTO counts;

    private CsvIngestionWorkflowRecordDTO row;

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

    public CsvIngestionWorkflowCountsDTO getCounts() {
        return counts;
    }

    public void setCounts(CsvIngestionWorkflowCountsDTO counts) {
        this.counts = counts;
    }

    public CsvIngestionWorkflowRecordDTO getRow() {
        return row;
    }

    public void setRow(CsvIngestionWorkflowRecordDTO row) {
        this.row = row;
    }
}
