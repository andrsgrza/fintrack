package com.fintrack.app.service.budget;

import com.fintrack.app.domain.BudgetCalendarDefinition;
import com.fintrack.app.domain.BudgetLifecycleEvent;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.enumeration.BudgetLifecycleEventType;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import com.fintrack.app.domain.enumeration.BudgetRevisionScheduleOperation;
import java.time.LocalDate;
import java.util.*;

/**
 * Read-only, deterministic projection of immutable scheduling and lifecycle history. It owns neither persistence nor
 * financial evaluation. The effective date belongs to schedules alone; a BudgetRevision is only a configuration
 * snapshot.
 */
public final class BudgetTimeline {

    private static final int MAX_SEARCHED_PERIODS = 100_000;

    private final List<BudgetRevisionSchedule> activeSchedules;
    private final List<BudgetLifecycleEvent> events;
    private final BudgetRevisionSchedule initialSchedule;

    public BudgetTimeline(List<BudgetRevisionSchedule> schedules, List<BudgetLifecycleEvent> events) {
        this.activeSchedules = activeSchedules(schedules);
        this.events = events == null ? List.of() : events.stream().sorted(eventOrder()).toList();
        this.initialSchedule = this.activeSchedules.stream()
            .min(scheduleOrder())
            .orElseThrow(() -> invalid("Budget is missing its initial schedule."));
        if (initialSchedule.getRevision() == null) throw invalid("Initial Budget schedule is missing its revision.");
    }

    public ResolvedRevision resolveEffectiveRevision(LocalDate periodStart) {
        if (periodStart == null) throw invalid("A period start is required.");
        return activeSchedules
            .stream()
            .filter(schedule -> !schedule.getEffectivePeriodStart().isAfter(periodStart))
            .max(scheduleOrder())
            .map(schedule -> new ResolvedRevision(schedule.getId(), schedule.getRevision(), schedule.getEffectivePeriodStart()))
            .orElseThrow(() -> invalid("No Budget revision is effective before the requested period."));
    }

    public Optional<BudgetPeriod> periodContaining(LocalDate date) {
        if (date == null || date.isBefore(initialSchedule.getEffectivePeriodStart())) return Optional.empty();
        LocalDate start = initialSchedule.getEffectivePeriodStart();
        for (int index = 0; index < MAX_SEARCHED_PERIODS; index++) {
            BudgetRevision revision = resolveEffectiveRevision(start).revision();
            BudgetPeriod period = containingBoundary(revision, start, index);
            if (date.isBefore(period.startInclusive())) return Optional.empty();
            if (date.isBefore(period.endExclusive())) return Optional.of(period);
            start = period.endExclusive();
        }
        throw invalid("Budget calendar has too many periods to resolve safely.");
    }

    public List<OperationalPeriod> periods(long firstIndex, long lastIndex, LocalDate today) {
        if (firstIndex < 0 || lastIndex < firstIndex || lastIndex - firstIndex >= MAX_SEARCHED_PERIODS) {
            throw invalid("Requested Budget period range is invalid or too large.");
        }
        BudgetPeriod current = periodContaining(today).orElse(null);
        List<OperationalPeriod> result = new ArrayList<>();
        LocalDate start = initialSchedule.getEffectivePeriodStart();
        for (long index = 0; index <= lastIndex; index++) {
            BudgetRevision revision = resolveEffectiveRevision(start).revision();
            BudgetPeriod period = containingBoundary(revision, start, index);
            if (index >= firstIndex) result.add(new OperationalPeriod(period, revision, operationalStatus(period, index, current)));
            start = period.endExclusive();
        }
        return List.copyOf(result);
    }

    public BudgetPeriod currentPeriod(LocalDate today) {
        return periodContaining(today).orElseThrow(() -> invalid("The Budget is not scheduled on the requested local date."));
    }

    public BudgetPeriod nextPeriod(LocalDate today) {
        BudgetPeriod current = currentPeriod(today);
        return periodContaining(current.endExclusive()).orElseThrow(() -> invalid("The Budget has no next period."));
    }

    public boolean isCompleteAt(LocalDate periodStart, long index) {
        if (automaticCompletionAt(index, periodStart)) return true;
        return effectiveLifecycleEvents()
            .stream()
            .anyMatch(
                event ->
                    event.getEventType() == BudgetLifecycleEventType.FINISH_REQUESTED &&
                    !event.getEffectivePeriodStart().isAfter(periodStart)
            );
    }

    public List<BudgetRevisionSchedule> activeSchedules() {
        return activeSchedules;
    }

    public List<BudgetLifecycleEvent> events() {
        return events;
    }

    private BudgetPeriod containingBoundary(BudgetRevision revision, LocalDate expectedStart, long index) {
        // Completion determines operational availability, not the underlying calendar boundaries. Keeping the
        // calendar unbounded here lets a read of the first period after a finite recurrence report COMPLETED
        // instead of failing to materialize a boundary at all.
        BudgetPeriodCalculator calculator = new BudgetPeriodCalculator(calendarWithoutTermination(revision));
        BudgetPeriod local = calculator
            .periodContaining(expectedStart)
            .orElseThrow(() -> invalid("A scheduled revision does not begin on one of its own complete calendar boundaries."));
        if (!local.startInclusive().equals(expectedStart)) {
            throw invalid("A scheduled revision would create a partial Budget period.");
        }
        return new BudgetPeriod(index, local.startInclusive(), local.endExclusive());
    }

    private static BudgetCalendarDefinition calendarWithoutTermination(BudgetRevision revision) {
        return new BudgetCalendarDefinition(
            revision.getIntervalCount(),
            revision.getIntervalUnit(),
            revision.getAlignment(),
            revision.getAnchorDate(),
            revision.getCustomDayOfMonth(),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null
        );
    }

    private BudgetPeriodOperationalStatus operationalStatus(BudgetPeriod period, long index, BudgetPeriod current) {
        if (isCompleteAt(period.startInclusive(), index)) return BudgetPeriodOperationalStatus.COMPLETED;
        boolean paused = false;
        for (BudgetLifecycleEvent event : effectiveLifecycleEvents()) {
            if (event.getEffectivePeriodStart().isAfter(period.startInclusive())) break;
            if (event.getEventType() == BudgetLifecycleEventType.PAUSE_REQUESTED) paused = true;
            if (event.getEventType() == BudgetLifecycleEventType.RESUME_REQUESTED) paused = false;
        }
        if (paused) return BudgetPeriodOperationalStatus.PAUSED;
        if (current == null || period.startInclusive().isAfter(current.startInclusive())) return BudgetPeriodOperationalStatus.SCHEDULED;
        return BudgetPeriodOperationalStatus.ACTIVE;
    }

    private boolean automaticCompletionAt(long index, LocalDate periodStart) {
        BudgetRevision initial = initialSchedule.getRevision();
        if (initial.getRecurrenceMode() == BudgetRecurrenceMode.ONCE) return index >= 1;
        return switch (initial.getTermination()) {
            case NONE -> false;
            case AFTER_N_PERIODS -> index >= initial.getTerminationPeriodCount();
            case END_DATE -> !periodStart.isBefore(initial.getRecurrenceEndDate());
        };
    }

    private List<BudgetLifecycleEvent> effectiveLifecycleEvents() {
        Set<Long> cancelledPauseIds = new HashSet<>();
        for (BudgetLifecycleEvent event : events) {
            if (event.getEventType() == BudgetLifecycleEventType.PAUSE_CANCELLED && event.getTargetEvent() != null) {
                cancelledPauseIds.add(event.getTargetEvent().getId());
            }
        }
        return events
            .stream()
            .filter(event -> event.getEventType() != BudgetLifecycleEventType.PAUSE_CANCELLED)
            .filter(event -> event.getEventType() != BudgetLifecycleEventType.PAUSE_REQUESTED || !cancelledPauseIds.contains(event.getId()))
            .toList();
    }

    private static List<BudgetRevisionSchedule> activeSchedules(List<BudgetRevisionSchedule> schedules) {
        if (schedules == null) throw invalid("Budget schedules are required.");
        Set<Long> cancelled = new HashSet<>();
        for (BudgetRevisionSchedule schedule : schedules) {
            if (schedule.getOperation() == BudgetRevisionScheduleOperation.CANCELLED && schedule.getTargetSchedule() != null) {
                cancelled.add(schedule.getTargetSchedule().getId());
            }
        }
        List<BudgetRevisionSchedule> active = schedules
            .stream()
            .filter(schedule -> schedule.getOperation() == BudgetRevisionScheduleOperation.SCHEDULED)
            .filter(schedule -> !cancelled.contains(schedule.getId()))
            .sorted(scheduleOrder())
            .toList();
        if (active.stream().anyMatch(schedule -> schedule.getRevision() == null)) throw invalid(
            "A scheduled revision is missing its configuration."
        );
        return active;
    }

    private static Comparator<BudgetRevisionSchedule> scheduleOrder() {
        return Comparator.comparing(BudgetRevisionSchedule::getEffectivePeriodStart).thenComparing(
            BudgetRevisionSchedule::getSequenceNumber
        );
    }

    private static Comparator<BudgetLifecycleEvent> eventOrder() {
        return Comparator.comparing(BudgetLifecycleEvent::getEffectivePeriodStart).thenComparing(BudgetLifecycleEvent::getSequenceNumber);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public record ResolvedRevision(Long scheduleId, BudgetRevision revision, LocalDate effectivePeriodStart) {}

    public record OperationalPeriod(BudgetPeriod period, BudgetRevision revision, BudgetPeriodOperationalStatus status) {
        public boolean evaluationEligible() {
            return status == BudgetPeriodOperationalStatus.ACTIVE;
        }
    }
}
