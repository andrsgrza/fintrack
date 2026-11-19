package com.fintrack.app.service.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fintrack.app.domain.Budget;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.BudgetAccountScopeMode;
import com.fintrack.app.domain.enumeration.BudgetAlignment;
import com.fintrack.app.domain.enumeration.BudgetCategoryScopeMode;
import com.fintrack.app.domain.enumeration.BudgetIntervalUnit;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceMode;
import com.fintrack.app.domain.enumeration.BudgetRecurrenceTermination;
import com.fintrack.app.domain.enumeration.BudgetTagMatchMode;
import com.fintrack.app.domain.enumeration.BudgetTagScopeMode;
import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.BudgetRepository;
import com.fintrack.app.repository.BudgetRevisionScheduleRepository;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.repository.UserRepository;
import com.fintrack.app.service.CurrentUserService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetCreationServiceTest {

    private static final String OWNER_LOGIN = "budget-owner";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-15T18:00:00Z"), ZoneOffset.UTC);

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetRevisionScheduleRepository revisionScheduleRepository;

    @Mock
    private FinancialAccountRepository financialAccountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private BudgetTimeZoneService budgetTimeZoneService;

    @Mock
    private BudgetNotificationPreferenceService notificationPreferenceService;

    private BudgetCreationService service;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(9L);
        owner.setLogin(OWNER_LOGIN);
        service = new BudgetCreationService(
            budgetRepository,
            revisionScheduleRepository,
            financialAccountRepository,
            categoryRepository,
            tagRepository,
            userRepository,
            currentUserService,
            CLOCK,
            budgetTimeZoneService,
            notificationPreferenceService
        );
        when(currentUserService.getCurrentUser()).thenReturn(owner);
        when(userRepository.findOneForBudgetCreationByLogin(OWNER_LOGIN)).thenReturn(Optional.of(owner));
        when(budgetRepository.findOneWithRevisionsByUserLoginAndCreationRequestId(any(), any())).thenReturn(Optional.empty());
        lenient().when(notificationPreferenceService.normalizeForCreation(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(revisionScheduleRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOneImmutableRevisionWithOwnerScopedSelections() {
        FinancialAccount account = new FinancialAccount();
        account.setId(11L);
        account.setCurrency(CurrencyCode.MXN);
        Category category = new Category();
        category.setId(12L);
        category.setCategoryType(CategoryType.EXPENSE);
        Tag tag = new Tag();
        tag.setId(13L);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(11L, OWNER_LOGIN)).thenReturn(Optional.of(account));
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(12L, OWNER_LOGIN)).thenReturn(Optional.of(category));
        when(tagRepository.findOneWithToOneRelationshipsByIdAndUserLogin(13L, OWNER_LOGIN)).thenReturn(Optional.of(tag));
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BudgetCreated created = service.create(command("create-1", LocalDate.of(2026, 3, 16), Set.of(11L), Set.of(12L), Set.of(13L)));

        ArgumentCaptor<Budget> budgetCaptor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).saveAndFlush(budgetCaptor.capture());
        verify(revisionScheduleRepository).saveAndFlush(any());
        Budget saved = budgetCaptor.getValue();
        assertThat(saved.getUser()).isSameAs(owner);
        assertThat(saved.getRevisions())
            .singleElement()
            .satisfies(revision -> {
                assertThat(revision.getRevisionNumber()).isEqualTo(1);
                assertThat(revision.getAccounts()).containsExactly(account);
                assertThat(revision.getCategories()).containsExactly(category);
                assertThat(revision.getTags()).containsExactly(tag);
                assertThat(revision.getRecordedAt()).isEqualTo(CLOCK.instant());
            });
        assertThat(created.initialRevision().accountIds()).containsExactly(11L);
        assertThat(created.initialRevision().categoryIds()).containsExactly(12L);
        assertThat(created.initialRevision().tagIds()).containsExactly(13L);
    }

    @Test
    void rejectsASelectedScopeWithoutAnExplicitSelection() {
        BudgetCreateCommand command = command("missing-account", LocalDate.of(2026, 3, 16), Set.of(), Set.of(), Set.of());

        assertThatThrownBy(() -> service.create(command))
            .isInstanceOf(BudgetCommandException.class)
            .hasMessageContaining("SELECTED account scope");
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnOverPreciseInitialTargetBeforePersistingAnything() {
        BudgetInitialRevisionCommand invalidRevision = new BudgetInitialRevisionCommand(
            new BigDecimal("100.999"),
            1,
            BudgetIntervalUnit.MONTH,
            BudgetAlignment.ANCHORED,
            LocalDate.of(2026, 3, 16),
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
        );
        BudgetCreateCommand invalid = new BudgetCreateCommand(
            "Precise food",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "too-many-decimals",
            "America/Mexico_City",
            invalidRevision
        );

        assertThatThrownBy(() -> service.create(invalid))
            .isInstanceOf(BudgetCommandException.class)
            .hasMessageContaining("at most two decimal places");
        verify(budgetRepository, never()).saveAndFlush(any());
        verify(notificationPreferenceService, never()).createForBudget(any(), any(), any());
    }

    @Test
    void rejectsAnOwnedAccountWhoseCurrencyDoesNotMatchTheBudgetBeforeAnyBudgetIsPersisted() {
        FinancialAccount usdAccount = new FinancialAccount();
        usdAccount.setId(11L);
        usdAccount.setCurrency(CurrencyCode.USD);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(11L, OWNER_LOGIN)).thenReturn(
            Optional.of(usdAccount)
        );

        assertThatThrownBy(() -> service.create(command("wrong-currency", LocalDate.of(2026, 3, 16), Set.of(11L), Set.of(), Set.of())))
            .isInstanceOf(BudgetCommandException.class)
            .hasMessageContaining("budget currency");

        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsAnOwnedCategoryThatIsIncompatibleWithTheBudgetObjectiveBeforeAnyBudgetIsPersisted() {
        FinancialAccount mxnAccount = new FinancialAccount();
        mxnAccount.setId(11L);
        mxnAccount.setCurrency(CurrencyCode.MXN);
        Category incomeCategory = new Category();
        incomeCategory.setId(12L);
        incomeCategory.setCategoryType(CategoryType.INCOME);
        when(financialAccountRepository.findOneWithToOneRelationshipsByIdAndUserLogin(11L, OWNER_LOGIN)).thenReturn(
            Optional.of(mxnAccount)
        );
        when(categoryRepository.findOneWithToOneRelationshipsByIdAndUserLogin(12L, OWNER_LOGIN)).thenReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> service.create(command("wrong-category", LocalDate.of(2026, 3, 16), Set.of(11L), Set.of(12L), Set.of())))
            .isInstanceOf(BudgetCommandException.class)
            .hasMessageContaining("compatible with this budget type");

        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsAPastAnchorWithoutChangingTheServerCreationTimestamp() {
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BudgetCreateCommand command = new BudgetCreateCommand(
            "Past budget",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "past-anchor",
            "America/Mexico_City",
            new BudgetInitialRevisionCommand(
                new BigDecimal("100.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 3, 14),
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

        service.create(command);

        ArgumentCaptor<Budget> budgetCaptor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).saveAndFlush(budgetCaptor.capture());
        Budget created = budgetCaptor.getValue();
        assertThat(created.getCreatedAt()).isEqualTo(CLOCK.instant());
        assertThat(created.getRevisions())
            .singleElement()
            .satisfies(revision -> assertThat(revision.getAnchorDate()).isEqualTo(LocalDate.of(2026, 3, 14)));
    }

    @Test
    void replaysTheOriginalCommandAndRejectsTheSameIdForDifferentContent() {
        Budget existing = Budget.create(
            owner,
            "Food",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "repeat",
            "different-hash",
            CLOCK.instant()
        );
        existing.addInitialRevision(
            com.fintrack.app.domain.BudgetRevision.initial(
                new com.fintrack.app.domain.BudgetRevisionConfiguration(
                    new BigDecimal("100.00"),
                    1,
                    BudgetIntervalUnit.MONTH,
                    BudgetAlignment.ANCHORED,
                    LocalDate.of(2026, 3, 16),
                    BudgetRecurrenceMode.ONCE,
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
            )
        );
        when(budgetRepository.findOneWithRevisionsByUserLoginAndCreationRequestId(OWNER_LOGIN, "repeat")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(command("repeat", LocalDate.of(2026, 3, 16), Set.of(), Set.of(), Set.of())))
            .isInstanceOf(BudgetCommandException.class)
            .extracting("code")
            .isEqualTo(BudgetCommandException.Code.IDEMPOTENCY_CONFLICT);
        verify(budgetRepository, never()).saveAndFlush(any());
    }

    @Test
    void persistsExplicitNotificationPreferencesInTheSameCreationFlow() {
        when(budgetRepository.saveAndFlush(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BudgetNotificationPreferenceInput input = new BudgetNotificationPreferenceInput(
            true,
            Set.of(new BigDecimal("50.0000"), new BigDecimal("80.0000")),
            true
        );
        when(notificationPreferenceService.normalizeForCreation(input)).thenReturn(input);
        BudgetCreateCommand base = new BudgetCreateCommand(
            "Preference budget",
            null,
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            "preferences-at-create",
            "America/Mexico_City",
            new BudgetInitialRevisionCommand(
                new BigDecimal("100.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                LocalDate.of(2026, 3, 16),
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
        BudgetCreateCommand command = new BudgetCreateCommand(
            base.name(),
            base.description(),
            base.type(),
            base.currency(),
            base.clientRequestId(),
            base.timeZone(),
            base.initialRevision(),
            input
        );

        service.create(command);

        verify(notificationPreferenceService).createForBudget(
            any(Budget.class),
            org.mockito.ArgumentMatchers.same(input),
            org.mockito.ArgumentMatchers.eq(CLOCK.instant())
        );
    }

    private static BudgetCreateCommand command(
        String requestId,
        LocalDate anchorDate,
        Set<Long> accountIds,
        Set<Long> categoryIds,
        Set<Long> tagIds
    ) {
        return new BudgetCreateCommand(
            "Food",
            "Monthly food plan",
            BudgetType.EXPENSE,
            CurrencyCode.MXN,
            requestId,
            "America/Mexico_City",
            new BudgetInitialRevisionCommand(
                new BigDecimal("1000.00"),
                1,
                BudgetIntervalUnit.MONTH,
                BudgetAlignment.ANCHORED,
                anchorDate,
                BudgetRecurrenceMode.RECURRING,
                BudgetRecurrenceTermination.NONE,
                null,
                null,
                BudgetAccountScopeMode.SELECTED,
                accountIds,
                BudgetCategoryScopeMode.SELECTED,
                categoryIds,
                BudgetTagScopeMode.SELECTED,
                tagIds,
                BudgetTagMatchMode.ALL
            )
        );
    }
}
