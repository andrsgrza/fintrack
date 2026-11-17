package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Permanent threshold deduplication evidence. Claims are intentionally not removed when preferences change. */
@Entity
@Table(
    name = "budget_threshold_claim",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_budget_threshold_claim",
        columnNames = { "budget_id", "period_start", "normalized_percentage" }
    ),
    indexes = @Index(name = "ix_budget_threshold_claim_budget_period", columnList = "budget_id,period_start")
)
public class BudgetThresholdClaim implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @NotNull
    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @NotNull
    @Column(name = "normalized_percentage", precision = 19, scale = 4, nullable = false, updatable = false)
    private BigDecimal normalizedPercentage;

    @NotNull
    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    @NotNull
    @Column(name = "source_mutation_key", length = 200, nullable = false, updatable = false)
    private String sourceMutationKey;

    protected BudgetThresholdClaim() {}

    public static BudgetThresholdClaim create(
        Budget budget,
        LocalDate periodStart,
        BigDecimal normalizedPercentage,
        Instant claimedAt,
        String sourceMutationKey
    ) {
        BudgetThresholdClaim claim = new BudgetThresholdClaim();
        claim.budget = budget;
        claim.periodStart = periodStart;
        claim.normalizedPercentage = normalizedPercentage;
        claim.claimedAt = claimedAt;
        claim.sourceMutationKey = sourceMutationKey;
        return claim;
    }

    public Long getId() {
        return id;
    }
}
