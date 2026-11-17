package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

/** A normalized percentage configured for one Budget's optional threshold alerts. */
@Entity
@Table(
    name = "budget_notification_threshold",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_budget_notification_threshold",
        columnNames = { "preference_id", "normalized_percentage" }
    )
)
public class BudgetNotificationThreshold implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preference_id", nullable = false, updatable = false)
    private BudgetNotificationPreference preference;

    @NotNull
    @Column(name = "normalized_percentage", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal normalizedPercentage;

    protected BudgetNotificationThreshold() {}

    static BudgetNotificationThreshold create(BudgetNotificationPreference preference, BigDecimal normalizedPercentage) {
        BudgetNotificationThreshold threshold = new BudgetNotificationThreshold();
        threshold.preference = preference;
        threshold.normalizedPercentage = normalizedPercentage;
        return threshold;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getNormalizedPercentage() {
        return normalizedPercentage;
    }
}
