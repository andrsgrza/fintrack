package com.fintrack.app.service.dto;

import jakarta.validation.constraints.NotNull;

/** Deliberate, minimal command for opening a new batch against one owned account. */
public class TransactionBatchCreateCommand {

    @NotNull
    private Long accountId;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }
}
