package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.repository.FinancialAccountRepository;
import com.fintrack.app.service.CurrentUserService;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole public entry point for governed financial calculations. It validates the canonical request, scopes it to
 * the authenticated owner, then dispatches to one specialization selected by the semantic catalog.
 */
@Service
@Transactional(readOnly = true)
public class FinancialAnalyticsExecutionService {

    private final FinancialAnalyticsValidator validator;
    private final FinancialSemanticCatalog catalog;
    private final CurrentUserService currentUserService;
    private final FinancialAccountRepository accountRepository;
    private final FinancialAnalyticsTransactionExecutor transactionExecutor;
    private final FinancialAnalyticsPositionExecutor positionExecutor;
    private final FinancialAnalyticsTransferExecutor transferExecutor;

    public FinancialAnalyticsExecutionService(
        FinancialAnalyticsValidator validator,
        FinancialSemanticCatalog catalog,
        CurrentUserService currentUserService,
        FinancialAccountRepository accountRepository,
        FinancialAnalyticsTransactionExecutor transactionExecutor,
        FinancialAnalyticsPositionExecutor positionExecutor,
        FinancialAnalyticsTransferExecutor transferExecutor
    ) {
        this.validator = validator;
        this.catalog = catalog;
        this.currentUserService = currentUserService;
        this.accountRepository = accountRepository;
        this.transactionExecutor = transactionExecutor;
        this.positionExecutor = positionExecutor;
        this.transferExecutor = transferExecutor;
    }

    public FinancialAnalyticsResult execute(FinancialAnalyticsExecutionRequest request) {
        ValidatedFinancialAnalyticsRequest validated = validator.validateOrThrow(request);
        String ownerLogin = currentUserService.getCurrentUserLogin();
        FinancialAnalyticsExecutionScope scope = new FinancialAnalyticsExecutionScope(
            validated,
            ownerLogin,
            resolveAccounts(validated, ownerLogin)
        );
        FinancialMetricDefinition firstMetric = catalog.metric(validated.request().definition().measures().get(0)).orElseThrow();
        return switch (firstMetric.executorSpecialization()) {
            case TRANSACTION_AGGREGATE -> transactionExecutor.execute(scope);
            case ACCOUNT_SNAPSHOT -> positionExecutor.execute(scope);
            case TRANSFER_RELATIONSHIP_AGGREGATE -> transferExecutor.execute(scope);
        };
    }

    private List<FinancialAccount> resolveAccounts(ValidatedFinancialAnalyticsRequest validated, String ownerLogin) {
        VisualizationContext context = validated.request().context();
        Set<Long> requestedAccountIds = new LinkedHashSet<>(context.accountIds());
        Set<CurrencyCode> requestedCurrencies = currencyCodes(context.currencyCodes());
        List<AnalyticalPredicate> predicates = validated.request().definition().analyticalPredicates();
        Set<Long> predicateAccountIds = referenceIds(predicates, FinancialSemanticCatalog.ACCOUNT);
        Set<AccountType> predicateTypes = enumValues(predicates, FinancialSemanticCatalog.ACCOUNT_TYPE, AccountType.class);
        Set<CurrencyCode> predicateCurrencies = enumValues(predicates, FinancialSemanticCatalog.CURRENCY, CurrencyCode.class);
        List<FinancialAccount> ownedAccounts = accountRepository.findAllWithToOneRelationshipsByUserLogin(ownerLogin);
        if (
            !requestedCurrencies.isEmpty() &&
            ownedAccounts.stream().noneMatch(account -> requestedCurrencies.contains(account.getCurrency()))
        ) {
            throw new FinancialAnalyticsValidationException(
                List.of(
                    new AnalyticsDiagnostic(
                        AnalyticsDiagnosticCode.INVALID_CONTEXT,
                        "context.currencyCodes",
                        "The selected currencies are not available in the current user's account scope."
                    )
                )
            );
        }
        return ownedAccounts
            .stream()
            .filter(account -> requestedAccountIds.isEmpty() || requestedAccountIds.contains(account.getId()))
            .filter(account -> requestedCurrencies.isEmpty() || requestedCurrencies.contains(account.getCurrency()))
            .filter(account -> predicateAccountIds.isEmpty() || predicateAccountIds.contains(account.getId()))
            .filter(account -> predicateTypes.isEmpty() || predicateTypes.contains(account.getAccountType()))
            .filter(account -> predicateCurrencies.isEmpty() || predicateCurrencies.contains(account.getCurrency()))
            .sorted(java.util.Comparator.comparing(FinancialAccount::getId))
            .toList();
    }

    private Set<Long> referenceIds(Collection<AnalyticalPredicate> predicates, String attribute) {
        return predicates
            .stream()
            .filter(predicate -> attribute.equals(predicate.attributeId()))
            .flatMap(predicate -> predicate.values().stream())
            .map(Long::valueOf)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private <T extends Enum<T>> Set<T> enumValues(Collection<AnalyticalPredicate> predicates, String attribute, Class<T> type) {
        return predicates
            .stream()
            .filter(predicate -> attribute.equals(predicate.attributeId()))
            .flatMap(predicate -> predicate.values().stream())
            .map(value -> Enum.valueOf(type, value))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<CurrencyCode> currencyCodes(Collection<String> values) {
        return values.stream().map(CurrencyCode::valueOf).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
