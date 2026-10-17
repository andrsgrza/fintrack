package com.fintrack.app.service.dto;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Explicit acceptance of a semantic server preview. IDs in this input are compared to a fresh server evaluation;
 * they are never treated as free-form classification instructions.
 */
public class TransactionBatchRuleApplyCommand {

    private Long expectedCandidateVersion;
    private boolean applyDescription;
    private String expectedSuggestedDescription;
    private boolean applyCategory;
    private Long expectedSuggestedCategoryId;
    private boolean applyTags;
    private Set<Long> expectedSuggestedTagIds = Set.of();

    public Long getExpectedCandidateVersion() {
        return expectedCandidateVersion;
    }

    public void setExpectedCandidateVersion(Long expectedCandidateVersion) {
        this.expectedCandidateVersion = expectedCandidateVersion;
    }

    public boolean isApplyDescription() {
        return applyDescription;
    }

    public void setApplyDescription(boolean applyDescription) {
        this.applyDescription = applyDescription;
    }

    public String getExpectedSuggestedDescription() {
        return expectedSuggestedDescription;
    }

    public void setExpectedSuggestedDescription(String expectedSuggestedDescription) {
        this.expectedSuggestedDescription = expectedSuggestedDescription;
    }

    public boolean isApplyCategory() {
        return applyCategory;
    }

    public void setApplyCategory(boolean applyCategory) {
        this.applyCategory = applyCategory;
    }

    public Long getExpectedSuggestedCategoryId() {
        return expectedSuggestedCategoryId;
    }

    public void setExpectedSuggestedCategoryId(Long expectedSuggestedCategoryId) {
        this.expectedSuggestedCategoryId = expectedSuggestedCategoryId;
    }

    public boolean isApplyTags() {
        return applyTags;
    }

    public void setApplyTags(boolean applyTags) {
        this.applyTags = applyTags;
    }

    public Set<Long> getExpectedSuggestedTagIds() {
        return expectedSuggestedTagIds;
    }

    public void setExpectedSuggestedTagIds(Set<Long> expectedSuggestedTagIds) {
        this.expectedSuggestedTagIds = expectedSuggestedTagIds == null
            ? Set.of()
            : Set.copyOf(new LinkedHashSet<>(expectedSuggestedTagIds));
    }
}
