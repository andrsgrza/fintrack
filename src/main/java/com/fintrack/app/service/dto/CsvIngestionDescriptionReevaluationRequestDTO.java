package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Optional record selection for a scoped CSV description-normalization reevaluation.
 * Omitting {@code recordIds} reevaluates every eligible row in the ingestion.
 */
public class CsvIngestionDescriptionReevaluationRequestDTO implements Serializable {

    private List<Long> recordIds;

    /**
     * Whether the evaluated description result should be persisted. Omitting this preserves the historical
     * reevaluation command behavior, which applies normalization results.
     */
    private Boolean apply;

    /**
     * Applies only when {@link #apply} is true. Omitting it preserves the existing protection for USER_EDIT
     * descriptions.
     */
    private Boolean protectManualChanges;

    public List<Long> getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(List<Long> recordIds) {
        this.recordIds = recordIds;
    }

    public Boolean getApply() {
        return apply;
    }

    public void setApply(Boolean apply) {
        this.apply = apply;
    }

    public Boolean getProtectManualChanges() {
        return protectManualChanges;
    }

    public void setProtectManualChanges(Boolean protectManualChanges) {
        this.protectManualChanges = protectManualChanges;
    }
}
