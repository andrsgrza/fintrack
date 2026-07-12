package com.fintrack.app.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * Create-only request DTO for ApiIngestion.
 *
 * ApiAccessToken is not a persisted ApiIngestion relationship. The token id is
 * accepted only at create time so the service can copy immutable snapshot fields.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ApiIngestionCreateRequestDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    private String requestId;

    @Size(max = 150)
    private String idempotencyKey;

    @Size(max = 100)
    private String sourceSystem;

    @NotNull
    @Size(max = 20)
    private String apiVersion;

    @NotNull
    @Size(max = 150)
    private String endpoint;

    @Size(max = 150)
    private String clientReference;

    private Instant receivedAt;

    private Instant createdAt;

    @NotNull
    private TransactionIngestionDTO transactionIngestion;

    @NotNull
    private Long apiAccessTokenId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public TransactionIngestionDTO getTransactionIngestion() {
        return transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestionDTO transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public Long getApiAccessTokenId() {
        return apiAccessTokenId;
    }

    public void setApiAccessTokenId(Long apiAccessTokenId) {
        this.apiAccessTokenId = apiAccessTokenId;
    }
}
