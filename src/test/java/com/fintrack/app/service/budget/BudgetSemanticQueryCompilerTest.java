package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.BudgetRevision;
import com.fintrack.app.domain.BudgetRevisionConfiguration;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.*;
import com.fintrack.app.service.analytics.SemanticAnalyticsValidator;
import com.fintrack.app.service.analytics.SemanticQueryDefinition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BudgetSemanticQueryCompilerTest {

    @Mock
    private SemanticAnalyticsValidator validator;

    private BudgetSemanticQueryCompiler compiler;
    private Budget budget;

    @BeforeEach
    void setUp() {
        // It is March 1st in UTC but still February 28th in the explicitly supplied owner zone.
        compiler = new BudgetSemanticQueryCompiler(validator, Clock.fixed(Instant.parse("2026-03-01T00:30:00Z"), ZoneOffset.UTC));
        lenient()
            .when(validator.normalizeDefinitionOrThrow(any()))
            .thenAnswer(invocation -> invocation.<SemanticQueryDefinition>getArgument(0));
        User owner = new User();
        owner.setId(7L);
        owner.setLogin("budget-owner");
        budget = Budget.create(owner, "Food", null, BudgetType.EXPENSE, CurrencyCode.MXN, "create", "hash", Instant.EPOCH);
        ReflectionTestUtils.setField(budget, "id", 9L);
        budget.addInitialRevision(
            BudgetRevision.initial(
                new BudgetRevisionConfiguration(
                    BigDecimal.TEN,
                    1,
                    BudgetIntervalUnit.MONTH,
                    BudgetAlignment.NATURAL,
                    LocalDate.of(2026, 2, 1),
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
            )
        );
    }

    @Test
    void caps_an_in_progress_period_at_the_injected_owner_local_date_not_the_server_default_zone() {
        BudgetSemanticQueryCompilation result = compiler.compile(
            budget,
            budget.getRevisions().get(0),
            new BudgetPeriod(0, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
            ZoneId.of("America/Mexico_City")
        );

        assertThat(result).isInstanceOf(BudgetSemanticQueryCompilation.Compiled.class);
        BudgetSemanticQueryCompilation.Compiled compiled = (BudgetSemanticQueryCompilation.Compiled) result;
        assertThat(compiled.context().time().from()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(compiled.context().time().through()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(compiled.context().currencyCodes()).containsExactly("MXN");
    }

    @Test
    void rejects_a_future_period_instead_of_turning_it_into_an_empty_or_zero_result() {
        BudgetSemanticQueryCompilation result = compiler.compile(
            budget,
            budget.getRevisions().get(0),
            new BudgetPeriod(1, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
            ZoneId.of("America/Mexico_City")
        );

        assertThat(result).isInstanceOf(BudgetSemanticQueryCompilation.Failed.class);
        assertThat(((BudgetSemanticQueryCompilation.Failed) result).reason()).isEqualTo(
            BudgetSemanticCompilationFailure.INVALID_EFFECTIVE_PERIOD
        );
    }

    @Test
    void reports_an_inaccessible_or_cross_currency_selected_account_without_broadening_to_all_accounts() {
        User otherOwner = new User();
        otherOwner.setId(8L);
        otherOwner.setLogin("another-owner");
        FinancialAccount inaccessibleAccount = new FinancialAccount();
        inaccessibleAccount.setId(11L);
        inaccessibleAccount.setUser(otherOwner);
        inaccessibleAccount.setCurrency(CurrencyCode.USD);
        BudgetRevision invalidScope = BudgetRevision.record(
            2,
            new BudgetRevisionConfiguration(
                BigDecimal.TEN,
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.NATURAL,
                LocalDate.of(2026, 2, 1),
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.SELECTED,
                BudgetCategoryScopeMode.ALL,
                BudgetTagScopeMode.NONE,
                BudgetTagMatchMode.ANY
            ),
            Set.of(inaccessibleAccount),
            Set.of(),
            Set.of(),
            Instant.EPOCH
        );
        budget.addRevision(invalidScope);

        BudgetSemanticQueryCompilation result = compiler.compile(
            budget,
            invalidScope,
            new BudgetPeriod(0, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)),
            ZoneId.of("America/Mexico_City")
        );

        assertThat(result).isInstanceOf(BudgetSemanticQueryCompilation.Failed.class);
        assertThat(((BudgetSemanticQueryCompilation.Failed) result).reason()).isEqualTo(
            BudgetSemanticCompilationFailure.MISSING_OR_INACCESSIBLE_REFERENCE
        );
    }
}
