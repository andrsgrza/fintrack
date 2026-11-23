package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Optional, mutable delivery preferences. They never form part of an immutable Budget revision. */
@Entity
@Table(
    name = "budget_notification_preference",
    indexes = @Index(name = "ix_budget_notification_preference_budget", columnList = "budget_id")
)
public class BudgetNotificationPreference implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, unique = true, updatable = false)
    private Budget budget;

    @NotNull
    @Column(name = "percentage_alerts_enabled", nullable = false)
    private boolean percentageAlertsEnabled;

    @NotNull
    @Column(name = "period_close_enabled", nullable = false)
    private boolean periodCloseEnabled;

    /**
     * Server-authoritative start of the current uninterrupted period-close enablement episode. It is deliberately
     * independent from {@link #updatedAt}: changing thresholds must not make an earlier, still-eligible closing look
     * newly enabled. A null value means that period-close delivery is currently disabled.
     */
    @Column(name = "period_close_enabled_since")
    private Instant periodCloseEnabledSince;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "preference", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("normalizedPercentage ASC")
    private Set<BudgetNotificationThreshold> thresholds = new LinkedHashSet<>();

    protected BudgetNotificationPreference() {}

    public static BudgetNotificationPreference create(
        Budget budget,
        boolean percentageAlertsEnabled,
        boolean periodCloseEnabled,
        Instant now
    ) {
        BudgetNotificationPreference preference = new BudgetNotificationPreference();
        preference.budget = budget;
        preference.percentageAlertsEnabled = percentageAlertsEnabled;
        preference.periodCloseEnabled = periodCloseEnabled;
        preference.periodCloseEnabledSince = periodCloseEnabled ? now : null;
        preference.updatedAt = now;
        return preference;
    }

    public void replace(
        boolean percentageAlertsEnabled,
        boolean periodCloseEnabled,
        Set<java.math.BigDecimal> normalizedThresholds,
        Instant now
    ) {
        this.percentageAlertsEnabled = percentageAlertsEnabled;
        if (!this.periodCloseEnabled && periodCloseEnabled) {
            // A genuine false-to-true transition starts a new episode. Retrying an ON update or editing thresholds
            // while ON deliberately leaves the original epoch intact.
            this.periodCloseEnabledSince = now;
        } else if (this.periodCloseEnabled && !periodCloseEnabled) {
            // Immediate deactivation invalidates every uncaptured closing from this episode. A later enablement must
            // therefore receive a new timestamp and cannot resurrect an older boundary.
            this.periodCloseEnabledSince = null;
        }
        this.periodCloseEnabled = periodCloseEnabled;
        // A full replacement is a new configuration revision even when a deterministic test clock has not moved.
        // Keep the audit instant strictly monotonic, which also marks the owning entity dirty and advances @Version
        // when the only material changes are threshold child rows.
        this.updatedAt = now.isAfter(this.updatedAt) ? now : this.updatedAt.plusNanos(1);
        // Keep unchanged child rows. Clearing and recreating all rows makes Hibernate insert a value such as 50%
        // before its orphan removal DELETE is flushed, which violates the per-preference uniqueness constraint.
        Set<BigDecimal> requested = new HashSet<>(normalizedThresholds);
        thresholds.removeIf(threshold -> !requested.contains(threshold.getNormalizedPercentage()));
        requested
            .stream()
            .filter(value -> thresholds.stream().noneMatch(existing -> existing.getNormalizedPercentage().equals(value)))
            .sorted()
            .forEach(value -> thresholds.add(BudgetNotificationThreshold.create(this, value)));
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Budget getBudget() {
        return budget;
    }

    public boolean isPercentageAlertsEnabled() {
        return percentageAlertsEnabled;
    }

    public boolean isPeriodCloseEnabled() {
        return periodCloseEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPeriodCloseEnabledSince() {
        return periodCloseEnabledSince;
    }

    /**
     * Contract for the future period-close producer. It must additionally hold the documented owner → preference
     * mutation locks while making this decision and durably writing its snapshot/outbox record.
     */
    public boolean permitsPeriodCloseCapture(Instant logicalClosingBoundary) {
        return (
            periodCloseEnabled &&
            periodCloseEnabledSince != null &&
            logicalClosingBoundary != null &&
            !logicalClosingBoundary.isBefore(periodCloseEnabledSince)
        );
    }

    public Set<BudgetNotificationThreshold> getThresholds() {
        return Set.copyOf(thresholds);
    }
}
