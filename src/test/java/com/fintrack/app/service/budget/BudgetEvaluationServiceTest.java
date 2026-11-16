package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetLifecycleEvent;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionConfiguration;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.BudgetLifecycleEventRepository;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.service.CurrentUserService;
import com.fintrack.app.service.analytics.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetEvaluationServiceTest {

    private static final String OWNER = "budget-owner";
    private static final String ZONE = "America/Mexico_City";
    // March 1st UTC is still February 28th for the explicitly selected budget-evaluation zone.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T00:30:00Z"), ZoneOffset.UTC);

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Mock
    private BudgetLifecycleEventRepository lifecycleEventRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private BudgetSemanticQueryCompiler compiler;

    @Mock
    private SemanticAnalyticsExecutionService executionService;

    private BudgetEvaluationService service;
    private Budget budget;
    private BudgetRevision revision;
    private BudgetRevisionSchedule schedule;

    @BeforeEach
    void setUp() {
        service = new BudgetEvaluationService(
            budgetRepository,
            scheduleRepository,
            lifecycleEventRepository,
            currentUserService,
            compiler,
            executionService,
            CLOCK
        );
        budget = budget(BudgetType.EXPENSE, "100.00");
        revision = budget.getRevisions().get(0);
        schedule = BudgetRevisionSchedule.scheduled(budget, revision, LocalDate.of(2026, 2, 1), 1, "INITIAL", Instant.EPOCH);
        ReflectionTestUtils.setField(schedule, "id", 30L);
        when(currentUserService.getCurrentUserLogin()).thenReturn(OWNER);
        when(budgetRepository.findOneForEvaluationByIdAndUserLogin(9L, OWNER)).thenReturn(Optional.of(budget));
        when(scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(9L)).thenReturn(List.of(schedule));
        when(lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(9L)).thenReturn(List.of());
        lenient()
            .when(compiler.compile(eq(budget), eq(revision), any(BudgetPeriod.class), any()))
            .thenAnswer(invocation -> compilation(invocation.getArgument(2)));
    }

    @Test
    void evaluates_expense_below_at_and_above_the_limit_with_signed_remaining_amount() {
        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("40.00", SemanticResultAvailability.COMPLETE));
        BudgetEvaluationResult below = service.current(9L, ZONE);

        assertThat(below.actualAmount()).isEqualByComparingTo("40.00");
        assertThat(below.progressPercentage()).isEqualByComparingTo("40.00");
        assertThat(below.remainingAmount()).isEqualByComparingTo("60.00");
        assertThat(below.goalStatus()).isEqualTo(BudgetGoalStatus.WITHIN_LIMIT);
        assertThat(below.periodTiming()).isEqualTo(BudgetEvaluationPeriodTiming.CURRENT);

        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("100.00", SemanticResultAvailability.COMPLETE));
        assertThat(service.current(9L, ZONE).goalStatus()).isEqualTo(BudgetGoalStatus.LIMIT_REACHED);

        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("125.00", SemanticResultAvailability.COMPLETE));
        BudgetEvaluationResult above = service.current(9L, ZONE);
        assertThat(above.progressPercentage()).isEqualByComparingTo("125.00");
        assertThat(above.remainingAmount()).isEqualByComparingTo("-25.00");
        assertThat(above.goalStatus()).isEqualTo(BudgetGoalStatus.EXCEEDED);
    }

    @Test
    void distinguishes_current_income_progress_from_a_closed_income_goal_that_was_not_met() {
        budget = budget(BudgetType.INCOME, "120.00", LocalDate.of(2026, 1, 1));
        revision = budget.getRevisions().get(0);
        schedule = BudgetRevisionSchedule.scheduled(budget, revision, LocalDate.of(2026, 1, 1), 1, "INITIAL", Instant.EPOCH);
        ReflectionTestUtils.setField(schedule, "id", 31L);
        when(budgetRepository.findOneForEvaluationByIdAndUserLogin(9L, OWNER)).thenReturn(Optional.of(budget));
        when(scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(9L)).thenReturn(List.of(schedule));
        when(compiler.compile(eq(budget), eq(revision), any(BudgetPeriod.class), any())).thenAnswer(invocation ->
            compilation(invocation.getArgument(2))
        );
        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("60.00", SemanticResultAvailability.COMPLETE));

        BudgetEvaluationResult historical = service.period(9L, 0, ZONE);
        BudgetEvaluationResult current = service.current(9L, ZONE);

        assertThat(historical.goalStatus()).isEqualTo(BudgetGoalStatus.GOAL_NOT_MET);
        assertThat(current.goalStatus()).isEqualTo(BudgetGoalStatus.GOAL_PENDING);
        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("120.00", SemanticResultAvailability.COMPLETE));
        assertThat(service.current(9L, ZONE).goalStatus()).isEqualTo(BudgetGoalStatus.GOAL_REACHED);
        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("140.00", SemanticResultAvailability.COMPLETE));
        assertThat(service.current(9L, ZONE).goalStatus()).isEqualTo(BudgetGoalStatus.GOAL_EXCEEDED);
    }

    @Test
    void preserves_a_known_economic_zero_and_rounds_progress_without_inventing_data() {
        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("0.00", SemanticResultAvailability.ZERO));

        BudgetEvaluationResult result = service.current(9L, ZONE);

        assertThat(result.actualAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.progressPercentage()).isEqualByComparingTo("0.00");
        assertThat(result.semanticAvailability()).isEqualTo(SemanticResultAvailability.ZERO);
        assertThat(result.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.EVALUATED);
    }

    @Test
    void returns_scheduled_and_paused_periods_without_calling_analytics() {
        BudgetEvaluationResult future = service.period(9L, 1, ZONE);
        assertThat(future.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.NOT_YET_EVALUATED);
        assertThat(future.actualAmount()).isNull();
        assertThat(future.failureReason()).isEqualTo(BudgetEvaluationFailure.FUTURE_PERIOD);
        verifyNoInteractions(executionService);

        BudgetLifecycleEvent pause = BudgetLifecycleEvent.record(
            budget,
            BudgetLifecycleEventType.PAUSE_REQUESTED,
            LocalDate.of(2026, 2, 1),
            1,
            "pause",
            Instant.EPOCH,
            null
        );
        ReflectionTestUtils.setField(pause, "id", 40L);
        when(lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(9L)).thenReturn(List.of(pause));

        BudgetEvaluationResult paused = service.current(9L, ZONE);
        assertThat(paused.operationalStatus()).isEqualTo(BudgetPeriodOperationalStatus.PAUSED);
        assertThat(paused.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.NOT_EVALUATED);
        assertThat(paused.failureReason()).isEqualTo(BudgetEvaluationFailure.PAUSED_PERIOD);
        verifyNoInteractions(executionService);
    }

    @Test
    void does_not_turn_incomplete_or_unavailable_semantics_into_zero() {
        when(compiler.compile(eq(budget), eq(revision), any(BudgetPeriod.class), any())).thenReturn(
            new BudgetSemanticQueryCompilation.Failed(
                BudgetSemanticCompilationFailure.INCOMPLETE_CONFIGURATION,
                "Selected Budget accounts are no longer available."
            )
        );

        BudgetEvaluationResult incomplete = service.current(9L, ZONE);
        assertThat(incomplete.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.INCOMPLETE_CONFIGURATION);
        assertThat(incomplete.actualAmount()).isNull();

        when(compiler.compile(eq(budget), eq(revision), any(BudgetPeriod.class), any())).thenAnswer(invocation ->
            compilation(invocation.getArgument(2))
        );
        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result(null, SemanticResultAvailability.UNAVAILABLE));
        BudgetEvaluationResult unavailable = service.current(9L, ZONE);
        assertThat(unavailable.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.SEMANTIC_UNAVAILABLE);
        assertThat(unavailable.actualAmount()).isNull();
        assertThat(unavailable.failureReason()).isEqualTo(BudgetEvaluationFailure.UNAVAILABLE_FINANCIAL_RESULT);
    }

    @Test
    void uses_the_requested_iana_zone_to_resolve_today_and_caps_the_current_period_through_that_date() {
        when(executionService.executeForTrustedOwner(any(), any())).thenReturn(result("10.00", SemanticResultAvailability.COMPLETE));

        service.current(9L, ZONE);

        ArgumentCaptor<BudgetPeriod> period = ArgumentCaptor.forClass(BudgetPeriod.class);
        verify(compiler).compile(eq(budget), eq(revision), period.capture(), eq(java.time.ZoneId.of(ZONE)));
        // The fixed instant is Feb 28 in Mexico City. March must remain a future period.
        assertThat(period.getValue().startInclusive()).isEqualTo(LocalDate.of(2026, 2, 1));
    }

    private Budget budget(BudgetType type, String target) {
        return budget(type, target, LocalDate.of(2026, 2, 1));
    }

    private Budget budget(BudgetType type, String target, LocalDate anchorDate) {
        User owner = new User();
        owner.setId(7L);
        owner.setLogin(OWNER);
        Budget value = Budget.create(owner, "Budget", null, type, CurrencyCode.MXN, "creation", "hash", Instant.EPOCH);
        ReflectionTestUtils.setField(value, "id", 9L);
        BudgetRevision initial = BudgetRevision.initial(
            new BudgetRevisionConfiguration(
                new BigDecimal(target),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                anchorDate,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.ALL,
                BudgetCategoryScopeMode.ALL,
                BudgetTagScopeMode.NONE,
                BudgetTagMatchMode.ANY
            ),
            Set.of(),
            Set.of(),
            Set.of(),
            Instant.EPOCH
        );
        ReflectionTestUtils.setField(initial, "id", 20L);
        value.addInitialRevision(initial);
        return value;
    }

    private BudgetSemanticQueryCompilation.Compiled compilation(BudgetPeriod period) {
        return new BudgetSemanticQueryCompilation.Compiled(
            null,
            null,
            new SemanticAnalyticsExecutionRequest(1, null, null),
            SemanticAnalyticsTrustedOwnerContext.forBudget(budget, period.endExclusive().minusDays(1))
        );
    }

    private SemanticAnalyticsResult result(String amount, SemanticResultAvailability availability) {
        String alias = budget.getType() == BudgetType.EXPENSE ? "economic_expense" : "economic_income";
        BigDecimal value = amount == null ? null : new BigDecimal(amount);
        return new SemanticAnalyticsResult(
            1,
            "financial-semantic-v1",
            null,
            Instant.EPOCH,
            availability == SemanticResultAvailability.UNAVAILABLE
                ? SemanticResultAvailability.UNAVAILABLE
                : SemanticResultAvailability.COMPLETE,
            List.of(),
            List.of(
                new SemanticAnalyticsResultRow(
                    List.of(),
                    "MXN",
                    List.of(
                        new SemanticAnalyticsResultValue(
                            alias,
                            value,
                            SemanticUnit.MONEY,
                            availability,
                            availability == SemanticResultAvailability.ZERO
                        )
                    )
                )
            ),
            List.of()
        );
    }
}
