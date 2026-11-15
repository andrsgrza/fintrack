package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * User-owned dashboard aggregate. It persists only the default execution context and widget definitions, never
 * calculated financial results.
 */
@Entity
@Table(
    name = "personal_dashboard",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_personal_dashboard_owner_normalized_name",
        columnNames = { "user_id", "normalized_name" }
    ),
    indexes = @Index(name = "ix_personal_dashboard_owner_updated", columnList = "user_id,updated_at")
)
public class PersonalDashboard implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @NotNull
    @Size(min = 1, max = 120)
    @Column(name = "normalized_name", length = 120, nullable = false)
    private String normalizedName;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    /** Strict VisualizationContext JSON. The time remains relative when the user selected a relative period. */
    @NotNull
    @Column(name = "default_context_json", columnDefinition = "text", nullable = false)
    private String defaultContextJson;

    @NotNull
    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "dashboard", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC, id ASC")
    private List<PersonalDashboardWidget> widgets = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultContextJson() {
        return defaultContextJson;
    }

    public void setDefaultContextJson(String defaultContextJson) {
        this.defaultContextJson = defaultContextJson;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<PersonalDashboardWidget> getWidgets() {
        return widgets;
    }

    public void addWidget(PersonalDashboardWidget widget) {
        widgets.add(widget);
        widget.setDashboard(this);
    }

    public void removeWidget(PersonalDashboardWidget widget) {
        widgets.remove(widget);
        widget.setDashboard(null);
    }
}
