package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
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
        this.periodCloseEnabled = periodCloseEnabled;
        this.updatedAt = now;
        thresholds.clear();
        normalizedThresholds.stream().sorted().forEach(value -> thresholds.add(BudgetNotificationThreshold.create(this, value)));
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

    public Set<BudgetNotificationThreshold> getThresholds() {
        return Set.copyOf(thresholds);
    }
}
