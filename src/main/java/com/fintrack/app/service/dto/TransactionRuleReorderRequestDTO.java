package com.fintrack.app.service.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Request DTO for manually reordering TransactionRule rows.
 */
public class TransactionRuleReorderRequestDTO implements Serializable {

    private List<Long> orderedIds;

    public List<Long> getOrderedIds() {
        return orderedIds;
    }

    public void setOrderedIds(List<Long> orderedIds) {
        this.orderedIds = orderedIds;
    }
}
