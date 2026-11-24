package com.fintrack.app.service.budget;

import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.repository.*;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable, bounded discovery of due Budget periods. It is deliberately Budget-specific: the cursor represents
 * lifecycle, revision, activation-epoch and time-zone semantics that do not belong to a generic job platform.
 */
@Service
public class BudgetPeriodCloseCursorService {

    private final BudgetPeriodCloseCursorRepository cursorRepository;
    private final BudgetPeriodCloseWorkItemRepository workRepository;
    private final BudgetNotificationPreferenceRepository preferenceRepository;
    private final BudgetRevisionScheduleRepository scheduleRepository;
    private final BudgetLifecycleEventRepository lifecycleEventRepository;
    private final BudgetTimeZoneService timeZoneService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final int discoveryBatchSize;

    public BudgetPeriodCloseCursorService(
        BudgetPeriodCloseCursorRepository cursorRepository,
        BudgetPeriodCloseWorkItemRepository workRepository,
        BudgetNotificationPreferenceRepository preferenceRepository,
        BudgetRevisionScheduleRepository scheduleRepository,
        BudgetLifecycleEventRepository lifecycleEventRepository,
        BudgetTimeZoneService timeZoneService,
        EntityManager entityManager,
        Clock clock,
        @Value("${application.budget-period-close.discovery-batch-size:50}") int discoveryBatchSize
    ) {
        this.cursorRepository = cursorRepository;
        this.workRepository = workRepository;
        this.preferenceRepository = preferenceRepository;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.timeZoneService = timeZoneService;
        this.entityManager = entityManager;
        this.clock = clock;
        if (discoveryBatchSize <= 0) throw new IllegalArgumentException("Budget period-close discovery batch size must be positive.");
        this.discoveryBatchSize = discoveryBatchSize;
    }

    /** Called from Budget creation, preference changes, and calendar/lifecycle changes in their enclosing transaction. */
    @Transactional
    public void synchronize(Budget budget) {
        if (budget == null || budget.getId() == null) return;
        BudgetNotificationPreference preference = preferenceRepository.findOneByBudgetId(budget.getId()).orElse(null);
        if (preference == null || !preference.isPeriodCloseEnabled() || preference.getPeriodCloseEnabledSince() == null) {
            cursorRepository.deleteByBudgetId(budget.getId());
            return;
        }
        ZoneId currentZone = timeZoneService.resolveCurrentZone(budget).orElse(null);
        if (currentZone == null) {
            // Capture never guesses UTC. A later durable time-zone assignment can explicitly synchronize again.
            cursorRepository.deleteByBudgetId(budget.getId());
            return;
        }
        BudgetTimeline timeline = timeline(budget);
        LocalDate start = cursorRepository
            .findOneByBudgetId(budget.getId())
            .map(BudgetPeriodCloseCursor::getNextPeriodStart)
            .orElseGet(() -> initialCandidateStart(timeline, currentZone));
        Candidate candidate = candidateAtOrAfter(budget, preference, timeline, start, currentZone);
        if (candidate == null) {
            cursorRepository.deleteByBudgetId(budget.getId());
            return;
        }
        Instant now = clock.instant();
        BudgetPeriodCloseCursor cursor = cursorRepository.findOneByBudgetId(budget.getId()).orElse(null);
        if (cursor == null) {
            cursorRepository.save(BudgetPeriodCloseCursor.schedule(budget, candidate.period().startInclusive(), candidate.dueAt(), now));
        } else {
            cursor.reschedule(candidate.period().startInclusive(), candidate.dueAt(), now);
        }
    }

    /** Returns the bounded due-cursor scan and the work items it durably created. */
    @Transactional
    public DiscoveryResult discoverDueWork() {
        Instant now = clock.instant();
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager
            .createNativeQuery(
                "select id from budget_period_close_cursor where next_due_at <= :now " +
                "order by next_due_at asc, budget_id asc limit :limit for update skip locked"
            )
            .setParameter("now", now)
            .setParameter("limit", discoveryBatchSize)
            .getResultList();
        int created = 0;
        for (Number id : ids) {
            if (discoverOne(id.longValue(), now)) created++;
        }
        return new DiscoveryResult(ids.size(), created);
    }

    private boolean discoverOne(Long cursorId, Instant now) {
        BudgetPeriodCloseCursor cursor = cursorRepository.findOneWithBudgetAndUser(cursorId).orElse(null);
        if (cursor == null) return false;
        Budget budget = cursor.getBudget();
        BudgetNotificationPreference preference = preferenceRepository.findOneByBudgetId(budget.getId()).orElse(null);
        if (preference == null || !preference.isPeriodCloseEnabled() || preference.getPeriodCloseEnabledSince() == null) {
            cursorRepository.delete(cursor);
            return false;
        }
        BudgetTimeline timeline = timeline(budget);
        ZoneId currentZone = timeZoneService.resolveCurrentZone(budget).orElse(null);
        if (currentZone == null) {
            cursorRepository.delete(cursor);
            return false;
        }
        Candidate candidate = candidateAtOrAfter(budget, preference, timeline, cursor.getNextPeriodStart(), currentZone);
        if (candidate == null) {
            cursorRepository.delete(cursor);
            return false;
        }
        if (candidate.dueAt().isAfter(now)) {
            cursor.reschedule(candidate.period().startInclusive(), candidate.dueAt(), now);
            return false;
        }
        if (!workRepository.existsByBudgetIdAndPeriodStart(budget.getId(), candidate.period().startInclusive())) {
            workRepository.save(
                BudgetPeriodCloseWorkItem.pending(
                    budget,
                    candidate.period().startInclusive(),
                    candidate.period().endExclusive(),
                    candidate.zone().getId(),
                    candidate.dueAt(),
                    now
                )
            );
        }
        Candidate next = candidateAtOrAfter(budget, preference, timeline, candidate.period().endExclusive(), currentZone);
        if (next == null) {
            cursorRepository.delete(cursor);
        } else {
            cursor.reschedule(next.period().startInclusive(), next.dueAt(), now);
        }
        return true;
    }

    private BudgetTimeline timeline(Budget budget) {
        return new BudgetTimeline(
            scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId()),
            lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId())
        );
    }

    private LocalDate initialCandidateStart(BudgetTimeline timeline, ZoneId currentZone) {
        LocalDate today = LocalDate.now(clock.withZone(currentZone));
        return timeline
            .periodContaining(today)
            .map(BudgetPeriod::startInclusive)
            .orElseGet(() -> timeline.periods(0, 0, today).get(0).period().startInclusive());
    }

    private Candidate candidateAtOrAfter(
        Budget budget,
        BudgetNotificationPreference preference,
        BudgetTimeline timeline,
        LocalDate requestedStart,
        ZoneId currentZone
    ) {
        if (requestedStart == null) return null;
        LocalDate today = LocalDate.now(clock.withZone(currentZone));
        LocalDate start = requestedStart;
        for (int searched = 0; searched < 10_000; searched++) {
            Optional<BudgetPeriod> resolved = timeline.periodContaining(start);
            if (resolved.isEmpty()) return null;
            BudgetPeriod period = resolved.orElseThrow();
            ZoneId periodZone = timeZoneService.resolveZoneForPeriod(budget, period.startInclusive()).orElse(null);
            if (periodZone == null) return null;
            Instant closedAt = period.endExclusive().atStartOfDay(periodZone).toInstant();
            BudgetTimeline.OperationalPeriod operational = timeline.periods(period.index(), period.index(), today).get(0);
            // COMPLETED and PAUSED are terminal for this discovery episode. SCHEDULED means the calendar has
            // moved beyond the current effective period; a future resume/calendar command will synchronize a
            // fresh cursor rather than making discovery scan artificial future periods.
            if (
                operational.status() == BudgetPeriodOperationalStatus.COMPLETED ||
                operational.status() == BudgetPeriodOperationalStatus.PAUSED ||
                operational.status() == BudgetPeriodOperationalStatus.SCHEDULED
            ) {
                return null;
            }
            if (operational.status() == BudgetPeriodOperationalStatus.ACTIVE && preference.permitsPeriodCloseCapture(closedAt)) {
                // A later preference/calendar update may synchronize a cursor after this period was
                // already captured (or deliberately cancelled). The immutable work-item identity is
                // authoritative: never schedule the same historical close a second time.
                if (!workRepository.existsByBudgetIdAndPeriodStart(budget.getId(), period.startInclusive())) {
                    return new Candidate(period, periodZone, closedAt);
                }
            }
            start = period.endExclusive();
        }
        throw new IllegalStateException("Budget close discovery exceeded the supported period search bound.");
    }

    private record Candidate(BudgetPeriod period, ZoneId zone, Instant dueAt) {}

    public record DiscoveryResult(int dueCursorsProcessed, int workItemsCreated) {}
}
