package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.service.CurrentUserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetCommandServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T18:00:00Z"), ZoneOffset.UTC);
    private static final String OWNER = "budget-owner";

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetRevisionRepository revisionRepository;

    @Mock
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Mock
    private BudgetLifecycleEventRepository lifecycleEventRepository;

    @Mock
    private BudgetCommandReceiptRepository receiptRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private CurrentUserService currentUserService;

    private final List<BudgetRevisionSchedule> schedules = new ArrayList<>();
    private final List<BudgetLifecycleEvent> events = new ArrayList<>();
    private final List<BudgetCommandReceipt> receipts = new ArrayList<>();
    private Budget budget;
    private BudgetCommandService service;
    private long nextId = 50L;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(1L);
        user.setLogin(OWNER);
        budget = Budget.create(user, "Food", null, BudgetType.EXPENSE, CurrencyCode.MXN, "create", "hash", CLOCK.instant());
        id(budget, 7L);
        ReflectionTestUtils.setField(budget, "version", 3L);
        BudgetRevision initial = revision(1, "1000.00");
        id(initial, 11L);
        budget.addInitialRevision(initial);
        BudgetRevisionSchedule initialSchedule = BudgetRevisionSchedule.scheduled(
            budget,
            initial,
            LocalDate.of(2026, 3, 1),
            1,
            "INITIAL",
            CLOCK.instant()
        );
        id(initialSchedule, 12L);
        schedules.add(initialSchedule);

        service = new BudgetCommandService(
            budgetRepository,
            revisionRepository,
            scheduleRepository,
            lifecycleEventRepository,
            receiptRepository,
            financialAccountRepository,
            categoryRepository,
            tagRepository,
            currentUserService,
            CLOCK
        );
        when(currentUserService.getCurrentUserLogin()).thenReturn(OWNER);
        when(budgetRepository.findOneByIdAndUserLogin(7L, OWNER)).thenReturn(Optional.of(budget));
        lenient().when(revisionRepository.findAllByBudgetIdOrderByRevisionNumber(7L)).thenAnswer(invocation -> budget.getRevisions());
        lenient().when(scheduleRepository.findAllByBudgetIdOrderBySequenceNumber(7L)).thenAnswer(invocation -> List.copyOf(schedules));
        lenient().when(lifecycleEventRepository.findAllByBudgetIdOrderBySequenceNumber(7L)).thenAnswer(invocation -> List.copyOf(events));
        lenient()
            .when(receiptRepository.findByBudgetCommandAndRequestId(eq(7L), anyString(), anyString()))
            .thenAnswer(invocation ->
                receipts
                    .stream()
                    .filter(
                        receipt ->
                            receipt.getCommandType().equals(invocation.getArgument(1)) &&
                            receipt.getClientRequestId().equals(invocation.getArgument(2))
                    )
                    .findFirst()
            );
        lenient()
            .when(budgetRepository.saveAndFlush(any(Budget.class)))
            .thenAnswer(invocation -> {
                Long version = invocation.<Budget>getArgument(0).getVersion();
                ReflectionTestUtils.setField(budget, "version", version == null ? 0L : version + 1L);
                for (BudgetRevision candidate : budget.getRevisions()) if (candidate.getId() == null) id(candidate, nextId++);
                return budget;
            });
        lenient()
            .when(scheduleRepository.saveAndFlush(any(BudgetRevisionSchedule.class)))
            .thenAnswer(invocation -> {
                BudgetRevisionSchedule value = invocation.getArgument(0);
                id(value, nextId++);
                schedules.add(value);
                return value;
            });
        lenient()
            .when(lifecycleEventRepository.saveAndFlush(any(BudgetLifecycleEvent.class)))
            .thenAnswer(invocation -> {
                BudgetLifecycleEvent value = invocation.getArgument(0);
                id(value, nextId++);
                events.add(value);
                return value;
            });
        lenient()
            .when(receiptRepository.saveAndFlush(any(BudgetCommandReceipt.class)))
            .thenAnswer(invocation -> {
                BudgetCommandReceipt value = invocation.getArgument(0);
                id(value, nextId++);
                receipts.add(value);
                return value;
            });
        lenient()
            .when(scheduleRepository.findById(anyLong()))
            .thenAnswer(invocation -> schedules.stream().filter(s -> Objects.equals(s.getId(), invocation.getArgument(0))).findFirst());
        lenient()
            .when(lifecycleEventRepository.findById(anyLong()))
            .thenAnswer(invocation -> events.stream().filter(e -> Objects.equals(e.getId(), invocation.getArgument(0))).findFirst());
    }

    @Test
    void schedulesAndReplaysAFutureRevisionWithoutChangingTheInitialSnapshot() {
        BudgetRevisionScheduleCommand command = revisionCommand(3L, "schedule-april", false, LocalDate.of(2026, 4, 1), "1500.00");

        BudgetMutationResult first = service.scheduleRevision(7L, command);
        BudgetMutationResult replay = service.scheduleRevision(7L, command);

        assertThat(first.revisionScheduleId()).isEqualTo(replay.revisionScheduleId());
        assertThat(first.version()).isEqualTo(replay.version());
        assertThat(budget.getRevisions()).hasSize(2);
        assertThat(schedules).hasSize(2);
        assertThat(
            new BudgetTimeline(schedules, events).resolveEffectiveRevision(LocalDate.of(2026, 4, 1)).revision().getTargetAmount()
        ).isEqualByComparingTo("1500.00");
    }

    @Test
    void cancelsOnlyTheTargetedFutureRevisionAndKeepsThePriorEffectiveConfiguration() {
        BudgetMutationResult scheduled = service.scheduleRevision(
            7L,
            revisionCommand(3L, "schedule-april", false, LocalDate.of(2026, 4, 1), "1500.00")
        );

        BudgetMutationResult cancelled = service.cancelFutureRevision(
            7L,
            scheduled.revisionScheduleId(),
            new BudgetVersionedCommand(4L, "cancel-april", "America/Mexico_City")
        );

        assertThat(cancelled.revisionScheduleId()).isNotNull();
        assertThat(schedules).hasSize(3);
        assertThat(
            new BudgetTimeline(schedules, events).resolveEffectiveRevision(LocalDate.of(2026, 4, 1)).revision().getTargetAmount()
        ).isEqualByComparingTo("1000.00");
    }

    @Test
    void rejectsStaleCommandsBeforeAppendingHistory() {
        assertThatThrownBy(() -> service.scheduleRevision(7L, revisionCommand(2L, "stale", false, LocalDate.of(2026, 4, 1), "1500.00")))
            .isInstanceOf(BudgetCommandException.class)
            .extracting("code")
            .isEqualTo(BudgetCommandException.Code.VERSION_CONFLICT);
        assertThat(schedules).hasSize(1);
    }

    @Test
    void appliesAnAllowedAmountChangeToTheEntireCurrentPeriodWithoutRemovingFutureHistory() {
        BudgetMutationResult changed = service.scheduleRevision(7L, revisionCommand(3L, "apply-current", true, null, "1700.00"));

        assertThat(changed.effectivePeriodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(
            new BudgetTimeline(schedules, events).resolveEffectiveRevision(LocalDate.of(2026, 3, 1)).revision().getTargetAmount()
        ).isEqualByComparingTo("1700.00");
        assertThat(schedules).hasSize(2);
    }

    @Test
    void currentChangesAndExplicitFutureReplacementPreserveUnrelatedScheduleBoundaries() {
        BudgetMutationResult april = service.scheduleRevision(
            7L,
            revisionCommand(3L, "schedule-april", false, LocalDate.of(2026, 4, 1), "1500.00")
        );
        service.scheduleRevision(7L, revisionCommand(4L, "apply-current", true, null, "1700.00"));
        BudgetMutationResult replacement = service.replaceFutureRevision(
            7L,
            april.revisionScheduleId(),
            revisionCommand(5L, "replace-april", false, LocalDate.of(2026, 4, 1), "1900.00")
        );

        BudgetTimeline timeline = new BudgetTimeline(schedules, events);
        assertThat(timeline.resolveEffectiveRevision(LocalDate.of(2026, 3, 1)).revision().getTargetAmount()).isEqualByComparingTo(
            "1700.00"
        );
        assertThat(timeline.resolveEffectiveRevision(LocalDate.of(2026, 4, 1)).revision().getTargetAmount()).isEqualByComparingTo(
            "1900.00"
        );
        assertThat(replacement.revisionScheduleId()).isNotEqualTo(april.revisionScheduleId());
    }

    @Test
    void pausesAtTheNextBoundaryAndRecordsAnImmutableLifecycleEvent() {
        BudgetMutationResult paused = service.requestPause(7L, new BudgetLifecycleCommand(3L, "pause-april", "America/Mexico_City"));

        assertThat(paused.effectivePeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(events)
            .singleElement()
            .satisfies(event -> {
                assertThat(event.getEventType()).isEqualTo(BudgetLifecycleEventType.PAUSE_REQUESTED);
                assertThat(event.getEffectivePeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
            });
    }

    @Test
    void cancelsOnlyAPendingPauseAndLeavesTheFuturePeriodScheduled() {
        BudgetMutationResult pause = service.requestPause(7L, new BudgetLifecycleCommand(3L, "pause-april", "America/Mexico_City"));

        service.cancelPendingPause(7L, pause.lifecycleEventId(), new BudgetLifecycleCommand(4L, "cancel-pause", "America/Mexico_City"));

        assertThat(events).hasSize(2);
        assertThat(new BudgetTimeline(schedules, events).periods(1, 1, LocalDate.of(2026, 3, 15)).get(0).status()).isEqualTo(
            BudgetPeriodOperationalStatus.SCHEDULED
        );
    }

    @Test
    void rejectsAReusedCommandIdWhenItsRevisionPayloadDiffers() {
        service.scheduleRevision(7L, revisionCommand(3L, "same-request", false, LocalDate.of(2026, 4, 1), "1500.00"));

        assertThatThrownBy(() ->
            service.scheduleRevision(7L, revisionCommand(3L, "same-request", false, LocalDate.of(2026, 4, 1), "1600.00"))
        )
            .isInstanceOf(BudgetCommandException.class)
            .extracting("code")
            .isEqualTo(BudgetCommandException.Code.VERSION_CONFLICT);
        assertThat(schedules).hasSize(2);
    }

    @Test
    void resumesOnlyFromAPausedPeriodAndKeepsTheOriginalCalendar() {
        events.add(lifecycleEvent(BudgetLifecycleEventType.PAUSE_REQUESTED, LocalDate.of(2026, 3, 1), 1, 80L, null));

        BudgetMutationResult resumed = service.requestResume(7L, new BudgetLifecycleCommand(3L, "resume-april", "America/Mexico_City"));

        assertThat(resumed.effectivePeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(new BudgetTimeline(schedules, events).periods(0, 1, LocalDate.of(2026, 5, 15)))
            .extracting(period -> period.status())
            .containsExactly(BudgetPeriodOperationalStatus.PAUSED, BudgetPeriodOperationalStatus.ACTIVE);
    }

    @Test
    void rejectsCancellationAfterAPauseHasBecomeEffective() {
        BudgetLifecycleEvent effectivePause = lifecycleEvent(
            BudgetLifecycleEventType.PAUSE_REQUESTED,
            LocalDate.of(2026, 3, 1),
            1,
            80L,
            null
        );
        events.add(effectivePause);

        assertThatThrownBy(() ->
            service.cancelPendingPause(7L, effectivePause.getId(), new BudgetLifecycleCommand(3L, "too-late", "America/Mexico_City"))
        )
            .isInstanceOf(BudgetCommandException.class)
            .extracting("code")
            .isEqualTo(BudgetCommandException.Code.INVALID_PAYLOAD);
        assertThat(events).singleElement().isSameAs(effectivePause);
    }

    @Test
    void completesAtTheNextBoundaryAndRejectsConflictingPendingLifecycleChanges() {
        service.requestPause(7L, new BudgetLifecycleCommand(3L, "pause-april", "America/Mexico_City"));

        assertThatThrownBy(() -> service.requestFinish(7L, new BudgetLifecycleCommand(4L, "finish-april", "America/Mexico_City")))
            .isInstanceOf(BudgetCommandException.class)
            .extracting("code")
            .isEqualTo(BudgetCommandException.Code.INVALID_PAYLOAD);

        events.clear();
        BudgetMutationResult finished = service.requestFinish(7L, new BudgetLifecycleCommand(4L, "finish-april", "America/Mexico_City"));

        assertThat(finished.effectivePeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(new BudgetTimeline(schedules, events).periods(1, 1, LocalDate.of(2026, 3, 15)).get(0).status()).isEqualTo(
            BudgetPeriodOperationalStatus.COMPLETED
        );
        assertThatThrownBy(() -> service.requestPause(7L, new BudgetLifecycleCommand(5L, "after-finish", "America/Mexico_City")))
            .isInstanceOf(BudgetCommandException.class)
            .extracting("code")
            .isEqualTo(BudgetCommandException.Code.INVALID_PAYLOAD);
    }

    private static BudgetRevisionScheduleCommand revisionCommand(
        Long expectedVersion,
        String requestId,
        boolean current,
        LocalDate effective,
        String amount
    ) {
        return new BudgetRevisionScheduleCommand(
            expectedVersion,
            requestId,
            "America/Mexico_City",
            current,
            effective,
            new BudgetInitialRevisionCommand(
                new BigDecimal(amount),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 3, 1),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.ALL,
                Set.of(),
                BudgetCategoryScopeMode.ALL,
                Set.of(),
                BudgetTagScopeMode.NONE,
                Set.of(),
                BudgetTagMatchMode.ANY
            )
        );
    }

    private static BudgetRevision revision(int number, String target) {
        return BudgetRevision.record(
            number,
            new BudgetRevisionConfiguration(
                new BigDecimal(target),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 3, 1),
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
            CLOCK.instant()
        );
    }

    private BudgetLifecycleEvent lifecycleEvent(
        BudgetLifecycleEventType type,
        LocalDate effective,
        long sequence,
        long id,
        BudgetLifecycleEvent target
    ) {
        BudgetLifecycleEvent event = BudgetLifecycleEvent.record(
            budget,
            type,
            effective,
            sequence,
            "history-" + id,
            CLOCK.instant(),
            target
        );
        id(event, id);
        return event;
    }

    private static void id(Object target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
    }
}
