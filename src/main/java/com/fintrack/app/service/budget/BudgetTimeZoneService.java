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

    @Transactional
    public void recordInitial(Budget budget, String timeZone, LocalDate effectivePeriodStart, Instant now) {
        ZoneId zone = requireZone(timeZone);
        assignmentRepository.save(BudgetTimeZoneAssignment.record(budget, zone.getId(), effectivePeriodStart, now));
    }

    /** Backend foundation for the later profile UI: every Budget changes only at its next period boundary. */
    @Transactional
    public void scheduleProfileTimeZoneChange(String login, String requestedTimeZone) {
        ZoneId nextZone = requireZone(requestedTimeZone);
        User owner = userRepository
            .findOneForBudgetNotificationMutationByLogin(login)
            .orElseThrow(() -> new IllegalArgumentException("Current user not found"));
        List<Budget> budgets = budgetRepository.findAllForOverviewByUserLogin(login);
        Instant now = Instant.now(clock);
        for (Budget budget : budgets) {
            ZoneId currentZone = resolveCurrentZone(budget).orElse(null);
            if (currentZone == null) continue;
            List<BudgetRevisionSchedule> schedules = scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
            List<BudgetLifecycleEvent> events = lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
            BudgetTimeline timeline = new BudgetTimeline(schedules, events);
            LocalDate effective = timeline.nextPeriod(LocalDate.now(clock.withZone(currentZone))).startInclusive();
            if (
                assignmentRepository
                    .findAllByBudgetIdOrderByEffectivePeriodStartAsc(budget.getId())
                    .stream()
                    .anyMatch(a -> a.getEffectivePeriodStart().equals(effective))
            ) throw new IllegalArgumentException("A time-zone change is already scheduled for this Budget period.");
            assignmentRepository.save(BudgetTimeZoneAssignment.record(budget, nextZone.getId(), effective, now));
        }
        owner.setTimeZone(nextZone.getId());
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
}
