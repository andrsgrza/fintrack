package com.fintrack.app.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.util.List;

public class FileImportCandidateClassificationRequestDTO implements Serializable {

    private Long categoryId;

    private boolean categoryIdProvided;

    private List<Long> tagIds;

    private boolean tagIdsProvided;

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.categoryIdProvided = true;
    }

    @JsonIgnore
    public boolean hasCategoryId() {
        return categoryIdProvided;
    }

    public List<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(List<Long> tagIds) {
        this.tagIds = tagIds;
        this.tagIdsProvided = true;
    }

    @JsonIgnore
    public boolean hasTagIds() {
        return tagIdsProvided;
    }
}
