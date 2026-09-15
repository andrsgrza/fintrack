package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Optional record selection for a scoped CSV description-normalization reevaluation.
 * Omitting {@code recordIds} reevaluates every eligible row in the ingestion.
 */
public class CsvIngestionDescriptionReevaluationRequestDTO implements Serializable {

    private List<Long> recordIds;

    public List<Long> getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(List<Long> recordIds) {
        this.recordIds = recordIds;
    }
}
