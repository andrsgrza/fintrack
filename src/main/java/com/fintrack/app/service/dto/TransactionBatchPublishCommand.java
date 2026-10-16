package com.fintrack.app.service.dto;

/** Internal first-publication consent. The operation key is the bounded idempotency identity. */
public class TransactionBatchPublishCommand {

    private Long expectedBatchVersion;

    private String operationKey;

    public Long getExpectedBatchVersion() {
        return expectedBatchVersion;
    }

    public void setExpectedBatchVersion(Long expectedBatchVersion) {
        this.expectedBatchVersion = expectedBatchVersion;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }
}
