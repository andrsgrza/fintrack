package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * Request metadata for an API ingestion.
 */
@Entity
@Table(name = "api_ingestion")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ApiIngestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "request_id", length = 100, nullable = false, unique = true)
    private String requestId;

    @Size(max = 150)
    @Column(name = "idempotency_key", length = 150)
    private String idempotencyKey;

    @Size(max = 100)
    @Column(name = "source_system", length = 100)
    private String sourceSystem;

    @NotNull
    @Size(max = 20)
    @Column(name = "api_version", length = 20, nullable = false)
    private String apiVersion;

    @NotNull
    @Size(max = 150)
    @Column(name = "endpoint", length = 150, nullable = false)
    private String endpoint;

    @Size(max = 150)
    @Column(name = "client_reference", length = 150)
    private String clientReference;

    @NotNull
    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JsonIgnoreProperties(value = { "accounts", "fileIngestion", "apiIngestion", "financialTransactions", "records" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    @JoinColumn(unique = true)
    private TransactionIngestion transactionIngestion;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "user", "apiIngestions", "permissions" }, allowSetters = true)
    private ApiAccessToken apiAccessToken;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ApiIngestion id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return this.requestId;
    }

    public ApiIngestion requestId(String requestId) {
        this.setRequestId(requestId);
        return this;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIdempotencyKey() {
        return this.idempotencyKey;
    }

    public ApiIngestion idempotencyKey(String idempotencyKey) {
        this.setIdempotencyKey(idempotencyKey);
        return this;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourceSystem() {
        return this.sourceSystem;
    }

    public ApiIngestion sourceSystem(String sourceSystem) {
        this.setSourceSystem(sourceSystem);
        return this;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getApiVersion() {
        return this.apiVersion;
    }

    public ApiIngestion apiVersion(String apiVersion) {
        this.setApiVersion(apiVersion);
        return this;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public ApiIngestion endpoint(String endpoint) {
        this.setEndpoint(endpoint);
        return this;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getClientReference() {
        return this.clientReference;
    }

    public ApiIngestion clientReference(String clientReference) {
        this.setClientReference(clientReference);
        return this;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }

    public Instant getReceivedAt() {
        return this.receivedAt;
    }

    public ApiIngestion receivedAt(Instant receivedAt) {
        this.setReceivedAt(receivedAt);
        return this;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public ApiIngestion createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public TransactionIngestion getTransactionIngestion() {
        return this.transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestion transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public ApiIngestion transactionIngestion(TransactionIngestion transactionIngestion) {
        this.setTransactionIngestion(transactionIngestion);
        return this;
    }

    public ApiAccessToken getApiAccessToken() {
        return this.apiAccessToken;
    }

    public void setApiAccessToken(ApiAccessToken apiAccessToken) {
        this.apiAccessToken = apiAccessToken;
    }

    public ApiIngestion apiAccessToken(ApiAccessToken apiAccessToken) {
        this.setApiAccessToken(apiAccessToken);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiIngestion)) {
            return false;
        }
        return getId() != null && getId().equals(((ApiIngestion) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ApiIngestion{" +
            "id=" + getId() +
            ", requestId='" + getRequestId() + "'" +
            ", idempotencyKey='" + getIdempotencyKey() + "'" +
            ", sourceSystem='" + getSourceSystem() + "'" +
            ", apiVersion='" + getApiVersion() + "'" +
            ", endpoint='" + getEndpoint() + "'" +
            ", clientReference='" + getClientReference() + "'" +
            ", receivedAt='" + getReceivedAt() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
