package com.fintrack.app.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.IntegrationTest;
import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.User;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CategoryType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.CategoryRepository;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.repository.TagRepository;
import com.fintrack.app.security.AuthoritiesConstants;
import com.fintrack.app.web.rest.TestUtil;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/** Real PostgreSQL ownership contracts for the clean Analytics validation boundary. */
@IntegrationTest
@Transactional
@WithMockUser(username = "user")
class FinancialAnalyticsValidatorIT {

    @Autowired
    private FinancialAnalyticsValidator validator;

    @Autowired
    private FinancialAccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private EntityManager em;

    @Test
    void acceptsOwnedInactiveReferencesAndNeverMutatesPersistedData() {
        FinancialAccount account = account("user", false);
        Category category = category("user", false);
        Tag tag = tag("user", false);
        long accountsBefore = accountRepository.count();
        long categoriesBefore = categoryRepository.count();
        long tagsBefore = tagRepository.count();

        AnalyticsValidationResult result = validator.validate(
            request(
                List.of(account.getId()),
                List.of(
                    new AnalyticalPredicate(
                        FinancialSemanticCatalog.CATEGORY,
                        FinancialSemanticCatalog.EQUALS,
                        List.of(category.getId().toString())
                    ),
                    new AnalyticalPredicate(FinancialSemanticCatalog.TAG, FinancialSemanticCatalog.IN, List.of(tag.getId().toString()))
                )
            )
        );

        assertThat(result.isValid()).isTrue();
        assertThat(result.normalizedRequest().orElseThrow().resolvedContext().accountIds()).containsExactly(account.getId());
        assertThat(accountRepository.count()).isEqualTo(accountsBefore);
        assertThat(categoryRepository.count()).isEqualTo(categoriesBefore);
        assertThat(tagRepository.count()).isEqualTo(tagsBefore);
    }

    @Test
    void rejectsEveryForeignReferenceEvenWhenItExists() {
        FinancialAccount foreignAccount = account("admin", true);
        Category foreignCategory = category("admin", true);
        Tag foreignTag = tag("admin", true);

        AnalyticsValidationResult result = validator.validate(
            request(
                List.of(foreignAccount.getId()),
                List.of(
                    new AnalyticalPredicate(
                        FinancialSemanticCatalog.CATEGORY,
                        FinancialSemanticCatalog.EQUALS,
                        List.of(foreignCategory.getId().toString())
                    ),
                    new AnalyticalPredicate(
                        FinancialSemanticCatalog.TAG,
                        FinancialSemanticCatalog.EQUALS,
                        List.of(foreignTag.getId().toString())
                    )
                )
            )
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(
                AnalyticsDiagnosticCode.FOREIGN_ACCOUNT_REFERENCE,
                AnalyticsDiagnosticCode.FOREIGN_CATEGORY_REFERENCE,
                AnalyticsDiagnosticCode.FOREIGN_TAG_REFERENCE
            );
    }

    @Test
    @WithMockUser(username = "admin", authorities = AuthoritiesConstants.ADMIN)
    void keepsAdministratorsInTheirOwnOwnershipScope() {
        FinancialAccount adminAccount = account("admin", true);
        FinancialAccount otherUserAccount = account("user", true);

        AnalyticsValidationResult ownReference = validator.validate(request(List.of(adminAccount.getId()), List.of()));
        AnalyticsValidationResult foreignReference = validator.validate(request(List.of(otherUserAccount.getId()), List.of()));

        assertThat(ownReference.isValid()).isTrue();
        assertThat(foreignReference.diagnostics())
            .extracting(AnalyticsDiagnostic::code)
            .contains(AnalyticsDiagnosticCode.FOREIGN_ACCOUNT_REFERENCE);
    }

    @Test
    void rejectsUnknownPersistedReferenceIdsWithTypedDiagnostics() {
        long unknownId = Long.MAX_VALUE;

        AnalyticsValidationResult result = validator.validate(
            request(
                List.of(unknownId),
                List.of(
                    new AnalyticalPredicate(
                        FinancialSemanticCatalog.CATEGORY,
                        FinancialSemanticCatalog.EQUALS,
                        List.of(Long.toString(unknownId))
                    ),
                    new AnalyticalPredicate(
                        FinancialSemanticCatalog.TAG,
                        FinancialSemanticCatalog.EQUALS,
                        List.of(Long.toString(unknownId))
                    )
                )
            )
        );

        assertThat(result.isValid()).isFalse();
        assertThat(result.diagnostics()).extracting(AnalyticsDiagnostic::code).contains(AnalyticsDiagnosticCode.INVALID_REFERENCE_ID);
    }

    private FinancialAnalyticsExecutionRequest request(List<Long> accountIds, List<AnalyticalPredicate> predicates) {
        return new FinancialAnalyticsExecutionRequest(
            AnalyticsSchema.VERSION,
            new QueryDefinition(
                AnalyticsSchema.VERSION,
                AnalyticsSchema.SEMANTIC_MODEL,
                List.of("EXPENSE_TOTAL"),
                List.of(),
                predicates,
                FinancialSemanticCatalog.TRANSACTION_DATE,
                null,
                AnalyticsSchema.DEFAULT_LIMIT
            ),
            new VisualizationContext(
                AnalyticsSchema.VERSION,
                new VisualizationTime("ABSOLUTE_RANGE", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                accountIds,
                List.of("MXN")
            )
        );
    }

    private FinancialAccount account(String ownerLogin, boolean active) {
        String suffix = Long.toString(System.nanoTime());
        return accountRepository.saveAndFlush(
            new FinancialAccount()
                .name("Analytics account " + suffix)
                .accountType(AccountType.DEBIT)
                .currency(CurrencyCode.MXN)
                .initialBalance(BigDecimal.ZERO)
                .initialBalanceDate(LocalDate.of(2026, 1, 1))
                .active(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(user(ownerLogin))
        );
    }

    private Category category(String ownerLogin, boolean active) {
        return categoryRepository.saveAndFlush(
            new Category()
                .name("Analytics category " + System.nanoTime())
                .categoryType(CategoryType.EXPENSE)
                .color("#224466")
                .active(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(user(ownerLogin))
        );
    }

    private Tag tag(String ownerLogin, boolean active) {
        return tagRepository.saveAndFlush(
            new Tag()
                .name("Analytics tag " + System.nanoTime())
                .color("#224466")
                .active(active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .user(user(ownerLogin))
        );
    }

    private User user(String login) {
        return TestUtil.findAll(em, User.class).stream().filter(user -> login.equals(user.getLogin())).findFirst().orElseThrow();
    }
}
