package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;

/**
 * The single owner-scoped dashboard preference. It selects either an owned personal dashboard or one immutable
 * official template. Older rows without either value remain the Financial Overview fallback.
 */
@Entity
@Table(
    name = "personal_dashboard_preference",
    uniqueConstraints = @UniqueConstraint(name = "ux_personal_dashboard_preference_user", columnNames = "user_id")
)
public class PersonalDashboardPreference implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Set only when the owner selected one of their personal dashboards. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_dashboard_id")
    private PersonalDashboard defaultDashboard;

    /** A stable, backend-governed identifier for an immutable official dashboard template. */
    @Column(name = "default_official_template_id", length = 64)
    private String defaultOfficialTemplateId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public PersonalDashboard getDefaultDashboard() {
        return defaultDashboard;
    }

    public void setDefaultDashboard(PersonalDashboard defaultDashboard) {
        this.defaultDashboard = defaultDashboard;
    }

    public String getDefaultOfficialTemplateId() {
        return defaultOfficialTemplateId;
    }

    public void setDefaultOfficialTemplateId(String defaultOfficialTemplateId) {
        this.defaultOfficialTemplateId = defaultOfficialTemplateId;
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
}
