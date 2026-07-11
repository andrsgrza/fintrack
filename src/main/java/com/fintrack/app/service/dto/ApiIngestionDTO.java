package com.fintrack.app.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.ApiIngestion} entity.
 */
@Schema(description = "Request metadata for an API ingestion.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ApiIngestionDTO implements Serializable {

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

    private Long apiTokenIdSnapshot;

    @Size(max = 20)
    private String apiTokenPrefixSnapshot;

    @Size(max = 100)
    private String apiTokenNameSnapshot;

    @NotNull
    private TransactionIngestionDTO transactionIngestion;

    /**
     * Create-only: used to resolve an accessible token and copy snapshot fields. Not persisted as FK.
     */
    private ApiAccessTokenDTO apiAccessToken;

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

    public Long getApiTokenIdSnapshot() {
        return apiTokenIdSnapshot;
    }

    public void setApiTokenIdSnapshot(Long apiTokenIdSnapshot) {
        this.apiTokenIdSnapshot = apiTokenIdSnapshot;
    }

    public String getApiTokenPrefixSnapshot() {
        return apiTokenPrefixSnapshot;
    }

    public void setApiTokenPrefixSnapshot(String apiTokenPrefixSnapshot) {
        this.apiTokenPrefixSnapshot = apiTokenPrefixSnapshot;
    }

    public String getApiTokenNameSnapshot() {
        return apiTokenNameSnapshot;
    }

    public void setApiTokenNameSnapshot(String apiTokenNameSnapshot) {
        this.apiTokenNameSnapshot = apiTokenNameSnapshot;
    }

    public TransactionIngestionDTO getTransactionIngestion() {
        return transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestionDTO transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public ApiAccessTokenDTO getApiAccessToken() {
        return apiAccessToken;
    }

    public void setApiAccessToken(ApiAccessTokenDTO apiAccessToken) {
        this.apiAccessToken = apiAccessToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiIngestionDTO)) {
            return false;
        }

        ApiIngestionDTO apiIngestionDTO = (ApiIngestionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, apiIngestionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ApiIngestionDTO{" +
            "id=" + getId() +
            ", requestId='" + getRequestId() + "'" +
            ", idempotencyKey='" + getIdempotencyKey() + "'" +
            ", sourceSystem='" + getSourceSystem() + "'" +
            ", apiVersion='" + getApiVersion() + "'" +
            ", endpoint='" + getEndpoint() + "'" +
            ", clientReference='" + getClientReference() + "'" +
            ", receivedAt='" + getReceivedAt() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", apiTokenIdSnapshot=" + getApiTokenIdSnapshot() +
            ", apiTokenPrefixSnapshot='" + getApiTokenPrefixSnapshot() + "'" +
            ", apiTokenNameSnapshot='" + getApiTokenNameSnapshot() + "'" +
            ", transactionIngestion=" + getTransactionIngestion() +
            ", apiAccessToken=" + getApiAccessToken() +
            "}";
    }
}
