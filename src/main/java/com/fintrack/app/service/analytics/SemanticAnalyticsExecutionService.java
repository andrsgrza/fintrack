package com.fintrack.app.service.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The POC's single execution entry point; dispatch is by validated semantic dataset, never by a named metric. */
@Service
@Transactional(readOnly = true)
public class SemanticAnalyticsExecutionService {

    private final SemanticAnalyticsValidator validator;
    private final SemanticTransactionLedgerExecutor transactionLedgerExecutor;
    private final SemanticAccountPositionExecutor accountPositionExecutor;

    public SemanticAnalyticsExecutionService(
        SemanticAnalyticsValidator validator,
        SemanticTransactionLedgerExecutor transactionLedgerExecutor,
        SemanticAccountPositionExecutor accountPositionExecutor
    ) {
        this.validator = validator;
        this.transactionLedgerExecutor = transactionLedgerExecutor;
        this.accountPositionExecutor = accountPositionExecutor;
    }

    public SemanticAnalyticsResult execute(SemanticAnalyticsExecutionRequest request) {
        SemanticAnalyticsExecutionScope scope = validator.validateOrThrow(request);
        return switch (scope.definition().dataset()) {
            case TRANSACTION_LEDGER -> transactionLedgerExecutor.execute(scope);
            case ACCOUNT_POSITION_SNAPSHOT -> accountPositionExecutor.execute(scope);
        };
    }
}
