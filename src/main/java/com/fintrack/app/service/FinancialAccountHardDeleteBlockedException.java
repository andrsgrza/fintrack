package com.fintrack.app.service;

import com.fintrack.app.service.dto.FinancialAccountDeletionBlockerDTO;
import java.util.List;

/** Signals an expected domain block before an account hard-delete starts any cleanup. */
public class FinancialAccountHardDeleteBlockedException extends RuntimeException {

    private final List<FinancialAccountDeletionBlockerDTO> blockers;

    public FinancialAccountHardDeleteBlockedException(List<FinancialAccountDeletionBlockerDTO> blockers) {
        super("Financial account hard delete is blocked");
        this.blockers = List.copyOf(blockers);
    }

    public List<FinancialAccountDeletionBlockerDTO> getBlockers() {
        return blockers;
    }
}
