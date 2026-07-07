package com.fintrack.app.domain;

import static com.fintrack.app.domain.CategoryTestSamples.*;
import static com.fintrack.app.domain.FinancialAccountTestSamples.*;
import static com.fintrack.app.domain.FinancialSubscriptionTestSamples.*;
import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.IngestionRecordTestSamples.*;
import static com.fintrack.app.domain.InternalTransferTestSamples.*;
import static com.fintrack.app.domain.TagTestSamples.*;
import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FinancialTransactionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(FinancialTransaction.class);
        FinancialTransaction financialTransaction1 = getFinancialTransactionSample1();
        FinancialTransaction financialTransaction2 = new FinancialTransaction();
        assertThat(financialTransaction1).isNotEqualTo(financialTransaction2);

        financialTransaction2.setId(financialTransaction1.getId());
        assertThat(financialTransaction1).isEqualTo(financialTransaction2);

        financialTransaction2 = getFinancialTransactionSample2();
        assertThat(financialTransaction1).isNotEqualTo(financialTransaction2);
    }

    @Test
    void accountTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        FinancialAccount financialAccountBack = getFinancialAccountRandomSampleGenerator();

        financialTransaction.setAccount(financialAccountBack);
        assertThat(financialTransaction.getAccount()).isEqualTo(financialAccountBack);

        financialTransaction.account(null);
        assertThat(financialTransaction.getAccount()).isNull();
    }

    @Test
    void categoryTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        Category categoryBack = getCategoryRandomSampleGenerator();

        financialTransaction.setCategory(categoryBack);
        assertThat(financialTransaction.getCategory()).isEqualTo(categoryBack);

        financialTransaction.category(null);
        assertThat(financialTransaction.getCategory()).isNull();
    }

    @Test
    void financialSubscriptionTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        FinancialSubscription financialSubscriptionBack = getFinancialSubscriptionRandomSampleGenerator();

        financialTransaction.setFinancialSubscription(financialSubscriptionBack);
        assertThat(financialTransaction.getFinancialSubscription()).isEqualTo(financialSubscriptionBack);

        financialTransaction.financialSubscription(null);
        assertThat(financialTransaction.getFinancialSubscription()).isNull();
    }

    @Test
    void transactionIngestionTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        TransactionIngestion transactionIngestionBack = getTransactionIngestionRandomSampleGenerator();

        financialTransaction.setTransactionIngestion(transactionIngestionBack);
        assertThat(financialTransaction.getTransactionIngestion()).isEqualTo(transactionIngestionBack);

        financialTransaction.transactionIngestion(null);
        assertThat(financialTransaction.getTransactionIngestion()).isNull();
    }

    @Test
    void tagsTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        Tag tagBack = getTagRandomSampleGenerator();

        financialTransaction.addTags(tagBack);
        assertThat(financialTransaction.getTags()).containsOnly(tagBack);

        financialTransaction.removeTags(tagBack);
        assertThat(financialTransaction.getTags()).doesNotContain(tagBack);

        financialTransaction.tags(new HashSet<>(Set.of(tagBack)));
        assertThat(financialTransaction.getTags()).containsOnly(tagBack);

        financialTransaction.setTags(new HashSet<>());
        assertThat(financialTransaction.getTags()).doesNotContain(tagBack);
    }

    @Test
    void outgoingInternalTransferTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        InternalTransfer internalTransferBack = getInternalTransferRandomSampleGenerator();

        financialTransaction.setOutgoingInternalTransfer(internalTransferBack);
        assertThat(financialTransaction.getOutgoingInternalTransfer()).isEqualTo(internalTransferBack);
        assertThat(internalTransferBack.getOutgoingTransaction()).isEqualTo(financialTransaction);

        financialTransaction.outgoingInternalTransfer(null);
        assertThat(financialTransaction.getOutgoingInternalTransfer()).isNull();
        assertThat(internalTransferBack.getOutgoingTransaction()).isNull();
    }

    @Test
    void incomingInternalTransferTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        InternalTransfer internalTransferBack = getInternalTransferRandomSampleGenerator();

        financialTransaction.setIncomingInternalTransfer(internalTransferBack);
        assertThat(financialTransaction.getIncomingInternalTransfer()).isEqualTo(internalTransferBack);
        assertThat(internalTransferBack.getIncomingTransaction()).isEqualTo(financialTransaction);

        financialTransaction.incomingInternalTransfer(null);
        assertThat(financialTransaction.getIncomingInternalTransfer()).isNull();
        assertThat(internalTransferBack.getIncomingTransaction()).isNull();
    }

    @Test
    void ingestionRecordTest() {
        FinancialTransaction financialTransaction = getFinancialTransactionRandomSampleGenerator();
        IngestionRecord ingestionRecordBack = getIngestionRecordRandomSampleGenerator();

        financialTransaction.setIngestionRecord(ingestionRecordBack);
        assertThat(financialTransaction.getIngestionRecord()).isEqualTo(ingestionRecordBack);
        assertThat(ingestionRecordBack.getFinancialTransaction()).isEqualTo(financialTransaction);

        financialTransaction.ingestionRecord(null);
        assertThat(financialTransaction.getIngestionRecord()).isNull();
        assertThat(ingestionRecordBack.getFinancialTransaction()).isNull();
    }
}
