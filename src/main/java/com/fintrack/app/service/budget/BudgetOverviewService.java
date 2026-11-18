package com.fintrack.app.service.budget;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetLifecycleEvent;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.repository.BudgetLifecycleEventRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.service.CurrentUserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product-facing list orchestration. It only composes immutable schedules with BudgetEvaluationService; financial
 * actuals still originate exclusively in the canonical Analytics execution path.
 */
@Service
@Transactional(readOnly = true)
public class BudgetOverviewService {

    private final BudgetRepository budgetRepository;
    private final BudgetRevisionScheduleRepository scheduleRepository;
    private final BudgetLifecycleEventRepository lifecycleEventRepository;
    private final CurrentUserService currentUserService;
    private final BudgetEvaluationService evaluationService;
    private final BudgetTimeZoneService budgetTimeZoneService;
    private final Clock clock;

    public BudgetOverviewService(
        BudgetRepository budgetRepository,
        BudgetRevisionScheduleRepository scheduleRepository,
        BudgetLifecycleEventRepository lifecycleEventRepository,
        CurrentUserService currentUserService,
        BudgetEvaluationService evaluationService,
        BudgetTimeZoneService budgetTimeZoneService,
        Clock clock
    ) {
        this.budgetRepository = budgetRepository;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.currentUserService = currentUserService;
        this.evaluationService = evaluationService;
        this.budgetTimeZoneService = budgetTimeZoneService;
        this.clock = clock;
    }

    public BudgetOverview overview(
        LocalDate referenceDate,
        String ignoredClientTimeZone,
        BudgetType type,
        BudgetPeriodOperationalStatus operationalStatus
    ) {
        List<Budget> budgets = budgetRepository.findAllForOverviewByUserLogin(currentUserService.getCurrentUserLogin());
        if (budgets.isEmpty()) return new BudgetOverview(referenceDate, null, List.of());
        String profileTimeZone = budgets.get(0).getUser().getTimeZone();

        List<Long> ids = budgets.stream().map(Budget::getId).toList();
        Map<Long, List<BudgetRevisionSchedule>> schedules = scheduleRepository
            .findAllByBudgetIdInOrderByBudgetIdAndSequenceNumber(ids)
            .stream()
            .collect(Collectors.groupingBy(schedule -> schedule.getBudget().getId()));
        Map<Long, List<BudgetLifecycleEvent>> events = lifecycleEventRepository
            .findAllByBudgetIdInOrderByBudgetIdAndSequenceNumber(ids)
            .stream()
            .collect(Collectors.groupingBy(event -> event.getBudget().getId()));

        List<BudgetOverviewItem> items = budgets
            .stream()
            .filter(budget -> type == null || budget.getType() == type)
            .map(budget ->
                item(
                    budget,
                    schedules.getOrDefault(budget.getId(), List.of()),
                    events.getOrDefault(budget.getId(), List.of()),
                    effectiveReferenceDate(budget, referenceDate),
                    ignoredClientTimeZone
                )
            )
            .filter(item -> operationalStatus == null || item.operationalStatus() == operationalStatus)
            .toList();
        return new BudgetOverview(referenceDate, profileTimeZone, items);
    }

    /**
     * The unfiltered overview's current cards are not based on the browser date. Budgets may legitimately retain
     * different effective zones while a profile change awaits their next calendar boundaries.
     */
    private LocalDate effectiveReferenceDate(Budget budget, LocalDate requestedReferenceDate) {
        if (requestedReferenceDate != null) return requestedReferenceDate;
        ZoneId zone = budgetTimeZoneService.resolveCurrentZoneForEvaluation(budget);
        return LocalDate.now(clock.withZone(zone));
    }

    private BudgetOverviewItem item(
        Budget budget,
        List<BudgetRevisionSchedule> schedules,
        List<BudgetLifecycleEvent> events,
        LocalDate referenceDate,
        String ignoredClientTimeZone
    ) {
        final BudgetTimeline timeline;
        try {
            timeline = new BudgetTimeline(schedules, events);
        } catch (IllegalArgumentException exception) {
            return new BudgetOverviewItem(
                budget.getId(),
                budget.getName(),
                budget.getDescription(),
                budget.getType(),
                budget.getCurrency(),
                BudgetPeriodOperationalStatus.COMPLETED,
                false,
                "This Budget's immutable schedule is unavailable.",
                null
            );
        }
        Optional<BudgetPeriod> referencePeriod = timeline.periodContaining(referenceDate);
        // BudgetTimeline deliberately keeps an unbounded calendar so history can expose COMPLETED windows. The
        // overview must instead tell a person that a finite/finished Budget has no *scheduled* period on this date.
        if (
            referencePeriod.isEmpty() ||
            timeline.isCompleteAt(referencePeriod.orElseThrow().startInclusive(), referencePeriod.orElseThrow().index())
        ) {
            LocalDate firstStart = timeline.activeSchedules().get(0).getEffectivePeriodStart();
            boolean notStarted = referenceDate.isBefore(firstStart);
            return new BudgetOverviewItem(
                budget.getId(),
                budget.getName(),
                budget.getDescription(),
                budget.getType(),
                budget.getCurrency(),
                notStarted ? BudgetPeriodOperationalStatus.SCHEDULED : BudgetPeriodOperationalStatus.COMPLETED,
                false,
                notStarted
                    ? "This Budget has not started on the selected reference date."
                    : "This Budget has no scheduled period on the selected reference date.",
                null
            );
        }
        BudgetEvaluationResult evaluation = evaluationService.evaluateAuthorizedBudgetAt(
            budget,
            schedules,
            events,
            referenceDate,
            ignoredClientTimeZone
        );
        return new BudgetOverviewItem(
            budget.getId(),
            budget.getName(),
            budget.getDescription(),
            budget.getType(),
            budget.getCurrency(),
            evaluation.operationalStatus(),
            true,
            null,
            evaluation
        );
    }

    private static BudgetEvaluationException invalidPeriod(String message) {
        return new BudgetEvaluationException(BudgetEvaluationException.Code.INVALID_PERIOD, message);
    }
}
