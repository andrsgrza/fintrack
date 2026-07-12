package com.fintrack.app.service.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request DTO for creating an ApiAccessToken.
 *
 * Create is intentionally name-only; token secrets, owner, status and timestamps are server-owned.
 */
public class ApiAccessTokenCreateRequestDTO implements Serializable {

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        apiAccessTokenDTO.setName(name);
        return apiAccessTokenDTO;
    }
}
