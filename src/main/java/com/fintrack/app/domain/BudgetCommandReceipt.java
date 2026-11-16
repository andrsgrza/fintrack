package com.fintrack.app.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.Instant;

/** Durable, owner-scoped retry receipt for a Budget-2 command. */
@Entity
@Table(
    name = "budget_command_receipt",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_budget_command_receipt_key",
        columnNames = { "budget_id", "command_type", "client_request_id" }
    )
)
public class BudgetCommandReceipt implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_id", nullable = false, updatable = false)
    private Budget budget;

    @NotNull
    @Size(max = 32)
    @Column(name = "command_type", length = 32, nullable = false, updatable = false)
    private String commandType;

    @NotNull
    @Size(max = 100)
    @Column(name = "client_request_id", length = 100, nullable = false, updatable = false)
    private String clientRequestId;

    @NotNull
    @Size(max = 64)
    @Column(name = "request_digest", length = 64, nullable = false, updatable = false)
    private String requestDigest;

    @Column(name = "revision_schedule_id", updatable = false)
    private Long revisionScheduleId;

    @Column(name = "lifecycle_event_id", updatable = false)
    private Long lifecycleEventId;

    @NotNull
    @Column(name = "resulting_budget_version", nullable = false, updatable = false)
    private Long resultingBudgetVersion;

    @NotNull
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected BudgetCommandReceipt() {}

    public static BudgetCommandReceipt record(
        Budget budget,
        String commandType,
        String clientRequestId,
        String requestDigest,
        Long revisionScheduleId,
        Long lifecycleEventId,
        Long resultingBudgetVersion,
        Instant recordedAt
    ) {
        BudgetCommandReceipt receipt = new BudgetCommandReceipt();
        receipt.budget = budget;
        receipt.commandType = commandType;
        receipt.clientRequestId = clientRequestId;
        receipt.requestDigest = requestDigest;
        receipt.revisionScheduleId = revisionScheduleId;
        receipt.lifecycleEventId = lifecycleEventId;
        receipt.resultingBudgetVersion = resultingBudgetVersion;
        receipt.recordedAt = recordedAt;
        return receipt;
    }

    public Long getId() {
        return id;
    }

    public Budget getBudget() {
        return budget;
    }

    public String getCommandType() {
        return commandType;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public Long getRevisionScheduleId() {
        return revisionScheduleId;
    }

    public Long getLifecycleEventId() {
        return lifecycleEventId;
    }

    public Long getResultingBudgetVersion() {
        return resultingBudgetVersion;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
