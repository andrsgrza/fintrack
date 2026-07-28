package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

public class CsvIngestionConfirmImportRequestDTO implements Serializable {

    private List<CsvIngestionConfirmImportRecordSelectionDTO> records;

    public List<CsvIngestionConfirmImportRecordSelectionDTO> getRecords() {
        return records;
    }

    public void setRecords(List<CsvIngestionConfirmImportRecordSelectionDTO> records) {
        this.records = records;
    }
}
