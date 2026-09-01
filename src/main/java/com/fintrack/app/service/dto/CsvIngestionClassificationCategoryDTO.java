package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.CategoryType;
import java.io.Serializable;

public class CsvIngestionClassificationCategoryDTO implements Serializable {

    private Long id;

    private String name;

    private CategoryType categoryType;

    public CsvIngestionClassificationCategoryDTO() {}

    public CsvIngestionClassificationCategoryDTO(Long id, String name, CategoryType categoryType) {
        this.id = id;
        this.name = name;
        this.categoryType = categoryType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CategoryType getCategoryType() {
        return categoryType;
    }

    public void setCategoryType(CategoryType categoryType) {
        this.categoryType = categoryType;
    }
}
