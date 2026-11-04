package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;

/** Durable, bounded retry receipt. It stores no request body and deliberately has no financial-data relationship. */
@Entity
@Table(
    name = "personal_dashboard_request",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_personal_dashboard_request_owner_scope_key",
        columnNames = { "user_id", "scope", "client_request_id" }
    )
)
public class PersonalDashboardRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @Size(max = 32)
    @Column(name = "scope", length = 32, nullable = false)
    private String scope;

    @NotNull
    @Size(max = 100)
    @Column(name = "client_request_id", length = 100, nullable = false)
    private String clientRequestId;

    @NotNull
    @Size(max = 128)
    @Column(name = "request_digest", length = 128, nullable = false)
    private String requestDigest;

    /** IDs are immutable retry receipts, intentionally not foreign keys so dashboard deletion only cascades to widgets. */
    @Column(name = "dashboard_id")
    private Long dashboardId;

    @Column(name = "widget_id")
    private Long widgetId;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(String requestDigest) {
        this.requestDigest = requestDigest;
    }

    public Long getDashboardId() {
        return dashboardId;
    }

    public void setDashboardId(Long dashboardId) {
        this.dashboardId = dashboardId;
    }

    public Long getWidgetId() {
        return widgetId;
    }

    public void setWidgetId(Long widgetId) {
        this.widgetId = widgetId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
