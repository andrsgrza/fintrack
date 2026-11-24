package com.fintrack.app.domain;

import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Owner-scoped budget identity. Financial scope and calendar settings deliberately live in immutable
 * {@link BudgetRevision} snapshots rather than on this aggregate root.
 */
@Entity
@Table(
    name = "budget",
    uniqueConstraints = @UniqueConstraint(name = "ux_budget_owner_request", columnNames = { "user_id", "creation_request_id" }),
    indexes = @Index(name = "ix_budget_owner_updated", columnList = "user_id,updated_at")
)
public class Budget implements Serializable {

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

    @Size(max = 500)
    @Column(name = "description", length = 500)
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "budget_type", length = 20, nullable = false, updatable = false)
    private BudgetType type;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "currency", length = 10, nullable = false, updatable = false)
    private CurrencyCode currency;

    /** Bounded opaque key used only to replay a creation command safely. */
    @NotNull
    @Size(min = 1, max = 100)
    @Column(name = "creation_request_id", length = 100, nullable = false, updatable = false)
    private String creationRequestId;

    @NotNull
    @Size(min = 1, max = 64)
    @Column(name = "creation_request_hash", length = 64, nullable = false, updatable = false)
    private String creationRequestHash;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User user;

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("revisionNumber ASC")
    private List<BudgetRevision> revisions = new ArrayList<>();

    protected Budget() {}

    public static Budget create(
        User owner,
        String name,
        String description,
        BudgetType type,
        CurrencyCode currency,
        String creationRequestId,
        String creationRequestHash,
        Instant now
    ) {
        Budget budget = new Budget();
        budget.user = owner;
        budget.name = name;
        budget.description = description;
        budget.type = type;
        budget.currency = currency;
        budget.creationRequestId = creationRequestId;
        budget.creationRequestHash = creationRequestHash;
        budget.createdAt = now;
        budget.updatedAt = now;
        return budget;
    }

    public void addInitialRevision(BudgetRevision revision) {
        addRevision(revision);
    }

    /** Revision snapshots are append-only. Effective dates are held by BudgetRevisionSchedule, not this aggregate. */
    public void addRevision(BudgetRevision revision) {
        if (
            revision == null || revisions.stream().anyMatch(existing -> existing.getRevisionNumber().equals(revision.getRevisionNumber()))
        ) {
            throw new IllegalStateException("A Budget revision number may be recorded only once.");
        }
        revisions.add(revision);
        revision.attachTo(this);
    }

    public void updateMetadata(String name, String description, Instant now) {
        this.name = name;
        this.description = description;
        touch(now);
    }

    /** Makes the aggregate the optimistic-lock authority for all separately persisted history rows. */
    public void touch(Instant now) {
        if (now == null) throw new IllegalArgumentException("Budget update time is required.");
        // A command may be processed by a fixed test clock or within the same database timestamp resolution.
        // Keep a monotonic aggregate field so Hibernate always emits the versioned root update that protects
        // separately persisted history rows.
        this.updatedAt = !now.isAfter(updatedAt) ? updatedAt.plusNanos(1) : now;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BudgetType getType() {
        return type;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public String getCreationRequestId() {
        return creationRequestId;
    }

    public String getCreationRequestHash() {
        return creationRequestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public User getUser() {
        return user;
    }

    public List<BudgetRevision> getRevisions() {
        return List.copyOf(revisions);
    }
}
