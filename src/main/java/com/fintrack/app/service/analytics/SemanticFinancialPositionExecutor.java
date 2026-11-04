package com.fintrack.app.service.analytics;

import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.enumeration.AccountType;
import com.fintrack.app.service.FinancialAccountBalanceService;
import com.fintrack.app.service.dto.FinancialAccountBalanceDTO;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The sole governed cross-account position: compatible asset book balances less recorded credit-card debt at the
 * context's one effective cutoff. It never combines currencies or sums observations from different dates.
 */
@Component
class SemanticFinancialPositionExecutor {

    private static final Set<AccountType> ASSET_ACCOUNT_TYPES = EnumSet.of(AccountType.DEBIT, AccountType.CASH, AccountType.INVESTMENT);

    private final FinancialAccountBalanceService balanceService;

    SemanticFinancialPositionExecutor(FinancialAccountBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    SemanticAnalyticsResult execute(SemanticAnalyticsExecutionScope scope) {
        List<FinancialAccount> accounts = scope.accounts().stream().filter(this::isPositionAccount).toList();
        if (accounts.isEmpty()) {
            return SemanticAnalyticsResultFactory.result(
                scope,
                List.of(),
                SemanticAnalyticsResultFactory.columns(scope.definition()),
                List.of(
                    new SemanticAnalyticsWarning(
                        "NO_APPLICABLE_POSITION_ACCOUNTS",
                        "No asset or credit-card account is available in the selected context."
                    )
                ),
                SemanticResultAvailability.NOT_APPLICABLE
            );
        }

        List<FinancialAccount> opened = accounts
            .stream()
            .filter(account -> !scope.context().through().isBefore(account.getInitialBalanceDate()))
            .toList();
        Map<Long, FinancialAccountBalanceDTO> balances = balanceService.calculateBalances(opened, scope.context().through());
        Map<String, CurrencyPosition> positions = new LinkedHashMap<>();
        for (FinancialAccount account : accounts) {
            CurrencyPosition position = positions.computeIfAbsent(account.getCurrency().name(), ignored -> new CurrencyPosition());
            if (scope.context().through().isBefore(account.getInitialBalanceDate())) {
                position.unavailable = true;
                continue;
            }
            BigDecimal contribution = contribution(account, balances.get(account.getId()));
            if (contribution == null) {
                position.unavailable = true;
            } else {
                position.amount = position.amount.add(contribution);
                position.hasValue = true;
            }
        }

        SemanticAggregateProjection projection = scope.definition().projections().get(0);
        List<SemanticAnalyticsResultRow> rows = positions
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> row(entry.getKey(), projection, entry.getValue()))
            .toList();
        return SemanticAnalyticsResultFactory.result(
            scope,
            rows,
            SemanticAnalyticsResultFactory.columns(scope.definition()),
            List.of(
                new SemanticAnalyticsWarning(
                    "FINANCIAL_POSITION_AT_CUTOFF",
                    "Financial position uses compatible asset balances less recorded credit-card debt at one observation cutoff; currencies remain separate."
                )
            )
        );
    }

    private boolean isPositionAccount(FinancialAccount account) {
        return ASSET_ACCOUNT_TYPES.contains(account.getAccountType()) || account.getAccountType() == AccountType.CREDIT_CARD;
    }

    private BigDecimal contribution(FinancialAccount account, FinancialAccountBalanceDTO balance) {
        if (balance == null) return null;
        if (ASSET_ACCOUNT_TYPES.contains(account.getAccountType())) return balance.getCurrentBalance();
        return balance.getCurrentDebt() == null ? null : balance.getCurrentDebt().negate();
    }

    private SemanticAnalyticsResultRow row(String currency, SemanticAggregateProjection projection, CurrencyPosition position) {
        SemanticResultAvailability availability = !position.hasValue
            ? SemanticResultAvailability.UNAVAILABLE
            : position.unavailable
                ? SemanticResultAvailability.PARTIAL
                : position.amount.signum() == 0 ? SemanticResultAvailability.ZERO : SemanticResultAvailability.COMPLETE;
        return new SemanticAnalyticsResultRow(
            List.of(),
            currency,
            List.of(
                new SemanticAnalyticsResultValue(
                    projection.alias(),
                    position.hasValue ? position.amount : null,
                    SemanticUnit.MONEY,
                    availability,
                    false
                )
            )
        );
    }

    private static final class CurrencyPosition {

        private BigDecimal amount = BigDecimal.ZERO;
        private boolean hasValue;
        private boolean unavailable;
    }
}
