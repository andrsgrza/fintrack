package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.FinancialAccountDeletionBlockerCode;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** A compact, product-safe reason why a FinancialAccount cannot be hard-deleted yet. */
public class FinancialAccountDeletionBlockerDTO implements Serializable {

    private FinancialAccountDeletionBlockerCode code;

    private Long count;

    private List<Long> relatedIds = new ArrayList<>();

    public FinancialAccountDeletionBlockerCode getCode() {
        return code;
    }

    public void setCode(FinancialAccountDeletionBlockerCode code) {
        this.code = code;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public List<Long> getRelatedIds() {
        return relatedIds;
    }

    public void setRelatedIds(List<Long> relatedIds) {
        this.relatedIds = relatedIds == null ? new ArrayList<>() : new ArrayList<>(relatedIds);
    }
}
