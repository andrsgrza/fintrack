package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.*;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.repository.*;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL proof that Budget-4 interprets the real canonical semantic result rather than aggregating transactions
 * itself. Fixture totals below are independently stated in each assertion.
 */
@IntegrationTest
@Transactional
@WithMockUser(username = "user")
@Import(BudgetEvaluationServiceIT.FixedClockConfiguration.class)
@AutoConfigureMockMvc
class BudgetEvaluationServiceIT {

    private static final String ZONE = "America/Mexico_City";
    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");

    @Autowired
    private BudgetEvaluationService evaluationService;

    @Autowired
    private MockMvc restBudgetMockMvc;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Autowired
    private BudgetRevisionRepository revisionRepository;

    @Autowired
    private BudgetLifecycleEventRepository lifecycleEventRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private InternalTransferRepository transferRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    @Test
    void evaluates_real_economic_expenses_by_descendant_scope_excludes_transfer_legs_and_caps_current_period() {
        User owner = currentUser();
        FinancialAccount mxn = account(owner, "Budget MXN", CurrencyCode.MXN);
        FinancialAccount cash = account(owner, "Budget cash", CurrencyCode.MXN);
        FinancialAccount usd = account(owner, "Budget USD", CurrencyCode.USD);
        Category household = category(owner, "Household", CategoryType.EXPENSE, null);
        Category food = category(owner, "Food", CategoryType.EXPENSE, household);

        // January economic expenses under Household: 50 + 20 = 70 MXN.
        FinancialTransaction janFood = transaction(mxn, food, TransactionFlow.OUT, "50", LocalDate.of(2026, 1, 10));
        FinancialTransaction janHousehold = transaction(mxn, household, TransactionFlow.OUT, "20", LocalDate.of(2026, 1, 20));
        transactionRepository.saveAndFlush(janFood);
        transactionRepository.saveAndFlush(janHousehold);
        // February current-period eligible expense is 25; March 20 is future relative to the fixed March 15 clock.
        transactionRepository.saveAndFlush(transaction(mxn, food, TransactionFlow.OUT, "25", LocalDate.of(2026, 3, 10)));
        transactionRepository.saveAndFlush(transaction(mxn, food, TransactionFlow.OUT, "60", LocalDate.of(2026, 3, 20)));
        // A linked internal transfer leg, an income, and USD expense are intentionally not economic MXN expense.
        FinancialTransaction outgoing = transaction(mxn, food, TransactionFlow.OUT, "200", LocalDate.of(2026, 1, 21));
        FinancialTransaction incoming = transaction(cash, null, TransactionFlow.IN, "200", LocalDate.of(2026, 1, 21));
        transactionRepository.saveAndFlush(outgoing);
        transactionRepository.saveAndFlush(incoming);
        transferRepository.saveAndFlush(
            new InternalTransfer().createdAt(NOW).updatedAt(NOW).outgoingTransaction(outgoing).incomingTransaction(incoming)
        );
        transactionRepository.saveAndFlush(transaction(mxn, food, TransactionFlow.IN, "400", LocalDate.of(2026, 1, 8)));
        transactionRepository.saveAndFlush(transaction(usd, food, TransactionFlow.OUT, "999", LocalDate.of(2026, 1, 9)));

        Budget budget = budget(
            owner,
            BudgetType.EXPENSE,
            "100.00",
            LocalDate.of(2026, 1, 1),
            BudgetCategoryScopeMode.SELECTED,
            Set.of(household)
        );

        BudgetEvaluationResult january = evaluationService.period(budget.getId(), 0, ZONE);
        BudgetEvaluationResult march = evaluationService.current(budget.getId(), ZONE);

        assertThat(january.actualAmount()).isEqualByComparingTo("70.00");
        assertThat(january.goalStatus()).isEqualTo(BudgetGoalStatus.FINISHED_WITHIN_LIMIT);
        assertThat(january.semanticAvailability()).isEqualTo(com.fintrack.app.service.analytics.SemanticResultAvailability.COMPLETE);
        assertThat(march.actualAmount()).isEqualByComparingTo("25.00");
        assertThat(march.periodTiming()).isEqualTo(BudgetEvaluationPeriodTiming.CURRENT);
        assertThat(march.goalStatus()).isEqualTo(BudgetGoalStatus.WITHIN_LIMIT);

        // Historical evaluation is intentionally live: correction changes the answer without a stored snapshot.
        janHousehold.setAmount(new BigDecimal("30"));
        transactionRepository.saveAndFlush(janHousehold);
        assertThat(evaluationService.period(budget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void preserves_revision_history_zero_paused_future_and_completed_budget_semantics() {
        User owner = currentUser();
        // An owned MXN account makes the empty scalar result applicable; the semantic executor can then emit a known zero.
        account(owner, "Empty budget account", CurrencyCode.MXN);
        Budget budget = budget(owner, BudgetType.INCOME, "100.00", LocalDate.of(2026, 1, 1), BudgetCategoryScopeMode.ALL, Set.of());
        BudgetRevision revisionOne = budget.getRevisions().get(0);
        BudgetRevision revisionTwo = BudgetRevision.record(
            2,
            configuration("150.00", LocalDate.of(2026, 1, 1)),
            Set.of(),
            Set.of(),
            Set.of(),
            NOW
        );
        budget.addRevision(revisionTwo);
        revisionRepository.saveAndFlush(revisionTwo);
        budgetRepository.saveAndFlush(budget);
        scheduleRepository.saveAndFlush(BudgetRevisionSchedule.scheduled(budget, revisionTwo, LocalDate.of(2026, 2, 1), 2, "r2", NOW));

        BudgetEvaluationResult january = evaluationService.period(budget.getId(), 0, ZONE);
        BudgetEvaluationResult february = evaluationService.period(budget.getId(), 1, ZONE);
        BudgetEvaluationResult april = evaluationService.period(budget.getId(), 3, ZONE);

        assertThat(january.revisionId()).isEqualTo(revisionOne.getId());
        assertThat(january.actualAmount()).isEqualByComparingTo("0");
        assertThat(january.semanticAvailability()).isEqualTo(com.fintrack.app.service.analytics.SemanticResultAvailability.ZERO);
        assertThat(january.goalStatus()).isEqualTo(BudgetGoalStatus.GOAL_NOT_MET);
        assertThat(february.revisionId()).isEqualTo(revisionTwo.getId());
        assertThat(february.targetAmount()).isEqualByComparingTo("150.00");
        assertThat(april.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.NOT_YET_EVALUATED);
        assertThat(april.actualAmount()).isNull();

        BudgetLifecycleEvent pause = lifecycleEventRepository.saveAndFlush(
            BudgetLifecycleEvent.record(budget, BudgetLifecycleEventType.PAUSE_REQUESTED, LocalDate.of(2026, 3, 1), 1, "pause", NOW, null)
        );
        BudgetEvaluationResult paused = evaluationService.current(budget.getId(), ZONE);
        assertThat(paused.operationalStatus()).isEqualTo(BudgetPeriodOperationalStatus.PAUSED);
        assertThat(paused.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.NOT_EVALUATED);
        assertThat(paused.actualAmount()).isNull();

        lifecycleEventRepository.saveAndFlush(
            BudgetLifecycleEvent.record(budget, BudgetLifecycleEventType.FINISH_REQUESTED, LocalDate.of(2026, 4, 1), 2, "finish", NOW, null)
        );
        // Final completion does not hide or mutate historical financial evaluation.
        BudgetEvaluationResult afterCompletion = evaluationService.period(budget.getId(), 0, ZONE);
        assertThat(afterCompletion.actualAmount()).isEqualByComparingTo("0");
        assertThat(afterCompletion.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.EVALUATED);
        assertThat(pause.getId()).isNotNull();
    }

    @Test
    void does_not_disclose_another_users_budget() throws Exception {
        User foreign = user("budget-evaluation-other", "budget-evaluation-other@example.invalid");
        Budget privateBudget = budget(
            foreign,
            BudgetType.EXPENSE,
            "10.00",
            LocalDate.of(2026, 1, 1),
            BudgetCategoryScopeMode.ALL,
            Set.of()
        );

        assertThatThrownBy(() -> evaluationService.current(privateBudget.getId(), ZONE))
            .isInstanceOf(BudgetEvaluationException.class)
            .extracting(exception -> ((BudgetEvaluationException) exception).getCode())
            .isEqualTo(BudgetEvaluationException.Code.BUDGET_NOT_FOUND);
        restBudgetMockMvc
            .perform(get("/api/budgets/{budgetId}/evaluation/current", privateBudget.getId()).param("timeZone", ZONE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BUDGET_NOT_FOUND"));
    }

    @Test
    void exposes_current_historical_reference_and_bounded_history_routes_without_mutating_financial_records() throws Exception {
        User owner = currentUser();
        FinancialAccount account = account(owner, "REST budget account", CurrencyCode.MXN);
        transactionRepository.saveAndFlush(transaction(account, null, TransactionFlow.OUT, "12", LocalDate.of(2026, 1, 10)));
        Budget budget = budget(owner, BudgetType.EXPENSE, "100.00", LocalDate.of(2026, 1, 1), BudgetCategoryScopeMode.ALL, Set.of());
        long transactionsBefore = transactionRepository.count();

        restBudgetMockMvc
            .perform(get("/api/budgets/{budgetId}/evaluation/current", budget.getId()).param("timeZone", ZONE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.periodIndex").value(2))
            .andExpect(jsonPath("$.actualAmount").value(0))
            .andExpect(jsonPath("$.evaluationStatus").value("EVALUATED"));
        restBudgetMockMvc
            .perform(get("/api/budgets/{budgetId}/evaluation/period/{periodIndex}", budget.getId(), 0).param("timeZone", ZONE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actualAmount").value(12))
            .andExpect(jsonPath("$.periodTiming").value("HISTORICAL"));
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{budgetId}/evaluation/at", budget.getId()).param("referenceDate", "2026-01-10").param("timeZone", ZONE)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.periodIndex").value(0));
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{budgetId}/evaluations", budget.getId())
                    .param("firstPeriodIndex", "0")
                    .param("size", "4")
                    .param("timeZone", ZONE)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evaluations.length()").value(4))
            .andExpect(jsonPath("$.evaluations[3].evaluationStatus").value("NOT_YET_EVALUATED"));
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{budgetId}/evaluation/at", budget.getId()).param("referenceDate", "2025-12-31").param("timeZone", ZONE)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
    }

    private Budget budget(
        User owner,
        BudgetType type,
        String target,
        LocalDate anchor,
        BudgetCategoryScopeMode categoryScope,
        Set<Category> categories
    ) {
        Budget budget = Budget.create(
            owner,
            "Budget " + System.nanoTime(),
            null,
            type,
            CurrencyCode.MXN,
            "create-" + System.nanoTime(),
            "hash",
            NOW
        );
        budget.addInitialRevision(
            BudgetRevision.initial(configuration(target, anchor, categoryScope), Set.of(), categories, Set.of(), NOW)
        );
        Budget saved = budgetRepository.saveAndFlush(budget);
        scheduleRepository.saveAndFlush(BudgetRevisionSchedule.scheduled(saved, saved.getRevisions().get(0), anchor, 1, "INITIAL", NOW));
        return saved;
    }

    private BudgetRevisionConfiguration configuration(String target, LocalDate anchor) {
        return configuration(target, anchor, BudgetCategoryScopeMode.ALL);
    }

    private BudgetRevisionConfiguration configuration(String target, LocalDate anchor, BudgetCategoryScopeMode categoryScope) {
        return new BudgetRevisionConfiguration(
            new BigDecimal(target),
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.NATURAL,
            anchor,
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null,
            BudgetAccountScopeMode.ALL,
            categoryScope,
            BudgetTagScopeMode.NONE,
            BudgetTagMatchMode.ANY
        );
    }

    private User currentUser() {
        return TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
    }

    private User user(String login, String email) {
        User user = new User();
        user.setLogin(login);
        user.setPassword("x".repeat(60));
        user.setActivated(true);
        user.setEmail(email);
        user.setLangKey("en");
        return userRepository.saveAndFlush(user);
    }

    private FinancialAccount account(User owner, String name, CurrencyCode currency) {
        return accountRepository.saveAndFlush(
            new FinancialAccount()
                .name(name)
                .institutionName("Budget bank")
                .accountType(AccountType.DEBIT)
                .currency(currency)
                .initialBalance(BigDecimal.ZERO)
                .initialBalanceDate(LocalDate.of(2025, 1, 1))
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .user(owner)
        );
    }

    private Category category(User owner, String name, CategoryType type, Category parent) {
        return categoryRepository.saveAndFlush(
            new Category()
                .name(name)
                .categoryType(type)
                .color("#225588")
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .user(owner)
                .parentCategory(parent)
        );
    }

    private FinancialTransaction transaction(
        FinancialAccount account,
        Category category,
        TransactionFlow flow,
        String amount,
        LocalDate date
    ) {
        return new FinancialTransaction()
            .account(account)
            .category(category)
            .transactionDate(date)
            .postingDate(date)
            .description("Budget evaluation " + amount)
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
        Clock budgetEvaluationClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
