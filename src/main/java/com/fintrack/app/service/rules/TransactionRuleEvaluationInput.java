package com.fintrack.app.service.rules;

import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public record TransactionRuleEvaluationInput(
    String userLogin,
    String description,
    BigDecimal amount,
    TransactionFlow flow,
    String externalReference,
    TransactionOrigin origin,
    LocalDate transactionDate,
    LocalDate postingDate,
    Long accountId,
    Long currentCategoryId,
    String currentCategoryName,
    Set<Long> currentTagIds,
    Map<Long, String> currentTagNames
) {
    public TransactionRuleEvaluationInput {
        currentTagIds = currentTagIds == null ? Collections.emptySet() : Set.copyOf(currentTagIds);
        currentTagNames = currentTagNames == null ? Collections.emptyMap() : Map.copyOf(currentTagNames);
    }
}
