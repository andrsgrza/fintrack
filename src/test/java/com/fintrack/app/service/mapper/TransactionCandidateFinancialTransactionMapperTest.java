package com.fintrack.app.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.domain.Category;
import com.fintrack.app.domain.FinancialAccount;
import com.fintrack.app.domain.FinancialTransaction;
import com.fintrack.app.domain.Tag;
import com.fintrack.app.domain.TransactionCandidate;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionCandidateFinancialTransactionMapperTest {

    private final TransactionCandidateFinancialTransactionMapper mapper = new TransactionCandidateFinancialTransactionMapper();

    @Test
    void mapsBaseCandidateFieldsToFinancialTransaction() {
        Instant now = Instant.parse("2026-09-15T12:00:00Z");
        FinancialAccount account = new FinancialAccount().id(1L).name("Checking");
        Category category = new Category().id(2L).name("Transport");
        Tag firstTag = new Tag().id(3L).name("Business");
        Tag secondTag = new Tag().id(4L).name("Reimbursable");
        TransactionCandidate candidate = new TransactionCandidate()
            .account(account)
            .transactionDate(LocalDate.of(2026, 9, 14))
            .postingDate(LocalDate.of(2026, 9, 15))
            .description("Uber")
            .amount(new BigDecimal("123.45"))
            .flow(TransactionFlow.OUT)
            .externalReference("ref-1")
            .notes("note")
            .category(category)
            .tags(new LinkedHashSet<>(Set.of(firstTag, secondTag)));

        FinancialTransaction financialTransaction = mapper.toFinancialTransaction(candidate, TransactionOrigin.FILE_IMPORT, now);

        assertThat(financialTransaction.getAccount()).isSameAs(account);
        assertThat(financialTransaction.getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(financialTransaction.getPostingDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(financialTransaction.getDescription()).isEqualTo("Uber");
        assertThat(financialTransaction.getAmount()).isEqualByComparingTo("123.45");
        assertThat(financialTransaction.getFlow()).isEqualTo(TransactionFlow.OUT);
        assertThat(financialTransaction.getOrigin()).isEqualTo(TransactionOrigin.FILE_IMPORT);
        assertThat(financialTransaction.getExternalReference()).isEqualTo("ref-1");
        assertThat(financialTransaction.getNotes()).isEqualTo("note");
        assertThat(financialTransaction.getCategory()).isSameAs(category);
        assertThat(financialTransaction.getTags()).extracting(Tag::getId).containsExactlyInAnyOrder(3L, 4L);
        assertThat(financialTransaction.getCreatedAt()).isEqualTo(now);
        assertThat(financialTransaction.getUpdatedAt()).isEqualTo(now);
        assertThat(financialTransaction.getFinancialSubscription()).isNull();
        assertThat(financialTransaction.getTransactionIngestion()).isNull();
    }

    @Test
    void mapsManualOriginFromParameter() {
        Instant now = Instant.parse("2026-09-15T12:00:00Z");
        TransactionCandidate candidate = new TransactionCandidate();

        FinancialTransaction financialTransaction = mapper.toFinancialTransaction(candidate, TransactionOrigin.MANUAL, now);

        assertThat(financialTransaction.getOrigin()).isEqualTo(TransactionOrigin.MANUAL);
    }

    @Test
    void nullTagsAreSafeAndCandidateIsNotMutated() {
        Instant now = Instant.parse("2026-09-15T12:00:00Z");
        TransactionCandidate candidate = new TransactionCandidate();
        candidate.setTags(null);

        FinancialTransaction financialTransaction = mapper.toFinancialTransaction(candidate, TransactionOrigin.MANUAL, now);

        assertThat(financialTransaction.getTags()).isEmpty();
        assertThat(candidate.getTags()).isNull();
    }
}
