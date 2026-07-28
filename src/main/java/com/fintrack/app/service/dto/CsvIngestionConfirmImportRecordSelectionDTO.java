package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

public class CsvIngestionConfirmImportRecordSelectionDTO implements Serializable {

    private Long recordId;

    private Long categoryId;

    private List<Long> tagIds;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
    }
}
