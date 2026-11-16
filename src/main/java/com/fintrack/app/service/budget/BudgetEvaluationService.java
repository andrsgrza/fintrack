package com.fintrack.app.service.budget;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetLifecycleEvent;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.repository.BudgetLifecycleEventRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.analytics.SemanticAnalyticsExecutionService;
import com.fintrack.app.service.analytics.SemanticAnalyticsResult;
import com.fintrack.app.service.analytics.SemanticAnalyticsResultRow;
import com.fintrack.app.service.analytics.SemanticAnalyticsResultValue;
import com.fintrack.app.service.analytics.SemanticResultAvailability;
import com.fintrack.app.service.analytics.SemanticUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only Budget financial evaluation. Calendar/revision/lifecycle facts come exclusively from
 * {@link BudgetTimeline}; economic actuals come exclusively from the canonical Analytics execution pipeline.
 */
@Service
@Transactional(readOnly = true)
public class BudgetEvaluationService {

    static final int MAX_HISTORY_SIZE = 120;
    private static final int PERCENTAGE_SCALE = 2;

    private final BudgetRepository budgetRepository;
    private final BudgetRevisionScheduleRepository scheduleRepository;
    private final BudgetLifecycleEventRepository lifecycleEventRepository;
    private final CurrentUserService currentUserService;
    private final BudgetSemanticQueryCompiler compiler;
    private final SemanticAnalyticsExecutionService executionService;
    private final Clock clock;

    public BudgetEvaluationService(
        BudgetRepository budgetRepository,
        BudgetRevisionScheduleRepository scheduleRepository,
        BudgetLifecycleEventRepository lifecycleEventRepository,
        CurrentUserService currentUserService,
        BudgetSemanticQueryCompiler compiler,
        SemanticAnalyticsExecutionService executionService,
        Clock clock
    ) {
        this.budgetRepository = budgetRepository;
        this.scheduleRepository = scheduleRepository;
        this.lifecycleEventRepository = lifecycleEventRepository;
        this.currentUserService = currentUserService;
        this.compiler = compiler;
        this.executionService = executionService;
        this.clock = clock;
    }

    public BudgetEvaluationResult current(Long budgetId, String timeZone) {
        EvaluationContext context = context(budgetId, timeZone);
        BudgetPeriod period;
        try {
            period = context.timeline().currentPeriod(context.today());
        } catch (IllegalArgumentException exception) {
            throw invalidPeriod("The Budget is not scheduled on the requested local date.");
        }
        return evaluate(context, operational(context, period.index()));
    }

    public BudgetEvaluationResult period(Long budgetId, long periodIndex, String timeZone) {
        if (periodIndex < 0) throw invalidPeriod("A Budget period index must be zero or greater.");
        EvaluationContext context = context(budgetId, timeZone);
        return evaluate(context, operational(context, periodIndex));
    }

    public BudgetEvaluationResult atDate(Long budgetId, LocalDate referenceDate, String timeZone) {
        if (referenceDate == null) throw invalidPeriod("A reference date is required.");
        EvaluationContext context = context(budgetId, timeZone);
        BudgetPeriod period = context
            .timeline()
            .periodContaining(referenceDate)
            .orElseThrow(() -> invalidPeriod("The reference date is outside this Budget's scheduled lifetime."));
        return evaluate(context, operational(context, period.index()));
    }

    public BudgetEvaluationHistory history(Long budgetId, long firstPeriodIndex, int size, String timeZone) {
        if (firstPeriodIndex < 0 || size <= 0 || size > MAX_HISTORY_SIZE || firstPeriodIndex > Long.MAX_VALUE - size) {
            throw invalidRange("Budget evaluation history must request between 1 and " + MAX_HISTORY_SIZE + " periods from a valid index.");
        }
        EvaluationContext context = context(budgetId, timeZone);
        long last = firstPeriodIndex + size - 1L;
        List<BudgetTimeline.OperationalPeriod> periods;
        try {
            periods = context.timeline().periods(firstPeriodIndex, last, context.today());
        } catch (IllegalArgumentException exception) {
            throw invalidRange(exception.getMessage());
        }
        return new BudgetEvaluationHistory(firstPeriodIndex, size, periods.stream().map(period -> evaluate(context, period)).toList());
    }

    private BudgetEvaluationResult evaluate(EvaluationContext context, BudgetTimeline.OperationalPeriod operational) {
        Budget budget = context.budget();
        BudgetPeriod period = operational.period();
        BudgetEvaluationPeriodTiming timing = timing(period, context.today());
        BudgetRevision revision = operational.revision();
        BigDecimal target = revision.getTargetAmount();

        if (operational.status() == BudgetPeriodOperationalStatus.PAUSED) {
            return notEvaluated(
                budget,
                period,
                revision,
                operational.status(),
                timing,
                target,
                BudgetEvaluationStatus.NOT_EVALUATED,
                BudgetEvaluationFailure.PAUSED_PERIOD,
                "This Budget period is paused and has not been financially evaluated."
            );
        }
        if (operational.status() == BudgetPeriodOperationalStatus.COMPLETED) {
            return notEvaluated(
                budget,
                period,
                revision,
                operational.status(),
                timing,
                target,
                BudgetEvaluationStatus.NOT_EVALUATED,
                BudgetEvaluationFailure.COMPLETED_BUDGET,
                "This Budget is completed for this period and has no planned financial evaluation."
            );
        }
        if (operational.status() == BudgetPeriodOperationalStatus.SCHEDULED || timing == BudgetEvaluationPeriodTiming.FUTURE) {
            return notEvaluated(
                budget,
                period,
                revision,
                operational.status(),
                timing,
                target,
                BudgetEvaluationStatus.NOT_YET_EVALUATED,
                BudgetEvaluationFailure.FUTURE_PERIOD,
                "This Budget period is scheduled for the future and has not been financially evaluated."
            );
        }
        if (target == null || target.signum() <= 0) {
            return notEvaluated(
                budget,
                period,
                revision,
                operational.status(),
                timing,
                target,
                BudgetEvaluationStatus.INCOMPLETE_CONFIGURATION,
                BudgetEvaluationFailure.INCOMPLETE_CONFIGURATION,
                "The effective Budget target is incomplete."
            );
        }

        BudgetSemanticQueryCompilation compilation = compiler.compile(budget, revision, period, context.zone());
        if (compilation instanceof BudgetSemanticQueryCompilation.Failed failed) {
            return compilationFailure(budget, period, revision, operational.status(), timing, target, failed);
        }
        BudgetSemanticQueryCompilation.Compiled compiled = (BudgetSemanticQueryCompilation.Compiled) compilation;
        final SemanticAnalyticsResult result;
        try {
            result = executionService.executeForTrustedOwner(compiled.request(), compiled.trustedOwner());
        } catch (RuntimeException exception) {
            return notEvaluated(
                budget,
                period,
                revision,
                operational.status(),
                timing,
                target,
                BudgetEvaluationStatus.SEMANTIC_EXECUTION_FAILED,
                BudgetEvaluationFailure.SEMANTIC_EXECUTION_FAILED,
                "FINTRACK could not evaluate this Budget period through Analytics."
            );
        }
        return interpret(budget, period, revision, operational.status(), timing, target, result);
    }

    private BudgetEvaluationResult compilationFailure(
        Budget budget,
        BudgetPeriod period,
        BudgetRevision revision,
        BudgetPeriodOperationalStatus operationalStatus,
        BudgetEvaluationPeriodTiming timing,
        BigDecimal target,
        BudgetSemanticQueryCompilation.Failed failed
    ) {
        BudgetEvaluationFailure failure =
            switch (failed.reason()) {
                case INCOMPLETE_CONFIGURATION -> BudgetEvaluationFailure.INCOMPLETE_CONFIGURATION;
                case MISSING_OR_INACCESSIBLE_REFERENCE -> BudgetEvaluationFailure.MISSING_OR_INACCESSIBLE_REFERENCE;
                case UNSUPPORTED_SEMANTIC_FILTER_COMBINATION -> BudgetEvaluationFailure.UNSUPPORTED_SEMANTIC_FILTER_COMBINATION;
                case UNAUTHORIZED_OWNER_CONTEXT -> BudgetEvaluationFailure.UNAUTHORIZED_OWNER_CONTEXT;
                case INVALID_EFFECTIVE_PERIOD -> BudgetEvaluationFailure.INVALID_SEMANTIC_RESULT;
            };
        BudgetEvaluationStatus status =
            switch (failure) {
                case INCOMPLETE_CONFIGURATION, MISSING_OR_INACCESSIBLE_REFERENCE -> BudgetEvaluationStatus.INCOMPLETE_CONFIGURATION;
                default -> BudgetEvaluationStatus.SEMANTIC_CONTRACT_ERROR;
            };
        return notEvaluated(budget, period, revision, operationalStatus, timing, target, status, failure, failed.message());
    }

    private BudgetEvaluationResult interpret(
        Budget budget,
        BudgetPeriod period,
        BudgetRevision revision,
        BudgetPeriodOperationalStatus operationalStatus,
        BudgetEvaluationPeriodTiming timing,
        BigDecimal target,
        SemanticAnalyticsResult result
    ) {
        String expectedAlias = budget.getType() == BudgetType.EXPENSE ? "economic_expense" : "economic_income";
        if (result == null || result.rows().size() != 1) {
            return semanticContractFailure(
                budget,
                period,
                revision,
                operationalStatus,
                timing,
                target,
                result == null ? null : result.availability(),
                "Analytics did not return the one scalar monetary result required by a Budget."
            );
        }
        SemanticAnalyticsResultRow row = result.rows().get(0);
        if (!row.dimensions().isEmpty() || !budget.getCurrency().name().equals(row.currencyCode()) || row.values().size() != 1) {
            return semanticContractFailure(
                budget,
                period,
                revision,
                operationalStatus,
                timing,
                target,
                result.availability(),
                "Analytics returned an unexpected Budget result shape or currency."
            );
        }
        SemanticAnalyticsResultValue value = row.values().get(0);
        if (!expectedAlias.equals(value.alias()) || value.unit() != SemanticUnit.MONEY) {
            return semanticContractFailure(
                budget,
                period,
                revision,
                operationalStatus,
                timing,
                target,
                result.availability(),
                "Analytics returned an unexpected Budget calculation."
            );
        }
        if (
            result.availability() == SemanticResultAvailability.UNAVAILABLE ||
            result.availability() == SemanticResultAvailability.NOT_APPLICABLE ||
            result.availability() == SemanticResultAvailability.NO_DATA ||
            result.availability() == SemanticResultAvailability.PARTIAL ||
            value.availability() == SemanticResultAvailability.UNAVAILABLE ||
            value.availability() == SemanticResultAvailability.NOT_APPLICABLE ||
            value.availability() == SemanticResultAvailability.NO_DATA ||
            value.availability() == SemanticResultAvailability.PARTIAL ||
            value.value() == null
        ) {
            return new BudgetEvaluationResult(
                budget.getId(),
                budget.getName(),
                budget.getType(),
                budget.getCurrency(),
                period.index(),
                period.startInclusive(),
                period.endExclusive(),
                revision.getId(),
                target,
                operationalStatus,
                timing,
                false,
                null,
                null,
                null,
                BudgetGoalStatus.UNKNOWN,
                BudgetEvaluationStatus.SEMANTIC_UNAVAILABLE,
                value.availability(),
                BudgetEvaluationFailure.UNAVAILABLE_FINANCIAL_RESULT,
                "Analytics could not provide an authoritative financial result for this Budget period."
            );
        }
        BigDecimal actual = value.value();
        BigDecimal progress = actual.multiply(BigDecimal.valueOf(100)).divide(target, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
        BigDecimal remaining = target.subtract(actual);
        return new BudgetEvaluationResult(
            budget.getId(),
            budget.getName(),
            budget.getType(),
            budget.getCurrency(),
            period.index(),
            period.startInclusive(),
            period.endExclusive(),
            revision.getId(),
            target,
            operationalStatus,
            timing,
            true,
            actual,
            progress,
            remaining,
            goalStatus(budget.getType(), actual, target, timing),
            BudgetEvaluationStatus.EVALUATED,
            value.availability(),
            null,
            null
        );
    }

    private BudgetEvaluationResult semanticContractFailure(
        Budget budget,
        BudgetPeriod period,
        BudgetRevision revision,
        BudgetPeriodOperationalStatus operationalStatus,
        BudgetEvaluationPeriodTiming timing,
        BigDecimal target,
        SemanticResultAvailability availability,
        String message
    ) {
        return new BudgetEvaluationResult(
            budget.getId(),
            budget.getName(),
            budget.getType(),
            budget.getCurrency(),
            period.index(),
            period.startInclusive(),
            period.endExclusive(),
            revision.getId(),
            target,
            operationalStatus,
            timing,
            false,
            null,
            null,
            null,
            BudgetGoalStatus.UNKNOWN,
            BudgetEvaluationStatus.SEMANTIC_CONTRACT_ERROR,
            availability,
            BudgetEvaluationFailure.INVALID_SEMANTIC_RESULT,
            message
        );
    }

    private BudgetEvaluationResult notEvaluated(
        Budget budget,
        BudgetPeriod period,
        BudgetRevision revision,
        BudgetPeriodOperationalStatus operationalStatus,
        BudgetEvaluationPeriodTiming timing,
        BigDecimal target,
        BudgetEvaluationStatus status,
        BudgetEvaluationFailure failure,
        String message
    ) {
        return new BudgetEvaluationResult(
            budget.getId(),
            budget.getName(),
            budget.getType(),
            budget.getCurrency(),
            period.index(),
            period.startInclusive(),
            period.endExclusive(),
            revision.getId(),
            target,
            operationalStatus,
            timing,
            false,
            null,
            null,
            null,
            BudgetGoalStatus.NOT_EVALUATED,
            status,
            null,
            failure,
            message
        );
    }

    private BudgetGoalStatus goalStatus(BudgetType type, BigDecimal actual, BigDecimal target, BudgetEvaluationPeriodTiming timing) {
        int comparison = actual.compareTo(target);
        if (type == BudgetType.EXPENSE) {
            if (comparison < 0) {
                return timing == BudgetEvaluationPeriodTiming.HISTORICAL
                    ? BudgetGoalStatus.FINISHED_WITHIN_LIMIT
                    : BudgetGoalStatus.WITHIN_LIMIT;
            }
            return comparison == 0 ? BudgetGoalStatus.LIMIT_REACHED : BudgetGoalStatus.EXCEEDED;
        }
        if (comparison < 0) {
            return timing == BudgetEvaluationPeriodTiming.HISTORICAL ? BudgetGoalStatus.GOAL_NOT_MET : BudgetGoalStatus.GOAL_PENDING;
        }
        return comparison == 0 ? BudgetGoalStatus.GOAL_REACHED : BudgetGoalStatus.GOAL_EXCEEDED;
    }

    private BudgetTimeline.OperationalPeriod operational(EvaluationContext context, long periodIndex) {
        try {
            return context.timeline().periods(periodIndex, periodIndex, context.today()).get(0);
        } catch (IllegalArgumentException exception) {
            throw invalidPeriod(exception.getMessage());
        }
    }

    private EvaluationContext context(Long budgetId, String requestedTimeZone) {
        if (budgetId == null || budgetId <= 0) throw budgetNotFound();
        ZoneId zone = resolveZone(requestedTimeZone);
        Budget budget = budgetRepository
            .findOneForEvaluationByIdAndUserLogin(budgetId, currentUserService.getCurrentUserLogin())
            .orElseThrow(this::budgetNotFound);
        List<BudgetRevisionSchedule> schedules = scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
        List<BudgetLifecycleEvent> events = lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(budget.getId());
        try {
            return new EvaluationContext(budget, new BudgetTimeline(schedules, events), zone, LocalDate.now(clock.withZone(zone)));
        } catch (IllegalArgumentException exception) {
            throw invalidPeriod("The Budget's immutable schedule is invalid.");
        }
    }

    private ZoneId resolveZone(String requestedTimeZone) {
        try {
            if (requestedTimeZone == null || requestedTimeZone.isBlank()) throw invalidTimeZone();
            return ZoneId.of(requestedTimeZone.trim());
        } catch (DateTimeException exception) {
            throw invalidTimeZone();
        }
    }

    private BudgetEvaluationPeriodTiming timing(BudgetPeriod period, LocalDate today) {
        if (today.isBefore(period.startInclusive())) return BudgetEvaluationPeriodTiming.FUTURE;
        if (today.isBefore(period.endExclusive())) return BudgetEvaluationPeriodTiming.CURRENT;
        return BudgetEvaluationPeriodTiming.HISTORICAL;
    }

    private BudgetEvaluationException budgetNotFound() {
        return new BudgetEvaluationException(BudgetEvaluationException.Code.BUDGET_NOT_FOUND, "Budget not found.");
    }

    private BudgetEvaluationException invalidPeriod(String message) {
        return new BudgetEvaluationException(BudgetEvaluationException.Code.INVALID_PERIOD, message);
    }

    private BudgetEvaluationException invalidRange(String message) {
        return new BudgetEvaluationException(BudgetEvaluationException.Code.INVALID_RANGE, message);
    }

    private BudgetEvaluationException invalidTimeZone() {
        return new BudgetEvaluationException(
            BudgetEvaluationException.Code.INVALID_TIME_ZONE,
            "timeZone must be a valid IANA zone identifier."
        );
    }

    private record EvaluationContext(Budget budget, BudgetTimeline timeline, ZoneId zone, LocalDate today) {}
}
