package com.fintrack.app.service.budget;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetLifecycleEvent;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.BudgetTimeZoneAssignment;
import com.fintrack.app.domain.User;
import com.fintrack.app.repository.BudgetLifecycleEventRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.BudgetTimeZoneAssignmentRepository;
import com.fintrack.app.repository.UserRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves a persistent IANA zone from immutable Budget-effective assignments. */
@Service
public class BudgetTimeZoneService {

    private final BudgetTimeZoneAssignmentRepository assignmentRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetRevisionScheduleRepository scheduleRepository;
    private final BudgetLifecycleEventRepository lifecycleEventRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public BudgetTimeZoneService(
        BudgetTimeZoneAssignmentRepository assignmentRepository,
        BudgetRepository budgetRepository,
        BudgetRevisionScheduleRepository scheduleRepository,
        BudgetLifecycleEventRepository lifecycleEventRepository,
        UserRepository userRepository,
        Clock clock
    ) {
        this.assignmentRepository = assignmentRepository;
        this.budgetRepository = budgetRepository;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Missing configuration is intentionally not guessed: optional alerts stay silent until a real zone exists. */
    @Transactional(readOnly = true)
    public Optional<ZoneId> resolveCurrentZone(Budget budget) {
        if (budget == null || budget.getId() == null) return Optional.empty();
        List<BudgetTimeZoneAssignment> assignments = assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(budget.getId());
        if (assignments.isEmpty()) return Optional.empty();
        ZoneId effective = zone(assignments.get(0).getTimeZone());
        if (effective == null) return Optional.empty();
        // Each next-period assignment is calculated in the zone that was in force immediately before it. Walk the
        // immutable history rather than comparing every assignment against the first zone; this stays correct after
        // more than one profile change near a day boundary.
        for (int index = 1; index < assignments.size(); index++) {
            BudgetTimeZoneAssignment next = assignments.get(index);
            if (next.getEffectivePeriodStart().isAfter(LocalDate.now(clock.withZone(effective)))) break;
            effective = zone(next.getTimeZone());
            if (effective == null) return Optional.empty();
        }
        return Optional.of(effective);
    }

    /**
     * Resolves the zone that was authoritative for one already-resolved Budget period. This deliberately does not
     * consult the user's current profile preference: changing that preference must never reinterpret history.
     */
    @Transactional(readOnly = true)
    public Optional<ZoneId> resolveZoneForPeriod(Budget budget, LocalDate periodStart) {
        if (budget == null || budget.getId() == null || periodStart == null) return Optional.empty();
        return assignmentRepository
            .findAllByBudgetIdOrderByEffectivePeriodStartAsc(budget.getId())
            .stream()
            .filter(assignment -> !assignment.getEffectivePeriodStart().isAfter(periodStart))
            .reduce((ignored, later) -> later)
            .map(assignment -> zone(assignment.getTimeZone()));
    }

    /**
     * Financial reads must not take a zone from the browser. A legacy Budget without an effective assignment falls
     * back only to its persisted profile zone, then to the server's explicit UTC compatibility fallback; notification
     * capture continues to require a durable assignment through {@link #resolveCurrentZone(Budget)}.
     */
    @Transactional(readOnly = true)
    public ZoneId resolveZoneForEvaluation(Budget budget, LocalDate periodStart) {
        return resolveZoneForPeriod(budget, periodStart).orElseGet(() -> profileZone(budget).orElse(ZoneId.of("UTC")));
    }

    @Transactional(readOnly = true)
    public ZoneId resolveCurrentZoneForEvaluation(Budget budget) {
        return resolveCurrentZone(budget).orElseGet(() -> profileZone(budget).orElse(ZoneId.of("UTC")));
    }

    @Transactional
    public void recordInitial(Budget budget, String timeZone, LocalDate effectivePeriodStart, Instant now) {
        ZoneId zone = requireZone(timeZone);
        assignmentRepository.save(BudgetTimeZoneAssignment.record(budget, zone.getId(), effectivePeriodStart, now));
    }

    /**
     * Changes the profile preference and plans only eligible future Budget boundaries. The owner lock is acquired
     * before any Budget state, which is the same mutation order used by Budget notification capture.
     */
    @Transactional
    public List<EffectiveChange> scheduleProfileTimeZoneChange(String login, String requestedTimeZone) {
        ZoneId nextZone = requireZone(requestedTimeZone);
        User owner = userRepository
            .findOneForBudgetNotificationMutationByLogin(login)
            .orElseThrow(() -> new IllegalArgumentException("Current user not found"));
        if (nextZone.getId().equals(owner.getTimeZone())) return List.of();
        List<Budget> budgets = budgetRepository.findAllForOverviewByUserLogin(login);
        Instant now = Instant.now(clock);
        List<EffectiveChange> changes = new ArrayList<>();
        for (Budget budget : budgets) {
            ZoneId currentZone = resolveCurrentZoneForEvaluation(budget);
            List<BudgetRevisionSchedule> schedules = scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
            List<BudgetLifecycleEvent> events = lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
            BudgetTimeline timeline = new BudgetTimeline(schedules, events);
            LocalDate today = LocalDate.now(clock.withZone(currentZone));
            Optional<BudgetPeriod> current = timeline.periodContaining(today);
            BudgetPeriod next;
            if (current.isEmpty()) {
                // The Budget has not started. Its initial, still-future period is the next applicable boundary.
                next = timeline.periods(0, 0, today).get(0).period();
            } else {
                BudgetPeriod currentPeriod = current.orElseThrow();
                if (timeline.isCompleteAt(currentPeriod.startInclusive(), currentPeriod.index())) continue;
                next = timeline.nextPeriod(today);
                if (timeline.isCompleteAt(next.startInclusive(), next.index())) continue;
            }
            LocalDate effective = next.startInclusive();
            ZoneId effectiveZone = resolveZoneForPeriod(budget, effective).orElse(currentZone);
            if (nextZone.equals(effectiveZone)) continue;

            List<BudgetTimeZoneAssignment> assignments = assignmentRepository.findAllByBudgetIdOrderByEffectivePeriodStartAsc(
                budget.getId()
            );
            Optional<BudgetTimeZoneAssignment> pending = assignments
                .stream()
                .filter(assignment -> assignment.getEffectivePeriodStart().equals(effective))
                .findFirst();
            if (pending.isPresent()) {
                // A not-yet-effective plan is superseded by the latest explicit profile choice. Historical assignments
                // are immutable and are never candidates because effective is strictly the current or next boundary.
                assignmentRepository.delete(pending.orElseThrow());
                assignmentRepository.flush();
            }
            assignmentRepository.save(BudgetTimeZoneAssignment.record(budget, nextZone.getId(), effective, now));
            changes.add(new EffectiveChange(budget.getId(), budget.getName(), effective));
        }
        owner.setTimeZone(nextZone.getId());
        return List.copyOf(changes);
    }

    private ZoneId requireZone(String value) {
        ZoneId zone = zone(value);
        if (zone == null) throw new IllegalArgumentException("timeZone must be a valid IANA zone identifier.");
        return zone;
    }

    private ZoneId zone(String value) {
        try {
            return value == null || value.isBlank() ? null : ZoneId.of(value.trim());
        } catch (DateTimeException exception) {
            return null;
        }
    }

    private Optional<ZoneId> profileZone(Budget budget) {
        return budget == null || budget.getUser() == null ? Optional.empty() : Optional.ofNullable(zone(budget.getUser().getTimeZone()));
    }

    public record EffectiveChange(Long budgetId, String budgetName, LocalDate effectivePeriodStart) {}
}
