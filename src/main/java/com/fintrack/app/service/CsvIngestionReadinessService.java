package com.fintrack.app.service;

import com.fintrack.app.domain.IngestionRecord;
import com.fintrack.app.domain.TransactionIngestion;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.service.dto.CsvIngestionWorkflowCountsDTO;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CsvIngestionReadinessService {

    public IngestionStatus readinessStatus(int validRows, int rejectedOrFailedRows) {
        return rejectedOrFailedRows > 0 || validRows == 0 ? IngestionStatus.PARTIALLY_READY : IngestionStatus.READY;
    }

    public CsvIngestionReadinessSnapshot snapshot(List<IngestionRecord> records) {
        int imported = 0;
        int skipped = 0;
        int rejected = 0;
        int valid = 0;
        for (IngestionRecord record : records) {
            if (record.getStatus() == IngestionRecordStatus.IMPORTED) {
                imported++;
            } else if (
                record.getStatus() == IngestionRecordStatus.DISABLED || record.getStatus() == IngestionRecordStatus.SKIPPED_DUPLICATE
            ) {
                skipped++;
            } else if (record.getStatus() == IngestionRecordStatus.REJECTED || record.getStatus() == IngestionRecordStatus.FAILED) {
                rejected++;
            } else if (record.getStatus() == IngestionRecordStatus.VALID) {
                valid++;
            }
        }
        CsvIngestionWorkflowCountsDTO counts = new CsvIngestionWorkflowCountsDTO();
        counts.setRecordsReceived(records.size());
        counts.setRecordsCreated(imported);
        counts.setRecordsSkipped(skipped);
        counts.setRecordsRejected(rejected);
        counts.setValidRows(valid);
        counts.setInvalidRows(rejected);
        return new CsvIngestionReadinessSnapshot(counts, readinessStatus(valid, rejected));
    }

    public CsvIngestionReadinessSnapshot applyReadiness(TransactionIngestion ingestion, List<IngestionRecord> records) {
        CsvIngestionReadinessSnapshot snapshot = snapshot(records);
        applyCounts(ingestion, snapshot.counts());
        ingestion.setStatus(snapshot.status());
        return snapshot;
    }

    public void applyCounts(TransactionIngestion ingestion, CsvIngestionWorkflowCountsDTO counts) {
        ingestion.setRecordsReceived(counts.getRecordsReceived());
        ingestion.setRecordsCreated(counts.getRecordsCreated());
        ingestion.setRecordsSkipped(counts.getRecordsSkipped());
        ingestion.setRecordsRejected(counts.getRecordsRejected());
    }

    public record CsvIngestionReadinessSnapshot(CsvIngestionWorkflowCountsDTO counts, IngestionStatus status) {}
}
