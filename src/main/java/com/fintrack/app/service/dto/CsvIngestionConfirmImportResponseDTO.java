package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.IngestionStatus;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CsvIngestionConfirmImportResponseDTO implements Serializable {

    private Long transactionIngestionId;

    private IngestionStatus status;

    private Integer createdNow;

    private Integer alreadyImported;

    private Integer skipped;

    private Integer rejected;

    private Integer failed;

    private CsvIngestionPreviewCountsDTO counts;

    private List<CsvIngestionPreviewRowDTO> rows = new ArrayList<>();

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

    public Integer getCreatedNow() {
        return createdNow;
    }

    public void setCreatedNow(Integer createdNow) {
        this.createdNow = createdNow;
    }

    public Integer getAlreadyImported() {
        return alreadyImported;
    }

    public void setAlreadyImported(Integer alreadyImported) {
        this.alreadyImported = alreadyImported;
    }

    public Integer getSkipped() {
        return skipped;
    }

    public void setSkipped(Integer skipped) {
        this.skipped = skipped;
    }

    public Integer getRejected() {
        return rejected;
    }

    public void setRejected(Integer rejected) {
        this.rejected = rejected;
    }

    public Integer getFailed() {
        return failed;
    }

    public void setFailed(Integer failed) {
        this.failed = failed;
    }

    public CsvIngestionPreviewCountsDTO getCounts() {
        return counts;
    }

    public void setCounts(CsvIngestionPreviewCountsDTO counts) {
        this.counts = counts;
    }

    public List<CsvIngestionPreviewRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<CsvIngestionPreviewRowDTO> rows) {
        this.rows = rows;
    }
}
