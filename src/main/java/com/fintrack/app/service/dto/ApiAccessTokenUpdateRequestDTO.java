package com.fintrack.app.service.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fintrack.app.domain.enumeration.ApiTokenStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request DTO for replacing editable ApiAccessToken fields.
 *
 * Secrets, owner and audit timestamps are intentionally absent from this contract.
 */
public class ApiAccessTokenUpdateRequestDTO implements Serializable {

    @NotNull
    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    private ApiTokenStatus status;

    private Instant expiresAt;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

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

    public ApiTokenStatus getStatus() {
        return status;
    }

    public void setStatus(ApiTokenStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @JsonAnySetter
    public void setUnknownField(String name, Object value) {
        unknownFields.put(name, value);
    }

    @AssertTrue(message = "Unknown fields are not allowed")
    @JsonIgnore
    public boolean isUnknownFieldsEmpty() {
        return unknownFields.isEmpty();
    }

    public ApiAccessTokenDTO toApiAccessTokenDTO() {
        ApiAccessTokenDTO apiAccessTokenDTO = new ApiAccessTokenDTO();
        apiAccessTokenDTO.setId(id);
        apiAccessTokenDTO.setName(name);
        apiAccessTokenDTO.setStatus(status);
        apiAccessTokenDTO.setExpiresAt(expiresAt);
        return apiAccessTokenDTO;
    }
}
