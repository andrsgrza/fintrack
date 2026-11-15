package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.PersonalDashboardVisualizationType;
import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;

/** A persisted use of the canonical semantic query plus local presentation configuration. */
@Entity
@Table(
    name = "personal_dashboard_widget",
    uniqueConstraints = @UniqueConstraint(name = "ux_personal_dashboard_widget_position", columnNames = { "dashboard_id", "position" }),
    indexes = @Index(name = "ix_personal_dashboard_widget_dashboard", columnList = "dashboard_id,position")
)
public class PersonalDashboardWidget implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @Size(min = 1, max = 120)
    @Column(name = "title", length = 120, nullable = false)
    private String title;

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Column(name = "query_definition_json", columnDefinition = "text", nullable = false)
    private String queryDefinitionJson;

    @NotNull
    @Column(name = "query_schema_version", nullable = false)
    private Integer querySchemaVersion;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "visualization_type", nullable = false)
    private PersonalDashboardVisualizationType visualizationType;

    @NotNull
    @Column(name = "visualization_options_json", columnDefinition = "text", nullable = false)
    private String visualizationOptionsJson;

    @NotNull
    @Column(name = "position", nullable = false)
    private Integer position;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "width", nullable = false)
    private PersonalDashboardWidgetWidth width;

    @NotNull
    @Column(name = "visible", nullable = false)
    private Boolean visible;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dashboard_id", nullable = false)
    private PersonalDashboard dashboard;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getQueryDefinitionJson() {
        return queryDefinitionJson;
    }

    public void setQueryDefinitionJson(String queryDefinitionJson) {
        this.queryDefinitionJson = queryDefinitionJson;
    }

    public Integer getQuerySchemaVersion() {
        return querySchemaVersion;
    }

    public void setQuerySchemaVersion(Integer querySchemaVersion) {
        this.querySchemaVersion = querySchemaVersion;
    }

    public PersonalDashboardVisualizationType getVisualizationType() {
        return visualizationType;
    }

    public void setVisualizationType(PersonalDashboardVisualizationType visualizationType) {
        this.visualizationType = visualizationType;
    }

    public String getVisualizationOptionsJson() {
        return visualizationOptionsJson;
    }

    public void setVisualizationOptionsJson(String visualizationOptionsJson) {
        this.visualizationOptionsJson = visualizationOptionsJson;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public PersonalDashboardWidgetWidth getWidth() {
        return width;
    }

    public void setWidth(PersonalDashboardWidgetWidth width) {
        this.width = width;
    }

    public Boolean getVisible() {
        return visible;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
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

    public PersonalDashboard getDashboard() {
        return dashboard;
    }

    public void setDashboard(PersonalDashboard dashboard) {
        this.dashboard = dashboard;
    }
}
