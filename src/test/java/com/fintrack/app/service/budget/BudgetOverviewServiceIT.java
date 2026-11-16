package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionConfiguration;
import com.fintrack.app.domain.BudgetRevisionSchedule;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.BudgetAccountScopeMode;
import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetCategoryScopeMode;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import com.fintrack.app.domain.enumeration.BudgetTagMatchMode;
import com.fintrack.app.domain.enumeration.BudgetTagScopeMode;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.FinancialTransactionRepository;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

@IntegrationTest
@Transactional
@AutoConfigureMockMvc
@WithMockUser(username = "user")
@Import(BudgetOverviewServiceIT.FixedClockConfiguration.class)
class BudgetOverviewServiceIT {

    private static final Instant NOW = Instant.parse("2026-03-15T18:00:00Z");
    private static final String ZONE = "America/Mexico_City";

    @Autowired
    private MockMvc restBudgetMockMvc;

    @Autowired
    private BudgetOverviewService overviewService;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private BudgetRevisionScheduleRepository scheduleRepository;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private FinancialTransactionRepository transactionRepository;

    @Autowired
    private EntityManager em;

    @Test
    void exposes_one_owner_scoped_overview_with_reference_specific_periods_and_no_post_lifetime_evaluation() throws Exception {
        User owner = currentUser();
        FinancialAccount account = account(owner);
        transactionRepository.saveAndFlush(transaction(account, "30.00", LocalDate.of(2026, 3, 10)));
        Budget active = budget(
            owner,
            "Active budget",
            LocalDate.of(2026, 3, 1),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null
        );
        budget(owner, "Scheduled budget", LocalDate.of(2026, 4, 1), BudgetRecurrenceMode.RECURRING, BudgetRecurrenceTermination.NONE, null);
        budget(owner, "Completed budget", LocalDate.of(2026, 1, 1), BudgetRecurrenceMode.ONCE, BudgetRecurrenceTermination.NONE, null);

        BudgetOverview overview = overviewService.overview(LocalDate.of(2026, 3, 15), ZONE, null, null);
        assertThat(overview.budgets()).hasSize(3);
        assertThat(overview.budgets())
            .filteredOn(item -> item.name().equals("Active budget"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.hasPeriodAtReferenceDate()).isTrue();
                assertThat(item.evaluation().actualAmount()).isEqualByComparingTo("30.00");
            });
        assertThat(overview.budgets())
            .filteredOn(item -> item.name().equals("Scheduled budget"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.hasPeriodAtReferenceDate()).isFalse();
                assertThat(item.operationalStatus()).isEqualTo(com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus.SCHEDULED);
            });
        assertThat(overview.budgets())
            .filteredOn(item -> item.name().equals("Completed budget"))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.hasPeriodAtReferenceDate()).isFalse();
                assertThat(item.operationalStatus()).isEqualTo(com.fintrack.app.domain.enumeration.BudgetPeriodOperationalStatus.COMPLETED);
            });

        restBudgetMockMvc
            .perform(get("/api/budgets/overview").param("referenceDate", "2026-03-15").param("timeZone", ZONE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.referenceDate").value("2026-03-15"))
            .andExpect(jsonPath("$.budgets.length()").value(3));

        // A status filter is applied to the same reference date, not a hidden global "current" date.
        restBudgetMockMvc
            .perform(get("/api/budgets/overview").param("referenceDate", "2026-03-15").param("timeZone", ZONE).param("status", "ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgets.length()").value(1))
            .andExpect(jsonPath("$.budgets[0].budgetId").value(active.getId()));

        // An authenticated different owner cannot discover the current user's card or its financial result.
        restBudgetMockMvc
            .perform(get("/api/budgets/overview").with(user("other-owner")).param("referenceDate", "2026-03-15").param("timeZone", ZONE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgets").isEmpty());
    }

    private Budget budget(
        User owner,
        String name,
        LocalDate anchor,
        BudgetRecurrenceMode recurrence,
        BudgetRecurrenceTermination termination,
        Integer count
    ) {
        Budget budget = Budget.create(owner, name, null, BudgetType.EXPENSE, CurrencyCode.MXN, "overview-" + name, "hash", NOW);
        BudgetRevision revision = BudgetRevision.initial(
            new BudgetRevisionConfiguration(
                new BigDecimal("100.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                anchor,
                recurrence,
                termination,
                count,
                null,
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
        budget.addInitialRevision(revision);
        Budget saved = budgetRepository.saveAndFlush(budget);
        scheduleRepository.saveAndFlush(BudgetRevisionSchedule.scheduled(saved, saved.getRevisions().get(0), anchor, 1, "INITIAL", NOW));
        return saved;
    }

    private FinancialAccount account(User owner) {
        return accountRepository.saveAndFlush(
            new FinancialAccount()
                .name("Overview MXN")
                .institutionName("Overview Bank")
                .accountType(AccountType.DEBIT)
                .currency(CurrencyCode.MXN)
                .initialBalance(BigDecimal.ZERO)
                .initialBalanceDate(LocalDate.of(2025, 1, 1))
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .user(owner)
        );
    }

    private FinancialTransaction transaction(FinancialAccount account, String amount, LocalDate date) {
        return new FinancialTransaction()
            .account(account)
            .transactionDate(date)
            .postingDate(date)
            .description("Overview expense")
            .amount(new BigDecimal(amount))
            .flow(TransactionFlow.OUT)
            .origin(TransactionOrigin.MANUAL)
            .createdAt(NOW)
            .updatedAt(NOW);
    }

    private User currentUser() {
        return TestUtil.findAll(em, User.class).stream().filter(user -> "user".equals(user.getLogin())).findFirst().orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock budgetOverviewClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
