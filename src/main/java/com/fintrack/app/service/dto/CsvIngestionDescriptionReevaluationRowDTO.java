package com.fintrack.app.service.dto;

import java.io.Serializable;

/**
 * Read-only result for one description-normalization reevaluation row.
 * Suggested descriptions for manual edits are transient and are not written to rawData.
 */
public class CsvIngestionDescriptionReevaluationRowDTO implements Serializable {

    private Long ingestionRecordId;

    private Integer recordIndex;

    private String action;

    private String error;

    private String suggestedDescription;

    private CsvIngestionDescriptionReviewDTO descriptionReview;

    public Long getIngestionRecordId() {
        return ingestionRecordId;
    }

    public void setIngestionRecordId(Long ingestionRecordId) {
        this.ingestionRecordId = ingestionRecordId;
    }

    public Integer getRecordIndex() {
        return recordIndex;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getSuggestedDescription() {
        return suggestedDescription;
    }

    public void setSuggestedDescription(String suggestedDescription) {
        this.suggestedDescription = suggestedDescription;
    }

    public CsvIngestionDescriptionReviewDTO getDescriptionReview() {
        return descriptionReview;
    }

    public void setDescriptionReview(CsvIngestionDescriptionReviewDTO descriptionReview) {
        this.descriptionReview = descriptionReview;
    }
}
