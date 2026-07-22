package com.fintrack.app.service.dto;

import java.io.Serializable;

public class CsvIngestionWorkflowCountsDTO implements Serializable {

    private Integer recordsReceived;

    private Integer recordsCreated;

    private Integer recordsSkipped;

    private Integer recordsRejected;

    private Integer validRows;

    private Integer invalidRows;

    public Integer getRecordsReceived() {
        return recordsReceived;
    }

    public void setRecordsReceived(Integer recordsReceived) {
        this.recordsReceived = recordsReceived;
    }

    public Integer getRecordsCreated() {
        return recordsCreated;
    }

    public void setRecordsCreated(Integer recordsCreated) {
        this.recordsCreated = recordsCreated;
    }

    public Integer getRecordsSkipped() {
        return recordsSkipped;
    }

    public void setRecordsSkipped(Integer recordsSkipped) {
        this.recordsSkipped = recordsSkipped;
    }

    public Integer getRecordsRejected() {
        return recordsRejected;
    }

    public void setRecordsRejected(Integer recordsRejected) {
        this.recordsRejected = recordsRejected;
    }

    public Integer getValidRows() {
        return validRows;
    }

    public void setValidRows(Integer validRows) {
        this.validRows = validRows;
    }

    public Integer getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(Integer invalidRows) {
        this.invalidRows = invalidRows;
    }
}
