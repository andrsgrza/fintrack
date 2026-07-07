package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.ApiTokenStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A hashed API credential owned by a user and restricted to explicitly
 * selected accounts.
 */
@Entity
@Table(name = "api_access_token")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ApiAccessToken implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @NotNull
    @Size(max = 20)
    @Column(name = "token_prefix", length = 20, nullable = false)
    private String tokenPrefix;

    @NotNull
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApiTokenStatus status;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(optional = false)
    @NotNull
    private User user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_api_access_token__accounts",
        joinColumns = @JoinColumn(name = "api_access_token_id"),
        inverseJoinColumns = @JoinColumn(name = "accounts_id")
    )
    @JsonIgnoreProperties(
        value = {
            "user", "creditAccountDetails", "financialTransactions", "subscriptions", "budgets", "transactionIngestions", "apiAccessTokens",
        },
        allowSetters = true
    )
    private Set<FinancialAccount> accounts = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "apiAccessToken")
    @JsonIgnoreProperties(value = { "transactionIngestion", "apiAccessToken" }, allowSetters = true)
    private Set<ApiIngestion> apiIngestions = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "apiAccessToken")
    @JsonIgnoreProperties(value = { "apiAccessToken" }, allowSetters = true)
    private Set<ApiAccessTokenPermission> permissions = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ApiAccessToken id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public ApiAccessToken name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTokenPrefix() {
        return this.tokenPrefix;
    }

    public ApiAccessToken tokenPrefix(String tokenPrefix) {
        this.setTokenPrefix(tokenPrefix);
        return this;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getTokenHash() {
        return this.tokenHash;
    }

    public ApiAccessToken tokenHash(String tokenHash) {
        this.setTokenHash(tokenHash);
        return this;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public ApiTokenStatus getStatus() {
        return this.status;
    }

    public ApiAccessToken status(ApiTokenStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(ApiTokenStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public ApiAccessToken createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return this.updatedAt;
    }

    public ApiAccessToken updatedAt(Instant updatedAt) {
        this.setUpdatedAt(updatedAt);
        return this;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastUsedAt() {
        return this.lastUsedAt;
    }

    public ApiAccessToken lastUsedAt(Instant lastUsedAt) {
        this.setLastUsedAt(lastUsedAt);
        return this;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public ApiAccessToken expiresAt(Instant expiresAt) {
        this.setExpiresAt(expiresAt);
        return this;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return this.revokedAt;
    }

    public ApiAccessToken revokedAt(Instant revokedAt) {
        this.setRevokedAt(revokedAt);
        return this;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public User getUser() {
        return this.user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ApiAccessToken user(User user) {
        this.setUser(user);
        return this;
    }

    public Set<FinancialAccount> getAccounts() {
        return this.accounts;
    }

    public void setAccounts(Set<FinancialAccount> financialAccounts) {
        this.accounts = financialAccounts;
    }

    public ApiAccessToken accounts(Set<FinancialAccount> financialAccounts) {
        this.setAccounts(financialAccounts);
        return this;
    }

    public ApiAccessToken addAccounts(FinancialAccount financialAccount) {
        this.accounts.add(financialAccount);
        return this;
    }

    public ApiAccessToken removeAccounts(FinancialAccount financialAccount) {
        this.accounts.remove(financialAccount);
        return this;
    }

    public Set<ApiIngestion> getApiIngestions() {
        return this.apiIngestions;
    }

    public void setApiIngestions(Set<ApiIngestion> apiIngestions) {
        if (this.apiIngestions != null) {
            this.apiIngestions.forEach(i -> i.setApiAccessToken(null));
        }
        if (apiIngestions != null) {
            apiIngestions.forEach(i -> i.setApiAccessToken(this));
        }
        this.apiIngestions = apiIngestions;
    }

    public ApiAccessToken apiIngestions(Set<ApiIngestion> apiIngestions) {
        this.setApiIngestions(apiIngestions);
        return this;
    }

    public ApiAccessToken addApiIngestions(ApiIngestion apiIngestion) {
        this.apiIngestions.add(apiIngestion);
        apiIngestion.setApiAccessToken(this);
        return this;
    }

    public ApiAccessToken removeApiIngestions(ApiIngestion apiIngestion) {
        this.apiIngestions.remove(apiIngestion);
        apiIngestion.setApiAccessToken(null);
        return this;
    }

    public Set<ApiAccessTokenPermission> getPermissions() {
        return this.permissions;
    }

    public void setPermissions(Set<ApiAccessTokenPermission> apiAccessTokenPermissions) {
        if (this.permissions != null) {
            this.permissions.forEach(i -> i.setApiAccessToken(null));
        }
        if (apiAccessTokenPermissions != null) {
            apiAccessTokenPermissions.forEach(i -> i.setApiAccessToken(this));
        }
        this.permissions = apiAccessTokenPermissions;
    }

    public ApiAccessToken permissions(Set<ApiAccessTokenPermission> apiAccessTokenPermissions) {
        this.setPermissions(apiAccessTokenPermissions);
        return this;
    }

    public ApiAccessToken addPermissions(ApiAccessTokenPermission apiAccessTokenPermission) {
        this.permissions.add(apiAccessTokenPermission);
        apiAccessTokenPermission.setApiAccessToken(this);
        return this;
    }

    public ApiAccessToken removePermissions(ApiAccessTokenPermission apiAccessTokenPermission) {
        this.permissions.remove(apiAccessTokenPermission);
        apiAccessTokenPermission.setApiAccessToken(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiAccessToken)) {
            return false;
        }
        return getId() != null && getId().equals(((ApiAccessToken) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ApiAccessToken{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", tokenPrefix='" + getTokenPrefix() + "'" +
            ", tokenHash='" + getTokenHash() + "'" +
            ", status='" + getStatus() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", lastUsedAt='" + getLastUsedAt() + "'" +
            ", expiresAt='" + getExpiresAt() + "'" +
            ", revokedAt='" + getRevokedAt() + "'" +
            "}";
    }
}
