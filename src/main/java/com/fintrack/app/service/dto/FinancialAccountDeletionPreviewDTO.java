package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only preview of a future permanent FinancialAccount deletion.
 *
 * <p>A successful preview is informational only. The future delete command must run the
 * same preflight again under its write lock before making any mutation.</p>
 */
public class FinancialAccountDeletionPreviewDTO implements Serializable {

    private Long accountId;
    private String accountName;
    private Boolean canHardDelete;
    private FinancialAccountDeletionPreviewCountsDTO counts;
    private List<FinancialAccountDeletionBlockerDTO> blockers = new ArrayList<>();

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public Boolean getCanHardDelete() {
        return canHardDelete;
    }

    public void setCanHardDelete(Boolean canHardDelete) {
        this.canHardDelete = canHardDelete;
    }

    public FinancialAccountDeletionPreviewCountsDTO getCounts() {
        return counts;
    }

    public void setCounts(FinancialAccountDeletionPreviewCountsDTO counts) {
        this.counts = counts;
    }

    public List<FinancialAccountDeletionBlockerDTO> getBlockers() {
        return blockers;
    }

    public void setBlockers(List<FinancialAccountDeletionBlockerDTO> blockers) {
        this.blockers = blockers == null ? new ArrayList<>() : new ArrayList<>(blockers);
    }
}
