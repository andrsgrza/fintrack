package com.fintrack.app.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fintrack.app.domain.enumeration.ApiTokenStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.ApiAccessToken} entity.
 */
@Schema(description = "A hashed API credential owned by a user and restricted to explicitly\nselected accounts.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ApiAccessTokenDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    @NotNull
    @Size(max = 20)
    private String tokenPrefix;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tokenHash;

    @NotNull
    private ApiTokenStatus status;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    private Instant lastUsedAt;

    private Instant expiresAt;

    private Instant revokedAt;

    private UserDTO user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public ApiTokenStatus getStatus() {
        return status;
    }

    public void setStatus(ApiTokenStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiAccessTokenDTO)) {
            return false;
        }

        ApiAccessTokenDTO apiAccessTokenDTO = (ApiAccessTokenDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, apiAccessTokenDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ApiAccessTokenDTO{" +
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
            ", user=" + getUser() +
            "}";
    }
}
