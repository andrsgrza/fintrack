package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Product-safe conflict response for a blocked FinancialAccount aggregate hard-delete command. */
public class FinancialAccountHardDeleteBlockedDTO implements Serializable {

    private final String code = "ACCOUNT_HARD_DELETE_BLOCKED";

    private List<FinancialAccountDeletionBlockerDTO> blockers = new ArrayList<>();

    public FinancialAccountHardDeleteBlockedDTO() {}

    public FinancialAccountHardDeleteBlockedDTO(List<FinancialAccountDeletionBlockerDTO> blockers) {
        setBlockers(blockers);
    }

    public String getCode() {
        return code;
    }

    public List<FinancialAccountDeletionBlockerDTO> getBlockers() {
        return blockers;
    }

    public void setBlockers(List<FinancialAccountDeletionBlockerDTO> blockers) {
        this.blockers = blockers == null ? new ArrayList<>() : new ArrayList<>(blockers);
    }
}
