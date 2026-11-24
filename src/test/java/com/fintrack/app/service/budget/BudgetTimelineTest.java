package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BudgetTimelineTest {

    private static final Instant RECORDED = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void resolvesByEffectiveBoundaryRatherThanRecordingOrderAndDoesNotResurrectCancelledSchedules() {
        Budget budget = budget();
        BudgetRevision r1 = revision(1, "4000.00", LocalDate.of(2026, 11, 1));
        BudgetRevision r2 = revision(2, "5000.00", LocalDate.of(2026, 11, 1));
        BudgetRevision r3 = revision(3, "6000.00", LocalDate.of(2026, 11, 1));
        schedule(budget, r1, LocalDate.of(2026, 11, 1), 1, 101L);
        BudgetRevisionSchedule r2Schedule = schedule(budget, r2, LocalDate.of(2026, 11, 29), 2, 102L);
        BudgetRevisionSchedule r3Schedule = schedule(budget, r3, LocalDate.of(2026, 11, 15), 3, 103L);

        BudgetTimeline timeline = new BudgetTimeline(
            List.of(schedule(budget, r1, LocalDate.of(2026, 11, 1), 1, 101L), r2Schedule, r3Schedule),
            List.of()
        );
        assertThat(timeline.resolveEffectiveRevision(LocalDate.of(2026, 11, 1)).revision().getTargetAmount()).isEqualByComparingTo(
            "4000.00"
        );
        assertThat(timeline.resolveEffectiveRevision(LocalDate.of(2026, 11, 15)).revision().getTargetAmount()).isEqualByComparingTo(
            "6000.00"
        );
        assertThat(timeline.resolveEffectiveRevision(LocalDate.of(2026, 11, 29)).revision().getTargetAmount()).isEqualByComparingTo(
            "5000.00"
        );

        BudgetRevisionSchedule cancellation = BudgetRevisionSchedule.cancelled(budget, r2Schedule, 4, "cancel-r2", RECORDED);
        id(cancellation, 104L);
        BudgetTimeline afterCancellation = new BudgetTimeline(
            List.of(schedule(budget, r1, LocalDate.of(2026, 11, 1), 1, 101L), r2Schedule, r3Schedule, cancellation),
            List.of()
        );
        assertThat(afterCancellation.resolveEffectiveRevision(LocalDate.of(2026, 12, 1)).revision().getTargetAmount()).isEqualByComparingTo(
            "6000.00"
        );
    }

    @Test
    void derivesPauseResumeAndCompletionWithoutPersistingACompetingStatus() {
        Budget budget = budget();
        BudgetRevision revision = revision(1, "4000.00", LocalDate.of(2026, 11, 1));
        BudgetRevisionSchedule initial = schedule(budget, revision, LocalDate.of(2026, 11, 1), 1, 101L);
        BudgetLifecycleEvent pause = event(budget, BudgetLifecycleEventType.PAUSE_REQUESTED, LocalDate.of(2026, 12, 1), 1, 201L, null);
        BudgetLifecycleEvent resume = event(budget, BudgetLifecycleEventType.RESUME_REQUESTED, LocalDate.of(2027, 1, 1), 2, 202L, null);
        BudgetLifecycleEvent finish = event(budget, BudgetLifecycleEventType.FINISH_REQUESTED, LocalDate.of(2027, 2, 1), 3, 203L, null);

        BudgetTimeline timeline = new BudgetTimeline(List.of(initial), List.of(pause, resume, finish));
        assertThat(timeline.periods(0, 3, LocalDate.of(2026, 11, 15)))
            .extracting(period -> period.status())
            .containsExactly(
                BudgetPeriodOperationalStatus.ACTIVE,
                BudgetPeriodOperationalStatus.PAUSED,
                BudgetPeriodOperationalStatus.SCHEDULED,
                BudgetPeriodOperationalStatus.COMPLETED
            );
        assertThat(timeline.periods(1, 1, LocalDate.of(2026, 11, 15)).get(0).evaluationEligible()).isFalse();
    }

    @Test
    void rejectsACalendarRevisionThatWouldStartInTheMiddleOfItsOwnPeriod() {
        Budget budget = budget();
        BudgetRevision initial = revision(1, "4000.00", LocalDate.of(2026, 11, 1));
        BudgetRevision incompatible = revision(2, "5000.00", LocalDate.of(2026, 11, 15));
        BudgetRevisionSchedule first = schedule(budget, initial, LocalDate.of(2026, 11, 1), 1, 101L);
        BudgetRevisionSchedule invalid = schedule(budget, incompatible, LocalDate.of(2026, 12, 1), 2, 102L);

        BudgetTimeline timeline = new BudgetTimeline(List.of(first, invalid), List.of());
        assertThatThrownBy(() -> timeline.periods(0, 2, LocalDate.of(2026, 11, 2)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("partial Budget period");
    }

    @Test
    void countsPausedPeriodsTowardAutomaticCompletionWithoutCreatingAPersistedFinishEvent() {
        Budget budget = budget();
        BudgetRevision revision = BudgetRevision.record(
            1,
            new BudgetRevisionConfiguration(
                new BigDecimal("4000.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 11, 1),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.AFTER_N_PERIODS,
                2,
                null,
                BudgetAccountScopeMode.ALL,
                BudgetCategoryScopeMode.ALL,
                BudgetTagScopeMode.NONE,
                BudgetTagMatchMode.ANY
            ),
            Set.of(),
            Set.of(),
            Set.of(),
            RECORDED
        );
        id(revision, 101L);
        BudgetRevisionSchedule initial = schedule(budget, revision, LocalDate.of(2026, 11, 1), 1, 101L);
        BudgetLifecycleEvent pause = event(budget, BudgetLifecycleEventType.PAUSE_REQUESTED, LocalDate.of(2026, 12, 1), 1, 201L, null);

        assertThat(new BudgetTimeline(List.of(initial), List.of(pause)).periods(0, 2, LocalDate.of(2026, 11, 15)))
            .extracting(period -> period.status())
            .containsExactly(
                BudgetPeriodOperationalStatus.ACTIVE,
                BudgetPeriodOperationalStatus.PAUSED,
                BudgetPeriodOperationalStatus.COMPLETED
            );
    }

    private static Budget budget() {
        User user = new User();
        user.setLogin("owner");
        Budget budget = Budget.create(user, "Budget", null, BudgetType.EXPENSE, CurrencyCode.MXN, "creation", "hash", RECORDED);
        id(budget, 1L);
        return budget;
    }

    private static BudgetRevision revision(int number, String amount, LocalDate anchor) {
        BudgetRevision revision = BudgetRevision.record(
            number,
            new BudgetRevisionConfiguration(
                new BigDecimal(amount),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                anchor,
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
            RECORDED
        );
        id(revision, 100L + number);
        return revision;
    }

    private static BudgetRevisionSchedule schedule(Budget budget, BudgetRevision revision, LocalDate effective, long sequence, long id) {
        BudgetRevisionSchedule schedule = BudgetRevisionSchedule.scheduled(
            budget,
            revision,
            effective,
            sequence,
            "schedule-" + id,
            RECORDED
        );
        id(schedule, id);
        return schedule;
    }

    private static BudgetLifecycleEvent event(
        Budget budget,
        BudgetLifecycleEventType type,
        LocalDate effective,
        long sequence,
        long id,
        BudgetLifecycleEvent target
    ) {
        BudgetLifecycleEvent event = BudgetLifecycleEvent.record(budget, type, effective, sequence, "event-" + id, RECORDED, target);
        id(event, id);
        return event;
    }

    private static void id(Object target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
    }
}
