package com.fintrack.app.service.dto;

/** Full replacement of the four OPEN-workspace rule settings. */
public class TransactionBatchSettingsCommand {

    private Long expectedBatchVersion;
    private Boolean autoApplyDescriptions;
    private Boolean autoApplyCategories;
    private Boolean autoApplyTags;
    private Boolean protectManualChanges;

    public Long getExpectedBatchVersion() {
        return expectedBatchVersion;
    }

    public void setExpectedBatchVersion(Long expectedBatchVersion) {
        this.expectedBatchVersion = expectedBatchVersion;
    }

    public Boolean getAutoApplyDescriptions() {
        return autoApplyDescriptions;
    }

    public void setAutoApplyDescriptions(Boolean autoApplyDescriptions) {
        this.autoApplyDescriptions = autoApplyDescriptions;
    }

    public Boolean getAutoApplyCategories() {
        return autoApplyCategories;
    }

    public void setAutoApplyCategories(Boolean autoApplyCategories) {
        this.autoApplyCategories = autoApplyCategories;
    }

    public Boolean getAutoApplyTags() {
        return autoApplyTags;
    }

    public void setAutoApplyTags(Boolean autoApplyTags) {
        this.autoApplyTags = autoApplyTags;
    }

    public Boolean getProtectManualChanges() {
        return protectManualChanges;
    }

    public void setProtectManualChanges(Boolean protectManualChanges) {
        this.protectManualChanges = protectManualChanges;
    }
}
