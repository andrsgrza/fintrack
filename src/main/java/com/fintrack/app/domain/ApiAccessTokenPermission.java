package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.ApiPermission;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;

/**
 * One permission granted to an API token.
 */
@Entity
@Table(name = "api_access_token_permission")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ApiAccessTokenPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private ApiPermission permission;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "user", "permissions" }, allowSetters = true)
    private ApiAccessToken apiAccessToken;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public ApiAccessTokenPermission id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ApiPermission getPermission() {
        return this.permission;
    }

    public ApiAccessTokenPermission permission(ApiPermission permission) {
        this.setPermission(permission);
        return this;
    }

    public void setPermission(ApiPermission permission) {
        this.permission = permission;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public ApiAccessTokenPermission createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ApiAccessToken getApiAccessToken() {
        return this.apiAccessToken;
    }

    public void setApiAccessToken(ApiAccessToken apiAccessToken) {
        this.apiAccessToken = apiAccessToken;
    }

    public ApiAccessTokenPermission apiAccessToken(ApiAccessToken apiAccessToken) {
        this.setApiAccessToken(apiAccessToken);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiAccessTokenPermission)) {
            return false;
        }
        return getId() != null && getId().equals(((ApiAccessTokenPermission) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ApiAccessTokenPermission{" +
            "id=" + getId() +
            ", permission='" + getPermission() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
