package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.service.FinancialTransactionService;
import com.fintrack.app.service.dto.CategoryDTO;
import com.fintrack.app.service.dto.FinancialAccountDTO;
import com.fintrack.app.service.dto.FinancialTransactionDTO;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL proof that threshold evidence uses the real Budget semantic executor, not a second sum implementation. */
@IntegrationTest
@Import(BudgetNotificationEngineIT.FixedClockConfiguration.class)
class BudgetNotificationEngineIT {

    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");
    private static final String OWNER = "user";

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Autowired
    private BudgetLifecycleEventRepository lifecycleEventRepository;

    @Autowired
    private BudgetRevisionRepository revisionRepository;

    @Autowired
    private BudgetTimeZoneAssignmentRepository timeZoneAssignmentRepository;

    @Autowired
    private BudgetNotificationPreferenceRepository preferenceRepository;

    @Autowired
    private BudgetThresholdClaimRepository claimRepository;

    @Autowired
    private BudgetNotificationOutboxRepository outboxRepository;

    @Autowired
    private BudgetNotificationRepository notificationRepository;

    @Autowired
    private BudgetPeriodCloseCursorRepository periodCloseCursorRepository;

    @Autowired
    private BudgetPeriodCloseWorkItemRepository periodCloseWorkItemRepository;

    @Autowired
    private BudgetTimeZoneService timeZoneService;

    @Autowired
    private BudgetThresholdNotificationService thresholdService;

    @Autowired
    private BudgetNotificationOutboxWorker outboxWorker;

    @Autowired
    private BudgetPeriodCloseScheduler periodCloseScheduler;

    @Autowired
    private FinancialTransactionService financialTransactionService;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @MockitoSpyBean
    private BudgetNotificationOutboxProcessor outboxProcessor;

    private final Set<Long> fixtureBudgetIds = new LinkedHashSet<>();
    private final Set<Long> fixtureAccountIds = new LinkedHashSet<>();
    private final Set<Long> fixtureCategoryIds = new LinkedHashSet<>();

    @AfterEach
    void removeCommittedFixtures() {
        transactionTemplate.executeWithoutResult(status -> {
            for (Long budgetId : fixtureBudgetIds) {
                periodCloseWorkItemRepository.deleteByBudgetId(budgetId);
                periodCloseCursorRepository.deleteByBudgetId(budgetId);
                notificationRepository.deleteByBudgetId(budgetId);
                outboxRepository.deleteByBudgetId(budgetId);
                claimRepository.deleteByBudgetId(budgetId);
                preferenceRepository.deleteByBudgetId(budgetId);
                timeZoneAssignmentRepository.deleteByBudgetId(budgetId);
                lifecycleEventRepository.deleteByBudgetId(budgetId);
                scheduleRepository.deleteByBudgetId(budgetId);
                revisionRepository.deleteAccountLinksByBudgetId(budgetId);
                revisionRepository.deleteCategoryLinksByBudgetId(budgetId);
                revisionRepository.deleteTagLinksByBudgetId(budgetId);
                revisionRepository.deleteByBudgetId(budgetId);
                budgetRepository
                    .findById(budgetId)
                    .ifPresent(budget -> budgetRepository.deleteAggregateById(budgetId, budget.getVersion()));
            }
            for (Long accountId : fixtureAccountIds) {
                transactionRepository.deleteTagLinksByAccountId(accountId);
                transactionRepository.deleteByAccountId(accountId);
                accountRepository.deleteById(accountId);
            }
            fixtureCategoryIds.forEach(categoryRepository::deleteById);
        });
        fixtureBudgetIds.clear();
        fixtureAccountIds.clear();
        fixtureCategoryIds.clear();
    }

    @Test
    void captures_intermediate_real_expense_crossings_once_and_materializes_one_notification_per_mutation() {
        User owner = TestUtil.findAll(entityManager, User.class)
            .stream()
            .filter(user -> OWNER.equals(user.getLogin()))
            .findFirst()
            .orElseGet(() -> {
                User user = new User();
                user.setLogin(OWNER);
                user.setPassword("x".repeat(60));
                user.setActivated(true);
                user.setEmail("notification-owner@example.test");
                user.setLangKey("en");
                return userRepository.saveAndFlush(user);
            });
        owner.setTimeZone("America/Mexico_City");
        FinancialAccount account = account(owner);
        Category category = category(owner);
        Budget budget = budget(owner, "notification-create", category);
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 3, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(budget, true, false, NOW);
        preference.replace(true, false, Set.of(new BigDecimal("50.0000"), new BigDecimal("80.0000"), new BigDecimal("100.0000")), NOW);
        preferenceRepository.saveAndFlush(preference);

        thresholdService.capture(
            OWNER,
            "TEST_FINANCIAL_MUTATION",
            () -> transactionRepository.saveAndFlush(transaction(account, category, "60.00")),
            transaction -> String.valueOf(transaction.getId())
        );
        thresholdService.capture(
            OWNER,
            "TEST_FINANCIAL_MUTATION",
            () -> transactionRepository.saveAndFlush(transaction(account, category, "50.00")),
            transaction -> String.valueOf(transaction.getId())
        );

        assertThat(claimRepository.countByBudgetId(budget.getId())).isEqualTo(3);
        var budgetEvents = outboxRepository.findAll().stream().filter(event -> event.getBudget().getId().equals(budget.getId())).toList();
        assertThat(budgetEvents).hasSize(2);
        assertThat(outboxWorker.processPending()).isEqualTo(2);
        assertThat(budgetEvents).allSatisfy(event -> {
            assertThat(notificationRepository.findOneByOutboxEventId(event.getId())).isPresent();
            assertThat(outboxRepository.findById(event.getId()).orElseThrow().getState()).isEqualTo(
                BudgetNotificationOutboxState.MATERIALIZED
            );
        });
    }

    @Test
    void persists_retry_evidence_and_allows_two_workers_to_materialize_only_once() throws Exception {
        User owner = owner();
        Budget budget = budget(owner, "notification-worker-retry");
        BudgetNotificationOutbox event = outboxRepository.saveAndFlush(
            BudgetNotificationOutbox.pending(owner, budget, "worker-retry", "{}", NOW)
        );

        doThrow(new IllegalStateException("simulated worker fault")).when(outboxProcessor).materialize(event.getId());
        // Other tests may have durable pending rows for this same disposable database. The failed event itself is
        // identified by its persisted retry state below; it must not materialize a notification on this pass.
        assertThat(outboxWorker.processPending()).isGreaterThanOrEqualTo(0);
        BudgetNotificationOutbox retriable = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(retriable.getState()).isEqualTo(BudgetNotificationOutboxState.PENDING);
        assertThat(retriable.getAttemptCount()).isEqualTo(1);
        assertThat(notificationRepository.findOneByOutboxEventId(event.getId())).isEmpty();

        // A restart sees the durable event. Make its already-persisted retry eligible without changing the snapshot
        // or recreating the event; this isolates retry policy from wall-clock timing in a deterministic test.
        ReflectionTestUtils.setField(retriable, "nextAttemptAt", NOW);
        outboxRepository.saveAndFlush(retriable);
        reset(outboxProcessor);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = executor.submit(() -> processAfter(start, ready));
            Future<Integer> second = executor.submit(() -> processAfter(start, ready));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(outboxRepository.findById(event.getId()).orElseThrow().getState()).isEqualTo(BudgetNotificationOutboxState.MATERIALIZED);
        assertThat(notificationRepository.findOneByOutboxEventId(event.getId())).isPresent();
        assertThat(outboxWorker.processPending()).isZero();
        assertThat(notificationRepository.findOneByOutboxEventId(event.getId())).isPresent();
    }

    @Test
    void serializes_two_concurrent_financial_mutations_for_one_owner_without_losing_a_crossing() throws Exception {
        User owner = owner();
        FinancialAccount account = account(owner);
        Category category = category(owner);
        Budget budget = budget(owner, "notification-concurrent-crossings", category);
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 3, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(budget, true, false, NOW);
        preference.replace(true, false, Set.of(new BigDecimal("50.0000"), new BigDecimal("80.0000")), NOW);
        preferenceRepository.saveAndFlush(preference);
        transactionRepository.saveAndFlush(transaction(account, category, "40.00"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Void> first = executor.submit(() -> captureConcurrentExpense(start, ready, account.getId(), category.getId(), "20.00"));
            Future<Void> second = executor.submit(() -> captureConcurrentExpense(start, ready, account.getId(), category.getId(), "20.00"));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(claimRepository.countByBudgetId(budget.getId())).isEqualTo(2);
        List<BudgetNotificationOutbox> events = outboxRepository
            .findAll()
            .stream()
            .filter(event -> event.getBudget().getId().equals(budget.getId()))
            .toList();
        assertThat(events).hasSize(2);
        assertThat(
            events.stream().flatMap(event -> snapshot(event.getSnapshotJson()).newlyCrossedPercentages().stream()).toList()
        ).containsExactlyInAnyOrder(new BigDecimal("50.0000"), new BigDecimal("80.0000"));
        assertThat(events.stream().map(event -> snapshot(event.getSnapshotJson()).newProgressPercentage()).toList()).allSatisfy(progress ->
            assertThat(progress).isIn(new BigDecimal("60.00"), new BigDecimal("80.00"))
        );
    }

    @Test
    @WithMockUser(username = OWNER)
    void captures_manual_create_and_scope_updates_through_the_real_transaction_service() {
        User owner = owner();
        FinancialAccount account = account(owner);
        Category category = category(owner);
        transactionRepository.saveAndFlush(transaction(account, category, "40.00"));
        Budget budget = budget(owner, "notification-manual-transaction-service", category);
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 3, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(budget, true, false, NOW);
        preference.replace(true, false, Set.of(new BigDecimal("50.0000"), new BigDecimal("80.0000")), NOW);
        preferenceRepository.saveAndFlush(preference);

        FinancialTransactionDTO crossing = financialTransactionService.save(transactionDto(account, category, "20.00"));
        assertThat(claimRepository.countByBudgetId(budget.getId())).isEqualTo(1);

        FinancialTransactionDTO leaveScope = new FinancialTransactionDTO();
        leaveScope.setId(crossing.getId());
        financialTransactionService.partialUpdate(leaveScope, objectMapper.createObjectNode().putNull("category")).orElseThrow();
        assertThat(claimRepository.countByBudgetId(budget.getId())).isEqualTo(1);

        FinancialTransactionDTO outsideScope = financialTransactionService.save(transactionDto(account, null, "40.00"));
        FinancialTransactionDTO enterScope = new FinancialTransactionDTO();
        enterScope.setId(outsideScope.getId());
        enterScope.setCategory(categoryDto(category));
        financialTransactionService
            .partialUpdate(
                enterScope,
                objectMapper.createObjectNode().set("category", objectMapper.createObjectNode().put("id", category.getId()))
            )
            .orElseThrow();

        assertThat(claimRepository.countByBudgetId(budget.getId())).isEqualTo(2);
        assertThat(outboxRepository.findAll().stream().filter(event -> event.getBudget().getId().equals(budget.getId()))).hasSize(2);
    }

    @Test
    @WithMockUser(username = OWNER)
    void captures_economic_income_with_the_same_canonical_pipeline() {
        User owner = owner();
        FinancialAccount account = account(owner);
        Category income = category(owner, CategoryType.INCOME);
        transactionRepository.saveAndFlush(transaction(account, income, "40.00", TransactionFlow.IN));
        Budget budget = budget(owner, "notification-income-transaction-service", income, BudgetType.INCOME);
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 3, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(budget, true, false, NOW);
        preference.replace(true, false, Set.of(new BigDecimal("50.0000")), NOW);
        preferenceRepository.saveAndFlush(preference);

        financialTransactionService.save(transactionDto(account, income, "20.00", TransactionFlow.IN));

        assertThat(claimRepository.countByBudgetId(budget.getId())).isEqualTo(1);
        assertThat(outboxRepository.findAll().stream().filter(event -> event.getBudget().getId().equals(budget.getId())))
            .singleElement()
            .satisfies(event -> assertThat(snapshot(event.getSnapshotJson()).budgetType()).isEqualTo(BudgetType.INCOME));
    }

    @Test
    @WithMockUser(username = OWNER)
    void captures_one_closed_period_with_real_financial_amount_and_materializes_it_once() {
        User owner = owner();
        FinancialAccount account = account(owner);
        Category category = category(owner);
        Budget budget = budget(owner, "period-close-february", category, BudgetType.EXPENSE, LocalDate.of(2026, 2, 1));
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 2, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(
            budget,
            false,
            true,
            Instant.parse("2026-02-01T00:00:00Z")
        );
        preference.replace(false, true, Set.of(), Instant.parse("2026-02-01T00:00:00Z"));
        preferenceRepository.saveAndFlush(preference);
        transactionRepository.saveAndFlush(transaction(account, category, "60.00", TransactionFlow.OUT, LocalDate.of(2026, 2, 15)));

        Instant closedAt = LocalDate.of(2026, 3, 1).atStartOfDay(java.time.ZoneId.of("America/Mexico_City")).toInstant();
        periodCloseCursorRepository.saveAndFlush(
            BudgetPeriodCloseCursor.schedule(budget, LocalDate.of(2026, 2, 1), closedAt, Instant.parse("2026-02-01T00:00:00Z"))
        );

        BudgetPeriodCloseScheduler.BudgetPeriodCloseRunResult result = periodCloseScheduler.processDue();

        assertThat(result.discovered()).isEqualTo(1);
        assertThat(result.captured()).isEqualTo(1);
        List<BudgetPeriodCloseWorkItem> workItems = periodCloseWorkItemRepository.findAllByBudgetIdOrderByPeriodStartAsc(budget.getId());
        assertThat(workItems).hasSize(1);
        BudgetPeriodCloseWorkItem work = workItems.get(0);
        assertThat(work.getState()).isEqualTo(BudgetPeriodCloseWorkItemState.CAPTURED);
        assertThat(work.getLogicalClosedAt()).isEqualTo(closedAt);
        BudgetNotificationOutbox event = outboxRepository
            .findAll()
            .stream()
            .filter(candidate -> candidate.getBudget().getId().equals(budget.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(event.getNotificationType()).isEqualTo(BudgetNotificationType.PERIOD_CLOSED);
        BudgetPeriodCloseNotificationSnapshot snapshot = periodCloseSnapshot(event.getSnapshotJson());
        assertThat(snapshot.actualAmount()).isEqualByComparingTo("60.00");
        assertThat(snapshot.targetAmount()).isEqualByComparingTo("100.00");
        assertThat(snapshot.progressPercentage()).isEqualByComparingTo("60.00");
        assertThat(snapshot.periodStart()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(snapshot.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(snapshot.logicalClosedAt()).isEqualTo(closedAt);
        assertThat(snapshot.capturedAt()).isEqualTo(NOW);

        assertThat(outboxWorker.processPending()).isEqualTo(1);
        assertThat(notificationRepository.findOneByOutboxEventId(event.getId()))
            .get()
            .extracting(BudgetNotification::getNotificationType)
            .isEqualTo(BudgetNotificationType.PERIOD_CLOSED);
        assertThat(periodCloseScheduler.processDue().captured()).isZero();
        assertThat(outboxRepository.findAll().stream().filter(candidate -> candidate.getBudget().getId().equals(budget.getId()))).hasSize(
            1
        );
    }

    @Test
    @WithMockUser(username = OWNER)
    void cancels_discovered_closing_when_preference_is_disabled_before_capture() {
        User owner = owner();
        Budget budget = budget(owner, "period-close-disabled", null, BudgetType.EXPENSE, LocalDate.of(2026, 2, 1));
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 2, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(
            budget,
            false,
            true,
            Instant.parse("2026-02-01T00:00:00Z")
        );
        preference.replace(false, true, Set.of(), Instant.parse("2026-02-01T00:00:00Z"));
        preferenceRepository.saveAndFlush(preference);
        Instant closedAt = LocalDate.of(2026, 3, 1).atStartOfDay(java.time.ZoneId.of("America/Mexico_City")).toInstant();
        BudgetPeriodCloseWorkItem work = periodCloseWorkItemRepository.saveAndFlush(
            BudgetPeriodCloseWorkItem.pending(
                budget,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1),
                "America/Mexico_City",
                closedAt,
                NOW
            )
        );
        preference.replace(false, false, Set.of(), NOW);
        preferenceRepository.saveAndFlush(preference);

        assertThat(periodCloseScheduler.processDue().captured()).isZero();
        BudgetPeriodCloseWorkItem persisted = periodCloseWorkItemRepository.findById(work.getId()).orElseThrow();
        assertThat(persisted.getState()).describedAs(persisted.getLastError()).isEqualTo(BudgetPeriodCloseWorkItemState.CANCELLED);
        assertThat(outboxRepository.findAll().stream().filter(candidate -> candidate.getBudget().getId().equals(budget.getId()))).isEmpty();
    }

    @Test
    @WithMockUser(username = OWNER)
    void captures_the_final_active_once_period_even_when_the_budget_is_now_terminal() {
        User owner = owner();
        account(owner); // A compatible account proves that an applicable empty scalar SUM becomes an authoritative zero.
        Budget budget = budget(
            owner,
            "period-close-once-final",
            null,
            BudgetType.EXPENSE,
            LocalDate.of(2026, 2, 1),
            BudgetRecurrenceMode.ONCE,
            BudgetRecurrenceTermination.NONE
        );
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 2, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(
            budget,
            false,
            true,
            Instant.parse("2026-02-01T00:00:00Z")
        );
        preference.replace(false, true, Set.of(), Instant.parse("2026-02-01T00:00:00Z"));
        preferenceRepository.saveAndFlush(preference);
        Instant closedAt = LocalDate.of(2026, 3, 1).atStartOfDay(java.time.ZoneId.of("America/Mexico_City")).toInstant();
        periodCloseCursorRepository.saveAndFlush(
            BudgetPeriodCloseCursor.schedule(budget, LocalDate.of(2026, 2, 1), closedAt, Instant.parse("2026-02-01T00:00:00Z"))
        );

        assertThat(periodCloseScheduler.processDue().captured()).isEqualTo(1);
        BudgetPeriodCloseWorkItem captured = periodCloseWorkItemRepository.findAllByBudgetIdOrderByPeriodStartAsc(budget.getId()).get(0);
        assertThat(captured.getState()).isEqualTo(BudgetPeriodCloseWorkItemState.CAPTURED);
        assertThat(
            periodCloseSnapshot(
                outboxRepository
                    .findAll()
                    .stream()
                    .filter(event -> event.getBudget().getId().equals(budget.getId()))
                    .findFirst()
                    .orElseThrow()
                    .getSnapshotJson()
            ).actualAmount()
        ).isEqualByComparingTo("0.00");
        assertThat(periodCloseCursorRepository.findOneByBudgetId(budget.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = OWNER)
    void stops_discovery_after_the_last_active_period_when_the_budget_is_paused() {
        User owner = owner();
        account(owner);
        Category category = category(owner);
        Budget budget = budget(owner, "period-close-paused", category, BudgetType.EXPENSE, LocalDate.of(2026, 2, 1));
        timeZoneService.recordInitial(budget, "America/Mexico_City", LocalDate.of(2026, 2, 1), NOW);
        BudgetNotificationPreference preference = BudgetNotificationPreference.create(
            budget,
            false,
            true,
            Instant.parse("2026-02-01T00:00:00Z")
        );
        preference.replace(false, true, Set.of(), Instant.parse("2026-02-01T00:00:00Z"));
        preferenceRepository.saveAndFlush(preference);
        lifecycleEventRepository.saveAndFlush(
            BudgetLifecycleEvent.record(
                budget,
                BudgetLifecycleEventType.PAUSE_REQUESTED,
                LocalDate.of(2026, 3, 1),
                1,
                "period-close-pause",
                NOW,
                null
            )
        );
        Instant closedAt = LocalDate.of(2026, 3, 1).atStartOfDay(java.time.ZoneId.of("America/Mexico_City")).toInstant();
        periodCloseCursorRepository.saveAndFlush(
            BudgetPeriodCloseCursor.schedule(budget, LocalDate.of(2026, 2, 1), closedAt, Instant.parse("2026-02-01T00:00:00Z"))
        );

        assertThat(periodCloseScheduler.processDue().captured()).isEqualTo(1);
        assertThat(periodCloseWorkItemRepository.findAllByBudgetIdOrderByPeriodStartAsc(budget.getId()))
            .singleElement()
            .satisfies(work -> {
                assertThat(work.getPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 1));
                assertThat(work.getState()).isEqualTo(BudgetPeriodCloseWorkItemState.CAPTURED);
            });
        assertThat(periodCloseCursorRepository.findOneByBudgetId(budget.getId())).isEmpty();
    }

    private int processAfter(CountDownLatch start, CountDownLatch ready) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent workers did not start");
            return outboxWorker.processPending();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private Void captureConcurrentExpense(CountDownLatch start, CountDownLatch ready, Long accountId, Long categoryId, String amount) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Concurrent mutations did not start");
            thresholdService.capture(
                OWNER,
                "TEST_CONCURRENT_FINANCIAL_MUTATION",
                () ->
                    transactionRepository.saveAndFlush(
                        transaction(accountRepository.getReferenceById(accountId), categoryRepository.getReferenceById(categoryId), amount)
                    ),
                transaction -> String.valueOf(transaction.getId())
            );
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private BudgetThresholdNotificationSnapshot snapshot(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .readValue(json, BudgetThresholdNotificationSnapshot.class);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Notification snapshot was not valid JSON", exception);
        }
    }

    private BudgetPeriodCloseNotificationSnapshot periodCloseSnapshot(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .readValue(json, BudgetPeriodCloseNotificationSnapshot.class);
        } catch (java.io.IOException exception) {
            throw new AssertionError("Period-close snapshot was not valid JSON", exception);
        }
    }

    private FinancialTransactionDTO transactionDto(FinancialAccount account, Category category, String amount) {
        return transactionDto(account, category, amount, TransactionFlow.OUT);
    }

    private FinancialTransactionDTO transactionDto(FinancialAccount account, Category category, String amount, TransactionFlow flow) {
        FinancialTransactionDTO dto = new FinancialTransactionDTO();
        dto.setTransactionDate(LocalDate.of(2026, 3, 15));
        dto.setPostingDate(LocalDate.of(2026, 3, 15));
        dto.setDescription("Manual notification expense " + amount);
        dto.setAmount(new BigDecimal(amount));
        dto.setFlow(flow);
        dto.setOrigin(TransactionOrigin.MANUAL);
        dto.setCreatedAt(NOW);
        dto.setUpdatedAt(NOW);
        FinancialAccountDTO accountDto = new FinancialAccountDTO();
        accountDto.setId(account.getId());
        dto.setAccount(accountDto);
        dto.setCategory(category == null ? null : categoryDto(category));
        return dto;
    }

    private CategoryDTO categoryDto(Category category) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(category.getId());
        return dto;
    }

    private User owner() {
        return TestUtil.findAll(entityManager, User.class)
            .stream()
            .filter(user -> OWNER.equals(user.getLogin()))
            .findFirst()
            .orElseGet(() -> {
                User user = new User();
                user.setLogin(OWNER);
                user.setPassword("x".repeat(60));
                user.setActivated(true);
                user.setEmail("notification-owner@example.test");
                user.setLangKey("en");
                return userRepository.saveAndFlush(user);
            });
    }

    private Budget budget(User owner) {
        return budget(owner, "notification-create");
    }

    private Budget budget(User owner, String creationRequestId) {
        return budget(owner, creationRequestId, null);
    }

    private Budget budget(User owner, String creationRequestId, Category category) {
        return budget(owner, creationRequestId, category, BudgetType.EXPENSE);
    }

    private Budget budget(User owner, String creationRequestId, Category category, BudgetType type) {
        return budget(owner, creationRequestId, category, type, LocalDate.of(2026, 3, 1));
    }

    private Budget budget(User owner, String creationRequestId, Category category, BudgetType type, LocalDate anchorDate) {
        return budget(
            owner,
            creationRequestId,
            category,
            type,
            anchorDate,
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE
        );
    }

    private Budget budget(
        User owner,
        String creationRequestId,
        Category category,
        BudgetType type,
        LocalDate anchorDate,
        BudgetRecurrenceMode recurrenceMode,
        BudgetRecurrenceTermination recurrenceTermination
    ) {
        boolean selectedCategory = category != null;
        BudgetRevisionConfiguration configuration = new BudgetRevisionConfiguration(
            new BigDecimal("100.00"),
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.NATURAL,
            anchorDate,
            recurrenceMode,
            recurrenceTermination,
            null,
            null,
            BudgetAccountScopeMode.ALL,
            selectedCategory ? BudgetCategoryScopeMode.SELECTED : BudgetCategoryScopeMode.ALL,
            BudgetTagScopeMode.NONE,
            BudgetTagMatchMode.ANY
        );
        Budget budget = Budget.create(owner, "Notification budget", null, type, CurrencyCode.MXN, creationRequestId, "hash", NOW);
        budget.addInitialRevision(
            BudgetRevision.initial(configuration, Set.of(), selectedCategory ? Set.of(category) : Set.of(), Set.of(), NOW)
        );
        Budget saved = budgetRepository.saveAndFlush(budget);
        fixtureBudgetIds.add(saved.getId());
        scheduleRepository.saveAndFlush(
            BudgetRevisionSchedule.scheduled(saved, saved.getRevisions().get(0), anchorDate, 1, "INITIAL", NOW)
        );
        return saved;
    }

    private FinancialAccount account(User owner) {
        FinancialAccount saved = accountRepository.saveAndFlush(
            new FinancialAccount()
                .name("Notification MXN")
                .institutionName("Bank")
                .accountType(AccountType.DEBIT)
                .currency(CurrencyCode.MXN)
                .initialBalance(BigDecimal.ZERO)
                .initialBalanceDate(LocalDate.of(2025, 1, 1))
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .user(owner)
        );
        fixtureAccountIds.add(saved.getId());
        return saved;
    }

    private Category category(User owner) {
        return category(owner, CategoryType.EXPENSE);
    }

    private Category category(User owner, CategoryType type) {
        Category saved = categoryRepository.saveAndFlush(
            new Category()
                .name("Notification expense")
                .categoryType(type)
                .color("#225588")
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .user(owner)
        );
        fixtureCategoryIds.add(saved.getId());
        return saved;
    }

    private FinancialTransaction transaction(FinancialAccount account, Category category, String amount) {
        return transaction(account, category, amount, TransactionFlow.OUT);
    }

    private FinancialTransaction transaction(FinancialAccount account, Category category, String amount, TransactionFlow flow) {
        return transaction(account, category, amount, flow, LocalDate.of(2026, 3, 15));
    }

    private FinancialTransaction transaction(
        FinancialAccount account,
        Category category,
        String amount,
        TransactionFlow flow,
        LocalDate transactionDate
    ) {
        return new FinancialTransaction()
            .account(account)
            .category(category)
            .transactionDate(transactionDate)
            .postingDate(transactionDate)
            .description("Notification expense " + amount)
            .amount(new BigDecimal(amount))
            .flow(flow)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(NOW)
            .updatedAt(NOW);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock budgetNotificationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
