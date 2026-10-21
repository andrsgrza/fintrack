package com.fintrack.app.service.dto;

import jakarta.validation.constraints.Positive;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

/** Optional, explicit manual classification for one newly created transfer leg. */
public class InternalTransferLegSelectionCommand implements Serializable {

    @Positive
    private Long categoryId;

    private Set<@Positive Long> tagIds = new LinkedHashSet<>();

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Set<Long> getTagIds() {
        return tagIds;
    }

    public void setTagIds(Set<Long> tagIds) {
        this.tagIds = tagIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(tagIds);
    }
}
