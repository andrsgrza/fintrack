package com.fintrack.app.service.budget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.BudgetNotificationType;
import com.fintrack.app.domain.enumeration.BudgetPeriodCloseWorkItemState;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.repository.*;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Captures one eligible period close atomically with its typed notification outbox evidence. */
@Service
public class BudgetPeriodCloseProcessor {

    private final BudgetPeriodCloseWorkClaimService claimService;
    private final BudgetPeriodCloseFailureRecorder failureRecorder;
    private final BudgetPeriodCloseWorkItemRepository workRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final BudgetNotificationPreferenceRepository preferenceRepository;
    private final BudgetRevisionScheduleRepository scheduleRepository;
    private final BudgetLifecycleEventRepository lifecycleEventRepository;
    private final BudgetTimeZoneService timeZoneService;
    private final BudgetEvaluationService evaluationService;
    private final BudgetNotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final BudgetPeriodCloseMetrics metrics;

    public BudgetPeriodCloseProcessor(
        BudgetPeriodCloseWorkClaimService claimService,
        BudgetPeriodCloseFailureRecorder failureRecorder,
        BudgetPeriodCloseWorkItemRepository workRepository,
        BudgetRepository budgetRepository,
        UserRepository userRepository,
        BudgetNotificationPreferenceRepository preferenceRepository,
        BudgetRevisionScheduleRepository scheduleRepository,
        BudgetLifecycleEventRepository lifecycleEventRepository,
        BudgetTimeZoneService timeZoneService,
        BudgetEvaluationService evaluationService,
        BudgetNotificationOutboxRepository outboxRepository,
        ObjectMapper objectMapper,
        Clock clock,
        TransactionTemplate transactionTemplate,
        BudgetPeriodCloseMetrics metrics
    ) {
        this.claimService = claimService;
        this.failureRecorder = failureRecorder;
        this.workRepository = workRepository;
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.timeZoneService = timeZoneService;
        this.evaluationService = evaluationService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metrics;
    }

    /** Bounded and invocable from isolated integration tests; each actual capture has its own transaction. */
    public int processDueWork() {
        List<Long> ids = claimService.claimDueWork();
        int captured = 0;
        for (Long id : ids) {
            Timer.Sample sample = metrics.startCapture();
            try {
                Boolean completed = transactionTemplate.execute(status -> captureInTransaction(id));
                if (Boolean.TRUE.equals(completed)) {
                    captured++;
                    metrics.captured();
                }
            } catch (RuntimeException exception) {
                metrics.failed();
                failureRecorder.recordRecoverableFailure(id, exception);
            } finally {
                metrics.stopCapture(sample);
            }
        }
        return captured;
    }

    @Transactional
    public boolean capture(Long workId) {
        return captureInTransaction(workId);
    }

    private boolean captureInTransaction(Long workId) {
        if (workId == null) return false;
        // Read identity without a lock only to establish the documented owner-first ordering below.
        BudgetPeriodCloseWorkItem initial = workRepository.findById(workId).orElse(null);
        if (initial == null || initial.getState() != BudgetPeriodCloseWorkItemState.PROCESSING) return false;
        String ownerLogin = initial.getBudget().getUser().getLogin();
        if (ownerLogin == null || ownerLogin.isBlank()) return false;

        User owner = userRepository.findOneForBudgetNotificationMutationByLogin(ownerLogin).orElse(null);
        if (owner == null) return false;
        Budget budget = budgetRepository.findOneForPeriodCloseCaptureByIdAndUserLogin(initial.getBudget().getId(), ownerLogin).orElse(null);
        if (budget == null) return false;
        BudgetNotificationPreference preference = preferenceRepository.findOneForOwnedMutation(budget.getId(), ownerLogin).orElse(null);
        BudgetPeriodCloseWorkItem work = workRepository.findOneForProcessing(workId).orElse(null);
        if (work == null || work.getState() != BudgetPeriodCloseWorkItemState.PROCESSING) return false;
        if (preference == null || !preference.permitsPeriodCloseCapture(work.getLogicalClosedAt())) {
            work.cancel("Period-close notifications are disabled or the activation epoch no longer permits this close.");
            metrics.cancelled();
            return false;
        }

        ZoneId periodZone = timeZoneService.resolveZoneForPeriod(budget, work.getPeriodStart()).orElse(null);
        if (periodZone == null || !periodZone.getId().equals(work.getPeriodTimeZone())) {
            work.block("The durable time-zone assignment for this Budget closing period is unavailable or inconsistent.");
            metrics.blocked();
            return false;
        }
        BudgetTimeline timeline = new BudgetTimeline(
            scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId()),
            lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId())
        );
        BudgetPeriod period = timeline.periodContaining(work.getPeriodStart()).orElse(null);
        if (period == null || !period.endExclusive().equals(work.getPeriodEndExclusive())) {
            work.block("The persisted Budget period is inconsistent with the immutable Budget calendar.");
            metrics.blocked();
            return false;
        }
        Instant logicalClosedAt = period.endExclusive().atStartOfDay(periodZone).toInstant();
        if (!logicalClosedAt.equals(work.getLogicalClosedAt())) {
            work.block("The persisted logical closing instant is inconsistent with the effective Budget time zone.");
            metrics.blocked();
            return false;
        }
        Instant now = clock.instant();
        if (logicalClosedAt.isAfter(now)) {
            work.retry(logicalClosedAt, "The logical Budget closing boundary has not arrived yet.");
            metrics.retried();
            return false;
        }
        BudgetTimeline.OperationalPeriod operational = timeline
            .periods(period.index(), period.index(), java.time.LocalDate.now(clock.withZone(periodZone)))
            .get(0);
        if (operational.status() != BudgetPeriodOperationalStatus.ACTIVE) {
            work.cancel("This Budget period is no longer active under its immutable lifecycle history.");
            metrics.cancelled();
            return false;
        }

        BudgetEvaluationResult evaluation = evaluationService.evaluateClosedPeriodForNotification(budget, period.index(), periodZone);
        if (
            evaluation.evaluationStatus() == BudgetEvaluationStatus.SEMANTIC_EXECUTION_FAILED ||
            evaluation.evaluationStatus() == BudgetEvaluationStatus.SEMANTIC_UNAVAILABLE
        ) {
            work.retry(retryAt(work.getAttemptCount(), now), "The authoritative financial result is temporarily unavailable.");
            metrics.retried();
            return false;
        }
        if (!evaluation.evaluationApplicable() || evaluation.actualAmount() == null || evaluation.progressPercentage() == null) {
            work.block("The Budget closing period has a permanently incomplete or invalid financial configuration.");
            metrics.blocked();
            return false;
        }

        String key = "PERIOD_CLOSED:" + budget.getId() + ":" + period.startInclusive();
        BudgetPeriodCloseNotificationSnapshot snapshot = new BudgetPeriodCloseNotificationSnapshot(
            budget.getId(),
            budget.getName(),
            budget.getType(),
            budget.getCurrency(),
            period.startInclusive(),
            period.endExclusive(),
            periodZone.getId(),
            evaluation.revisionId(),
            evaluation.targetAmount(),
            evaluation.actualAmount(),
            evaluation.progressPercentage(),
            evaluation.evaluationStatus(),
            logicalClosedAt,
            now,
            key
        );
        BudgetNotificationOutbox event = outboxRepository.save(
            BudgetNotificationOutbox.pending(owner, budget, BudgetNotificationType.PERIOD_CLOSED, key, serialize(snapshot), now)
        );
        work.capture(event, now);
        return true;
    }

    private Instant retryAt(int currentAttempts, Instant now) {
        long seconds = Math.min(21_600L, 1L << Math.min(14, currentAttempts + 1));
        return now.plusSeconds(seconds);
    }

    private String serialize(BudgetPeriodCloseNotificationSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Budget period-close snapshot could not be serialized.", exception);
        }
    }
}
