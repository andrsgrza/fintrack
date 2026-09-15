package com.fintrack.app.service.mapper;

import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Internal mapper for converting a reviewed TransactionCandidate into a new posted FinancialTransaction shell.
 *
 * This mapper only copies common transaction fields. Callers still own lifecycle validation, idempotency,
 * source-specific links, status transitions, persistence, and transactional boundaries.
 */
@Service
public class TransactionCandidateFinancialTransactionMapper {

    public FinancialTransaction toFinancialTransaction(TransactionCandidate candidate, TransactionOrigin origin, Instant now) {
        FinancialTransaction financialTransaction = new FinancialTransaction()
            .transactionDate(candidate.getTransactionDate())
            .postingDate(candidate.getPostingDate())
            .description(candidate.getDescription())
            .amount(candidate.getAmount())
            .flow(candidate.getFlow())
            .origin(origin)
            .externalReference(candidate.getExternalReference())
            .notes(candidate.getNotes())
            .createdAt(now)
            .updatedAt(now)
            .account(candidate.getAccount())
            .category(candidate.getCategory())
            .financialSubscription(null);

        if (candidate.getTags() != null) {
            candidate.getTags().forEach(financialTransaction::addTags);
        }

        return financialTransaction;
    }
}
