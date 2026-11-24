package com.fintrack.app.service.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The POC's single execution entry point; dispatch is by validated semantic dataset, never by a named metric. */
@Service
@Transactional(readOnly = true)
public class SemanticAnalyticsExecutionService {

    private final SemanticAnalyticsValidator validator;
    private final SemanticTransactionLedgerExecutor transactionLedgerExecutor;
    private final SemanticFinancialPositionExecutor financialPositionExecutor;
    private final SemanticAccountPositionExecutor accountPositionExecutor;
    private final SemanticCreditAccountExecutor creditAccountExecutor;
    private final SemanticInternalTransferExecutor internalTransferExecutor;

    public SemanticAnalyticsExecutionService(
        SemanticAnalyticsValidator validator,
        SemanticTransactionLedgerExecutor transactionLedgerExecutor,
        SemanticFinancialPositionExecutor financialPositionExecutor,
        SemanticAccountPositionExecutor accountPositionExecutor,
        SemanticCreditAccountExecutor creditAccountExecutor,
        SemanticInternalTransferExecutor internalTransferExecutor
    ) {
        this.validator = validator;
        this.transactionLedgerExecutor = transactionLedgerExecutor;
        this.financialPositionExecutor = financialPositionExecutor;
        this.accountPositionExecutor = accountPositionExecutor;
        this.creditAccountExecutor = creditAccountExecutor;
        this.internalTransferExecutor = internalTransferExecutor;
    }

    public SemanticAnalyticsResult execute(SemanticAnalyticsExecutionRequest request) {
        SemanticAnalyticsExecutionScope scope = validator.validateOrThrow(request);
        return execute(scope);
    }

    /**
     * Narrow server-only path for a trusted aggregate such as Budget. There is intentionally no REST endpoint or
     * owner-id parameter: the owner comes from {@link SemanticAnalyticsTrustedOwnerContext} built from persistence.
     */
    public SemanticAnalyticsResult executeForTrustedOwner(
        SemanticAnalyticsExecutionRequest request,
        SemanticAnalyticsTrustedOwnerContext trustedOwner
    ) {
        SemanticAnalyticsExecutionScope scope = validator.validateForTrustedOwnerOrThrow(request, trustedOwner);
        return execute(scope);
    }

    private SemanticAnalyticsResult execute(SemanticAnalyticsExecutionScope scope) {
        return switch (scope.definition().dataset()) {
            case TRANSACTION_LEDGER -> transactionLedgerExecutor.execute(scope);
            case FINANCIAL_POSITION_SNAPSHOT -> financialPositionExecutor.execute(scope);
            case ACCOUNT_POSITION_SNAPSHOT -> accountPositionExecutor.execute(scope);
            case CREDIT_ACCOUNT_SNAPSHOT -> creditAccountExecutor.execute(scope);
            case INTERNAL_TRANSFER_EVENT -> internalTransferExecutor.execute(scope);
        };
    }
}
