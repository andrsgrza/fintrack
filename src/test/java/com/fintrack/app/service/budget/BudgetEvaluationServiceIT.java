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
    private BudgetCommandService commandService;

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
    private TagRepository tagRepository;

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
    void evaluates_all_owned_accounts_with_currency_isolation_exact_date_boundaries_and_transfer_link_mutations() {
        User owner = currentUser();
        FinancialAccount initialMxn = account(owner, "All scope initial MXN", CurrencyCode.MXN);
        FinancialAccount inactiveMxn = account(owner, "All scope inactive MXN", CurrencyCode.MXN);
        inactiveMxn.setActive(false);
        accountRepository.saveAndFlush(inactiveMxn);
        FinancialAccount usd = account(owner, "All scope USD", CurrencyCode.USD);
        Category expense = category(owner, "All scope expense", CategoryType.EXPENSE, null);

        Budget expenseBudget = budget(
            owner,
            BudgetType.EXPENSE,
            "3000.00",
            LocalDate.of(2026, 1, 1),
            BudgetCategoryScopeMode.ALL,
            Set.of()
        );
        Budget incomeBudget = budget(owner, BudgetType.INCOME, "3000.00", LocalDate.of(2026, 1, 1), BudgetCategoryScopeMode.ALL, Set.of());

        // The account is created after the ALL-scope Budget. It must still participate in an evaluation.
        FinancialAccount laterMxn = account(owner, "All scope later MXN", CurrencyCode.MXN);
        transactionRepository.saveAndFlush(transaction(initialMxn, expense, TransactionFlow.OUT, "100", LocalDate.of(2026, 1, 1)));
        transactionRepository.saveAndFlush(transaction(inactiveMxn, expense, TransactionFlow.OUT, "50", LocalDate.of(2026, 1, 15)));
        transactionRepository.saveAndFlush(transaction(laterMxn, expense, TransactionFlow.OUT, "200", LocalDate.of(2026, 1, 31)));
        transactionRepository.saveAndFlush(transaction(initialMxn, expense, TransactionFlow.OUT, "500", LocalDate.of(2025, 12, 31)));
        transactionRepository.saveAndFlush(transaction(initialMxn, expense, TransactionFlow.OUT, "600", LocalDate.of(2026, 2, 1)));
        transactionRepository.saveAndFlush(transaction(usd, expense, TransactionFlow.OUT, "999", LocalDate.of(2026, 1, 12)));
        transactionRepository.saveAndFlush(transaction(initialMxn, null, TransactionFlow.IN, "400", LocalDate.of(2026, 1, 7)));

        FinancialTransaction transferOut = transaction(initialMxn, expense, TransactionFlow.OUT, "700", LocalDate.of(2026, 1, 20));
        FinancialTransaction transferIn = transaction(laterMxn, null, TransactionFlow.IN, "700", LocalDate.of(2026, 1, 20));
        transactionRepository.saveAndFlush(transferOut);
        transactionRepository.saveAndFlush(transferIn);
        InternalTransfer linkedTransfer = transferRepository.saveAndFlush(
            new InternalTransfer().createdAt(NOW).updatedAt(NOW).outgoingTransaction(transferOut).incomingTransaction(transferIn)
        );

        // Independent arithmetic: 100 + 50 + 200 = 350 expenses; 400 income. Boundaries, USD and linked legs do not qualify.
        BudgetEvaluationResult expenseBeforeUnlink = evaluationService.period(expenseBudget.getId(), 0, ZONE);
        BudgetEvaluationResult incomeBeforeUnlink = evaluationService.period(incomeBudget.getId(), 0, ZONE);
        assertThat(expenseBeforeUnlink.actualAmount()).isEqualByComparingTo("350");
        assertThat(expenseBeforeUnlink.progressPercentage()).isEqualByComparingTo("11.67");
        assertThat(incomeBeforeUnlink.actualAmount()).isEqualByComparingTo("400");

        // Unlinking restores the ordinary economic meaning of both persisted legs without changing either transaction.
        transferRepository.delete(linkedTransfer);
        transferRepository.flush();
        assertThat(evaluationService.period(expenseBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("1050");
        assertThat(evaluationService.period(incomeBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("1100");
    }

    @Test
    void recalculates_selected_account_category_and_tag_scopes_after_financial_record_mutations_without_duplicate_descendants() {
        User owner = currentUser();
        FinancialAccount selected = account(owner, "Selected MXN", CurrencyCode.MXN);
        FinancialAccount outsideSelection = account(owner, "Outside selected MXN", CurrencyCode.MXN);
        Category household = category(owner, "Scoped household", CategoryType.EXPENSE, null);
        Category food = category(owner, "Scoped food", CategoryType.EXPENSE, household);
        Category travel = category(owner, "Scoped travel", CategoryType.EXPENSE, null);
        Category unrelated = category(owner, "Scoped unrelated", CategoryType.EXPENSE, null);
        Tag essential = tag(owner, "Scoped essential");
        Tag recurring = tag(owner, "Scoped recurring");

        FinancialTransaction foodExpense = transaction(selected, food, TransactionFlow.OUT, "100", LocalDate.of(2026, 1, 10));
        foodExpense.addTags(essential);
        transactionRepository.saveAndFlush(foodExpense);
        FinancialTransaction householdExpense = transaction(selected, household, TransactionFlow.OUT, "50", LocalDate.of(2026, 1, 11));
        householdExpense.addTags(essential).addTags(recurring);
        transactionRepository.saveAndFlush(householdExpense);
        FinancialTransaction travelExpense = transaction(selected, travel, TransactionFlow.OUT, "40", LocalDate.of(2026, 1, 12));
        travelExpense.addTags(recurring);
        transactionRepository.saveAndFlush(travelExpense);

        // Selecting both a root and its child is a union, not two joins that duplicate Food.
        Budget anyTagBudget = scopedBudget(
            owner,
            "3000.00",
            BudgetTagMatchMode.ANY,
            selected,
            Set.of(household, food, travel),
            Set.of(essential, recurring)
        );
        Budget allTagBudget = scopedBudget(
            owner,
            "3000.00",
            BudgetTagMatchMode.ALL,
            selected,
            Set.of(household, food, travel),
            Set.of(essential, recurring)
        );

        assertThat(evaluationService.period(anyTagBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("190");
        assertThat(evaluationService.period(allTagBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("50");

        // Tag, amount, account, category and transaction-date corrections all recalculate through the same Analytics query.
        foodExpense.addTags(recurring);
        transactionRepository.saveAndFlush(foodExpense);
        assertThat(evaluationService.period(allTagBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("150");

        foodExpense.setAmount(new BigDecimal("120"));
        transactionRepository.saveAndFlush(foodExpense);
        assertThat(evaluationService.period(allTagBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("170");

        foodExpense.setAccount(outsideSelection);
        transactionRepository.saveAndFlush(foodExpense);
        assertThat(evaluationService.period(allTagBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("50");

        householdExpense.setCategory(unrelated);
        transactionRepository.saveAndFlush(householdExpense);
        assertThat(evaluationService.period(allTagBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("0");

        travelExpense.setTransactionDate(LocalDate.of(2026, 2, 1));
        travelExpense.setPostingDate(LocalDate.of(2026, 2, 1));
        transactionRepository.saveAndFlush(travelExpense);
        assertThat(evaluationService.period(anyTagBudget.getId(), 0, ZONE).actualAmount()).isEqualByComparingTo("0");

        // 3,600 against a 3,000 limit is an exact 120%, and remains an active excess rather than a distinct calculation path.
        FinancialTransaction correctedExpense = transaction(selected, food, TransactionFlow.OUT, "3600", LocalDate.of(2026, 1, 25));
        correctedExpense.addTags(essential).addTags(recurring);
        transactionRepository.saveAndFlush(correctedExpense);
        BudgetEvaluationResult exceeded = evaluationService.period(allTagBudget.getId(), 0, ZONE);
        assertThat(exceeded.actualAmount()).isEqualByComparingTo("3600");
        assertThat(exceeded.progressPercentage()).isEqualByComparingTo("120.00");
        assertThat(exceeded.goalStatus()).isEqualTo(BudgetGoalStatus.EXCEEDED);
        assertThat(exceeded.operationalStatus()).isEqualTo(BudgetPeriodOperationalStatus.ACTIVE);
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
    void applies_current_and_future_revisions_without_proration_or_mutating_historical_financial_results() {
        User owner = currentUser();
        FinancialAccount account = account(owner, "Integrated revision MXN", CurrencyCode.MXN);
        Category expense = category(owner, "Integrated revision expense", CategoryType.EXPENSE, null);
        FinancialTransaction januaryExpense = transaction(account, expense, TransactionFlow.OUT, "50.00", LocalDate.of(2026, 1, 12));
        FinancialTransaction marchExpense = transaction(account, expense, TransactionFlow.OUT, "100.00", LocalDate.of(2026, 3, 8));
        transactionRepository.saveAndFlush(januaryExpense);
        transactionRepository.saveAndFlush(marchExpense);

        Budget budget = budget(owner, BudgetType.EXPENSE, "200.00", LocalDate.of(2026, 1, 1), BudgetCategoryScopeMode.ALL, Set.of());
        BudgetEvaluationResult januaryBefore = evaluationService.period(budget.getId(), 0, ZONE);
        BudgetEvaluationResult marchBefore = evaluationService.current(budget.getId(), ZONE);
        assertThat(januaryBefore.actualAmount()).isEqualByComparingTo("50.00");
        assertThat(januaryBefore.targetAmount()).isEqualByComparingTo("200.00");
        assertThat(januaryBefore.progressPercentage()).isEqualByComparingTo("25.00");
        assertThat(marchBefore.actualAmount()).isEqualByComparingTo("100.00");
        assertThat(marchBefore.targetAmount()).isEqualByComparingTo("200.00");

        BudgetMutationResult currentChange = commandService.scheduleRevision(
            budget.getId(),
            revisionCommand(budget.getVersion(), "integrated-current-revision", true, null, "150.00")
        );
        BudgetEvaluationResult januaryAfterCurrentChange = evaluationService.period(budget.getId(), 0, ZONE);
        BudgetEvaluationResult marchAfterCurrentChange = evaluationService.current(budget.getId(), ZONE);
        assertThat(januaryAfterCurrentChange.revisionId()).isEqualTo(januaryBefore.revisionId());
        assertThat(januaryAfterCurrentChange.targetAmount()).isEqualByComparingTo("200.00");
        assertThat(januaryAfterCurrentChange.actualAmount()).isEqualByComparingTo("50.00");
        assertThat(marchAfterCurrentChange.revisionId()).isEqualTo(currentChange.revisionId());
        assertThat(marchAfterCurrentChange.targetAmount()).isEqualByComparingTo("150.00");
        assertThat(marchAfterCurrentChange.actualAmount()).isEqualByComparingTo("100.00");
        assertThat(marchAfterCurrentChange.progressPercentage()).isEqualByComparingTo("66.67");
        assertThat(marchAfterCurrentChange.remainingAmount()).isEqualByComparingTo("50.00");

        // Financial facts remain live. This correction changes March's actual, not either immutable revision.
        marchExpense.setAmount(new BigDecimal("150.00"));
        transactionRepository.saveAndFlush(marchExpense);
        BudgetEvaluationResult marchAfterFinancialCorrection = evaluationService.current(budget.getId(), ZONE);
        assertThat(marchAfterFinancialCorrection.revisionId()).isEqualTo(currentChange.revisionId());
        assertThat(marchAfterFinancialCorrection.actualAmount()).isEqualByComparingTo("150.00");
        assertThat(marchAfterFinancialCorrection.progressPercentage()).isEqualByComparingTo("100.00");
        assertThat(marchAfterFinancialCorrection.remainingAmount()).isEqualByComparingTo("0.00");

        BudgetMutationResult futureChange = commandService.scheduleRevision(
            budget.getId(),
            revisionCommand(currentChange.version(), "integrated-future-revision", false, LocalDate.of(2026, 4, 1), "180.00")
        );
        BudgetEvaluationResult januaryAfterFutureChange = evaluationService.period(budget.getId(), 0, ZONE);
        BudgetEvaluationResult april = evaluationService.period(budget.getId(), 3, ZONE);
        assertThat(januaryAfterFutureChange.revisionId()).isEqualTo(januaryBefore.revisionId());
        assertThat(januaryAfterFutureChange.actualAmount()).isEqualByComparingTo("50.00");
        assertThat(april.revisionId()).isEqualTo(futureChange.revisionId());
        assertThat(april.targetAmount()).isEqualByComparingTo("180.00");
        assertThat(april.evaluationStatus()).isEqualTo(BudgetEvaluationStatus.NOT_YET_EVALUATED);
        assertThat(april.actualAmount()).isNull();
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

    @Test
    void exposes_the_revision_and_scope_that_were_effective_for_each_bounded_history_period() throws Exception {
        User owner = currentUser();
        FinancialAccount januaryAccount = account(owner, "January detail account", CurrencyCode.MXN);
        Category januaryCategory = category(owner, "January detail category", CategoryType.EXPENSE, null);
        Tag januaryTag = tag(owner, "January detail tag");
        Budget budget = budget(
            owner,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "100.00",
            LocalDate.of(2026, 1, 1),
            BudgetAccountScopeMode.SELECTED,
            Set.of(januaryAccount),
            BudgetCategoryScopeMode.SELECTED,
            Set.of(januaryCategory),
            BudgetTagScopeMode.SELECTED,
            Set.of(januaryTag),
            BudgetTagMatchMode.ALL
        );
        BudgetRevision januaryRevision = budget.getRevisions().get(0);
        BudgetRevision februaryRevision = BudgetRevision.record(
            2,
            configuration(
                "200.00",
                LocalDate.of(2026, 2, 1),
                BudgetAccountScopeMode.ALL,
                BudgetCategoryScopeMode.ALL,
                BudgetTagScopeMode.NONE,
                BudgetTagMatchMode.ANY
            ),
            Set.of(),
            Set.of(),
            Set.of(),
            NOW
        );
        budget.addRevision(februaryRevision);
        revisionRepository.saveAndFlush(februaryRevision);
        scheduleRepository.saveAndFlush(
            BudgetRevisionSchedule.scheduled(budget, februaryRevision, LocalDate.of(2026, 2, 1), 2, "detail-r2", NOW)
        );

        restBudgetMockMvc
            .perform(
                get("/api/budgets/{budgetId}/evaluations", budget.getId())
                    .param("firstPeriodIndex", "0")
                    .param("size", "2")
                    .param("timeZone", ZONE)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evaluations.length()").value(2))
            .andExpect(jsonPath("$.evaluations[0].revisionId").value(januaryRevision.getId()))
            .andExpect(jsonPath("$.evaluations[0].targetAmount").value(100.00))
            .andExpect(jsonPath("$.evaluations[1].revisionId").value(februaryRevision.getId()))
            .andExpect(jsonPath("$.evaluations[1].targetAmount").value(200.00));
        restBudgetMockMvc
            .perform(get("/api/budgets/{budgetId}/effective-revision", budget.getId()).param("periodStart", "2026-01-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(januaryRevision.getId()))
            .andExpect(jsonPath("$.targetAmount").value(100.00))
            .andExpect(jsonPath("$.accountIds[0]").value(januaryAccount.getId()))
            .andExpect(jsonPath("$.categoryIds[0]").value(januaryCategory.getId()))
            .andExpect(jsonPath("$.tagIds[0]").value(januaryTag.getId()))
            .andExpect(jsonPath("$.tagMatchMode").value("ALL"));
        restBudgetMockMvc
            .perform(get("/api/budgets/{budgetId}/effective-revision", budget.getId()).param("periodStart", "2026-02-01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(februaryRevision.getId()))
            .andExpect(jsonPath("$.targetAmount").value(200.00))
            .andExpect(jsonPath("$.accountScopeMode").value("ALL"));

        User foreign = user("detail-foreign", "detail-foreign@example.invalid");
        restBudgetMockMvc
            .perform(
                get("/api/budgets/{budgetId}/evaluations", budget.getId())
                    .with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(foreign.getLogin())
                    )
                    .param("firstPeriodIndex", "0")
                    .param("size", "2")
                    .param("timeZone", ZONE)
            )
            .andExpect(status().isNotFound());
    }

    private Budget budget(
        User owner,
        BudgetType type,
        String target,
        LocalDate anchor,
        BudgetCategoryScopeMode categoryScope,
        Set<Category> categories
    ) {
        return budget(
            owner,
            type,
            CurrencyCode.MXN,
            target,
            anchor,
            BudgetAccountScopeMode.ALL,
            Set.of(),
            categoryScope,
            categories,
            BudgetTagScopeMode.NONE,
            Set.of(),
            BudgetTagMatchMode.ANY
        );
    }

    private Budget scopedBudget(
        User owner,
        String target,
        BudgetTagMatchMode tagMatchMode,
        FinancialAccount account,
        Set<Category> categories,
        Set<Tag> tags
    ) {
        return budget(
            owner,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            target,
            LocalDate.of(2026, 1, 1),
            BudgetAccountScopeMode.SELECTED,
            Set.of(account),
            BudgetCategoryScopeMode.SELECTED,
            categories,
            BudgetTagScopeMode.SELECTED,
            tags,
            tagMatchMode
        );
    }

    private Budget budget(
        User owner,
        BudgetType type,
        CurrencyCode currency,
        String target,
        LocalDate anchor,
        BudgetAccountScopeMode accountScope,
        Set<FinancialAccount> accounts,
        BudgetCategoryScopeMode categoryScope,
        Set<Category> categories,
        BudgetTagScopeMode tagScope,
        Set<Tag> tags,
        BudgetTagMatchMode tagMatchMode
    ) {
        Budget budget = Budget.create(
            owner,
            "Budget " + System.nanoTime(),
            null,
            type,
            currency,
            "create-" + System.nanoTime(),
            "hash",
            NOW
        );
        budget.addInitialRevision(
            BudgetRevision.initial(
                configuration(target, anchor, accountScope, categoryScope, tagScope, tagMatchMode),
                accounts,
                categories,
                tags,
                NOW
            )
        );
        Budget saved = budgetRepository.saveAndFlush(budget);
        scheduleRepository.saveAndFlush(BudgetRevisionSchedule.scheduled(saved, saved.getRevisions().get(0), anchor, 1, "INITIAL", NOW));
        return saved;
    }

    private BudgetRevisionConfiguration configuration(String target, LocalDate anchor) {
        return configuration(target, anchor, BudgetCategoryScopeMode.ALL);
    }

    private BudgetRevisionConfiguration configuration(String target, LocalDate anchor, BudgetCategoryScopeMode categoryScope) {
        return configuration(target, anchor, BudgetAccountScopeMode.ALL, categoryScope, BudgetTagScopeMode.NONE, BudgetTagMatchMode.ANY);
    }

    private BudgetRevisionConfiguration configuration(
        String target,
        LocalDate anchor,
        BudgetAccountScopeMode accountScope,
        BudgetCategoryScopeMode categoryScope,
        BudgetTagScopeMode tagScope,
        BudgetTagMatchMode tagMatchMode
    ) {
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
            accountScope,
            categoryScope,
            tagScope,
            tagMatchMode
        );
    }

    private BudgetRevisionScheduleCommand revisionCommand(
        long expectedVersion,
        String requestId,
        boolean applyToCurrentPeriod,
        LocalDate effectivePeriodStart,
        String targetAmount
    ) {
        return new BudgetRevisionScheduleCommand(
            expectedVersion,
            requestId,
            ZONE,
            applyToCurrentPeriod,
            effectivePeriodStart,
            new BudgetInitialRevisionCommand(
                new BigDecimal(targetAmount),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                LocalDate.of(2026, 1, 1),
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

    private Tag tag(User owner, String name) {
        return tagRepository.saveAndFlush(new Tag().name(name).color("#225588").active(true).createdAt(NOW).updatedAt(NOW).user(owner));
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
