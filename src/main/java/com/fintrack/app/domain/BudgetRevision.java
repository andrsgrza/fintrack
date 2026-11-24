package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.BudgetAccountScopeMode;
import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetCategoryScopeMode;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import com.fintrack.app.domain.enumeration.BudgetTagMatchMode;
import com.fintrack.app.domain.enumeration.BudgetTagScopeMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable configuration captured when a Budget revision is recorded. */
@Entity
@Table(
    name = "budget_revision",
    uniqueConstraints = @UniqueConstraint(name = "ux_budget_revision_number", columnNames = { "budget_id", "revision_number" })
)
public class BudgetRevision implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @NotNull
    @Column(name = "revision_number", nullable = false, updatable = false)
    private Integer revisionNumber;

    @NotNull
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @NotNull
    @Column(name = "target_amount", precision = 21, scale = 2, nullable = false, updatable = false)
    private BigDecimal targetAmount;

    @NotNull
    @Column(name = "interval_count", nullable = false, updatable = false)
    private Integer intervalCount;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "interval_unit", length = 20, nullable = false, updatable = false)
    private BudgetIntervalUnit intervalUnit;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "alignment", length = 20, nullable = false, updatable = false)
    private BudgetAlignment alignment;

    @NotNull
    @Column(name = "anchor_date", nullable = false, updatable = false)
    private LocalDate anchorDate;

    /** Original requested day for CUSTOM month, quarter, and year calendars. */
    @Column(name = "custom_day_of_month", updatable = false)
    private Integer customDayOfMonth;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "recurrence_mode", length = 20, nullable = false, updatable = false)
    private BudgetRecurrenceMode recurrenceMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "termination", length = 30, nullable = false, updatable = false)
    private BudgetRecurrenceTermination termination;

    @Column(name = "termination_period_count", updatable = false)
    private Integer terminationPeriodCount;

    /** Exclusive end boundary when termination is END_DATE. */
    @Column(name = "recurrence_end_date", updatable = false)
    private LocalDate recurrenceEndDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "account_scope_mode", length = 20, nullable = false, updatable = false)
    private BudgetAccountScopeMode accountScopeMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "category_scope_mode", length = 20, nullable = false, updatable = false)
    private BudgetCategoryScopeMode categoryScopeMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_scope_mode", length = 20, nullable = false, updatable = false)
    private BudgetTagScopeMode tagScopeMode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_match_mode", length = 10, nullable = false, updatable = false)
    private BudgetTagMatchMode tagMatchMode;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_budget_revision__accounts",
        joinColumns = @JoinColumn(name = "budget_revision_id"),
        inverseJoinColumns = @JoinColumn(name = "accounts_id")
    )
    private Set<FinancialAccount> accounts = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_budget_revision__categories",
        joinColumns = @JoinColumn(name = "budget_revision_id"),
        inverseJoinColumns = @JoinColumn(name = "categories_id")
    )
    private Set<Category> categories = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "rel_budget_revision__tags",
        joinColumns = @JoinColumn(name = "budget_revision_id"),
        inverseJoinColumns = @JoinColumn(name = "tags_id")
    )
    private Set<Tag> tags = new LinkedHashSet<>();

    protected BudgetRevision() {}

    public static BudgetRevision initial(
        BudgetRevisionConfiguration configuration,
        Set<FinancialAccount> accounts,
        Set<Category> categories,
        Set<Tag> tags,
        Instant recordedAt
    ) {
        return record(1, configuration, accounts, categories, tags, recordedAt);
    }

    public static BudgetRevision record(
        int revisionNumber,
        BudgetRevisionConfiguration configuration,
        Set<FinancialAccount> accounts,
        Set<Category> categories,
        Set<Tag> tags,
        Instant recordedAt
    ) {
        BudgetRevision revision = new BudgetRevision();
        revision.revisionNumber = revisionNumber;
        revision.recordedAt = recordedAt;
        revision.targetAmount = configuration.targetAmount();
        revision.intervalCount = configuration.intervalCount();
        revision.intervalUnit = configuration.intervalUnit();
        revision.alignment = configuration.alignment();
        revision.anchorDate = configuration.anchorDate();
        revision.customDayOfMonth = configuration.customDayOfMonth();
        revision.recurrenceMode = configuration.recurrenceMode();
        revision.termination = configuration.termination();
        revision.terminationPeriodCount = configuration.terminationPeriodCount();
        revision.recurrenceEndDate = configuration.recurrenceEndDate();
        revision.accountScopeMode = configuration.accountScopeMode();
        revision.categoryScopeMode = configuration.categoryScopeMode();
        revision.tagScopeMode = configuration.tagScopeMode();
        revision.tagMatchMode = configuration.tagMatchMode();
        revision.accounts = new LinkedHashSet<>(accounts);
        revision.categories = new LinkedHashSet<>(categories);
        revision.tags = new LinkedHashSet<>(tags);
        return revision;
    }

    void attachTo(Budget budget) {
        if (this.budget != null && this.budget != budget) throw new IllegalStateException(
            "A BudgetRevision cannot move to another Budget."
        );
        this.budget = budget;
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public Integer getIntervalCount() {
        return intervalCount;
    }

    public BudgetIntervalUnit getIntervalUnit() {
        return intervalUnit;
    }

    public BudgetAlignment getAlignment() {
        return alignment;
    }

    public LocalDate getAnchorDate() {
        return anchorDate;
    }

    public Integer getCustomDayOfMonth() {
        return customDayOfMonth;
    }

    public BudgetRecurrenceMode getRecurrenceMode() {
        return recurrenceMode;
    }

    public BudgetRecurrenceTermination getTermination() {
        return termination;
    }

    public Integer getTerminationPeriodCount() {
        return terminationPeriodCount;
    }

    public LocalDate getRecurrenceEndDate() {
        return recurrenceEndDate;
    }

    public BudgetAccountScopeMode getAccountScopeMode() {
        return accountScopeMode;
    }

    public BudgetCategoryScopeMode getCategoryScopeMode() {
        return categoryScopeMode;
    }

    public BudgetTagScopeMode getTagScopeMode() {
        return tagScopeMode;
    }

    public BudgetTagMatchMode getTagMatchMode() {
        return tagMatchMode;
    }

    public Set<FinancialAccount> getAccounts() {
        return Set.copyOf(accounts);
    }

    public Set<Category> getCategories() {
        return Set.copyOf(categories);
    }

    public Set<Tag> getTags() {
        return Set.copyOf(tags);
    }

    public BudgetCalendarDefinition calendarDefinition() {
        return new BudgetCalendarDefinition(
            intervalCount,
            intervalUnit,
            alignment,
            anchorDate,
            customDayOfMonth,
            recurrenceMode,
            termination,
            terminationPeriodCount,
            recurrenceEndDate
        );
    }
}
