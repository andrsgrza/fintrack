package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.ApiPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.ApiAccessTokenPermission} entity.
 */
@Schema(description = "One permission granted to an API token.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ApiAccessTokenPermissionDTO implements Serializable {

    private Long id;

    @NotNull
    private ApiPermission permission;

    @NotNull
    private Instant createdAt;

    @NotNull
    private ApiAccessTokenDTO apiAccessToken;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ApiPermission getPermission() {
        return permission;
    }

    public void setPermission(ApiPermission permission) {
        this.permission = permission;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
        if (!(o instanceof ApiAccessTokenPermissionDTO)) {
            return false;
        }

        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO = (ApiAccessTokenPermissionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, apiAccessTokenPermissionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ApiAccessTokenPermissionDTO{" +
            "id=" + getId() +
            ", permission='" + getPermission() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", apiAccessToken=" + getApiAccessToken() +
            "}";
    }
}
