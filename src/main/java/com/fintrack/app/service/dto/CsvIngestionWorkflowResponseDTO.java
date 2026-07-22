package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.service.csv.CsvIngestionValidationMessage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CsvIngestionWorkflowResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private Long fileIngestionId;

    private IngestionStatus status;

    private String sourceLabel;

    private CsvIngestionWorkflowCountsDTO counts;

    private List<CsvIngestionValidationMessage> warnings = new ArrayList<>();

    private CsvIngestionFileMetadataDTO fileMetadata;

    private List<CsvIngestionWorkflowRecordDTO> rows = new ArrayList<>();

    public Long getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(Long transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public Long getFileIngestionId() {
        return fileIngestionId;
    }

    public void setFileIngestionId(Long fileIngestionId) {
        this.fileIngestionId = fileIngestionId;
    }

    public IngestionStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionStatus status) {
        this.status = status;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public void setSourceLabel(String sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public CsvIngestionWorkflowCountsDTO getCounts() {
        return counts;
    }

    public void setCounts(CsvIngestionWorkflowCountsDTO counts) {
        this.counts = counts;
    }

    public List<CsvIngestionValidationMessage> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<CsvIngestionValidationMessage> warnings) {
        this.warnings = warnings;
    }

    public CsvIngestionFileMetadataDTO getFileMetadata() {
        return fileMetadata;
    }

    public void setFileMetadata(CsvIngestionFileMetadataDTO fileMetadata) {
        this.fileMetadata = fileMetadata;
    }

    public List<CsvIngestionWorkflowRecordDTO> getRows() {
        return rows;
    }

    public void setRows(List<CsvIngestionWorkflowRecordDTO> rows) {
        this.rows = rows;
    }
}
