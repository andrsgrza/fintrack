package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

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
    private BudgetNotificationPreferenceRepository preferenceRepository;

    @Autowired
    private BudgetThresholdClaimRepository claimRepository;

    @Autowired
    private BudgetNotificationOutboxRepository outboxRepository;

    @Autowired
    private BudgetNotificationRepository notificationRepository;

    @Autowired
    private BudgetTimeZoneService timeZoneService;

    @Autowired
    private BudgetThresholdNotificationService thresholdService;

    @Autowired
    private BudgetNotificationOutboxWorker outboxWorker;

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

    @Test
    @Transactional
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
        Budget budget = budget(owner);
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

        assertThat(claimRepository.findAll()).hasSize(3);
        assertThat(outboxRepository.findAll()).hasSize(2);
        assertThat(outboxWorker.processPending()).isEqualTo(2);
        assertThat(notificationRepository.findAll()).hasSize(2);
        assertThat(outboxRepository.findAll()).allSatisfy(event ->
            assertThat(event.getState()).isEqualTo(BudgetNotificationOutboxState.MATERIALIZED)
        );
    }

    private Budget budget(User owner) {
        BudgetRevisionConfiguration configuration = new BudgetRevisionConfiguration(
            new BigDecimal("100.00"),
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.NATURAL,
            LocalDate.of(2026, 3, 1),
            BudgetRecurrenceMode.RECURRING,
            BudgetRecurrenceTermination.NONE,
            null,
            null,
            BudgetAccountScopeMode.ALL,
            BudgetCategoryScopeMode.ALL,
            BudgetTagScopeMode.NONE,
            BudgetTagMatchMode.ANY
        );
        Budget budget = Budget.create(
            owner,
            "Notification budget",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "notification-create",
            "hash",
            NOW
        );
        budget.addInitialRevision(BudgetRevision.initial(configuration, Set.of(), Set.of(), Set.of(), NOW));
        Budget saved = budgetRepository.saveAndFlush(budget);
        scheduleRepository.saveAndFlush(
            BudgetRevisionSchedule.scheduled(saved, saved.getRevisions().get(0), LocalDate.of(2026, 3, 1), 1, "INITIAL", NOW)
        );
        return saved;
    }

    private FinancialAccount account(User owner) {
        return accountRepository.saveAndFlush(
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
    }

    private Category category(User owner) {
        return categoryRepository.saveAndFlush(
            new Category()
                .name("Notification expense")
                .categoryType(CategoryType.EXPENSE)
                .color("#225588")
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .user(owner)
        );
    }

    private FinancialTransaction transaction(FinancialAccount account, Category category, String amount) {
        return new FinancialTransaction()
            .account(account)
            .category(category)
            .transactionDate(LocalDate.of(2026, 3, 15))
            .postingDate(LocalDate.of(2026, 3, 15))
            .description("Notification expense " + amount)
            .amount(new BigDecimal(amount))
            .flow(TransactionFlow.OUT)
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
