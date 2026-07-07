package com.fintrack.app.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Lob;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.UserDashboardPreference} entity.
 */
@Schema(
    description = "Stores JSON preferences for the application's fixed dashboard.\n\nJHipster 8.11 generates this as ManyToOne to the built-in User entity.\nA unique database constraint on user_id will enforce one preference row\nper user after generation."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserDashboardPreferenceDTO implements Serializable {

    private Long id;

    @Lob
    private String configuration;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    @NotNull
    private UserDTO user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfiguration() {
        return configuration;
    }

    public void setConfiguration(String configuration) {
        this.configuration = configuration;
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
        if (!(o instanceof UserDashboardPreferenceDTO)) {
            return false;
        }

        UserDashboardPreferenceDTO userDashboardPreferenceDTO = (UserDashboardPreferenceDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, userDashboardPreferenceDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UserDashboardPreferenceDTO{" +
            "id=" + getId() +
            ", configuration='" + getConfiguration() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", user=" + getUser() +
            "}";
    }
}
