package com.fintrack.app.domain;

import static com.fintrack.app.domain.ApiIngestionTestSamples.*;
import static com.fintrack.app.domain.FileIngestionTestSamples.*;
import static com.fintrack.app.domain.FinancialAccountTestSamples.*;
import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.IngestionRecordTestSamples.*;
import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransactionIngestionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionIngestion.class);
        TransactionIngestion transactionIngestion1 = getTransactionIngestionSample1();
        TransactionIngestion transactionIngestion2 = new TransactionIngestion();
        assertThat(transactionIngestion1).isNotEqualTo(transactionIngestion2);

        transactionIngestion2.setId(transactionIngestion1.getId());
        assertThat(transactionIngestion1).isEqualTo(transactionIngestion2);

        transactionIngestion2 = getTransactionIngestionSample2();
        assertThat(transactionIngestion1).isNotEqualTo(transactionIngestion2);
    }

    @Test
    void accountTest() {
        TransactionIngestion transactionIngestion = getTransactionIngestionRandomSampleGenerator();
        FinancialAccount financialAccountBack = getFinancialAccountRandomSampleGenerator();

        transactionIngestion.setAccount(financialAccountBack);
        assertThat(transactionIngestion.getAccount()).isEqualTo(financialAccountBack);

        transactionIngestion.account(null);
        assertThat(transactionIngestion.getAccount()).isNull();
    }

    @Test
    void fileIngestionTest() {
        TransactionIngestion transactionIngestion = getTransactionIngestionRandomSampleGenerator();
        FileIngestion fileIngestionBack = getFileIngestionRandomSampleGenerator();

        transactionIngestion.setFileIngestion(fileIngestionBack);
        assertThat(transactionIngestion.getFileIngestion()).isEqualTo(fileIngestionBack);
        assertThat(fileIngestionBack.getTransactionIngestion()).isEqualTo(transactionIngestion);

        transactionIngestion.fileIngestion(null);
        assertThat(transactionIngestion.getFileIngestion()).isNull();
        assertThat(fileIngestionBack.getTransactionIngestion()).isNull();
    }

    @Test
    void apiIngestionTest() {
        TransactionIngestion transactionIngestion = getTransactionIngestionRandomSampleGenerator();
        ApiIngestion apiIngestionBack = getApiIngestionRandomSampleGenerator();

        transactionIngestion.setApiIngestion(apiIngestionBack);
        assertThat(transactionIngestion.getApiIngestion()).isEqualTo(apiIngestionBack);
        assertThat(apiIngestionBack.getTransactionIngestion()).isEqualTo(transactionIngestion);

        transactionIngestion.apiIngestion(null);
        assertThat(transactionIngestion.getApiIngestion()).isNull();
        assertThat(apiIngestionBack.getTransactionIngestion()).isNull();
    }

    @Test
    void financialTransactionsTest() {
        TransactionIngestion transactionIngestion = getTransactionIngestionRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        transactionIngestion.addFinancialTransactions(financialTransactionBack);
        assertThat(transactionIngestion.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getTransactionIngestion()).isEqualTo(transactionIngestion);

        transactionIngestion.removeFinancialTransactions(financialTransactionBack);
        assertThat(transactionIngestion.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getTransactionIngestion()).isNull();

        transactionIngestion.financialTransactions(new HashSet<>(Set.of(financialTransactionBack)));
        assertThat(transactionIngestion.getFinancialTransactions()).containsOnly(financialTransactionBack);
        assertThat(financialTransactionBack.getTransactionIngestion()).isEqualTo(transactionIngestion);

        transactionIngestion.setFinancialTransactions(new HashSet<>());
        assertThat(transactionIngestion.getFinancialTransactions()).doesNotContain(financialTransactionBack);
        assertThat(financialTransactionBack.getTransactionIngestion()).isNull();
    }

    @Test
    void recordsTest() {
        TransactionIngestion transactionIngestion = getTransactionIngestionRandomSampleGenerator();
        IngestionRecord ingestionRecordBack = getIngestionRecordRandomSampleGenerator();

        transactionIngestion.addRecords(ingestionRecordBack);
        assertThat(transactionIngestion.getRecords()).containsOnly(ingestionRecordBack);
        assertThat(ingestionRecordBack.getTransactionIngestion()).isEqualTo(transactionIngestion);

        transactionIngestion.removeRecords(ingestionRecordBack);
        assertThat(transactionIngestion.getRecords()).doesNotContain(ingestionRecordBack);
        assertThat(ingestionRecordBack.getTransactionIngestion()).isNull();

        transactionIngestion.records(new HashSet<>(Set.of(ingestionRecordBack)));
        assertThat(transactionIngestion.getRecords()).containsOnly(ingestionRecordBack);
        assertThat(ingestionRecordBack.getTransactionIngestion()).isEqualTo(transactionIngestion);

        transactionIngestion.setRecords(new HashSet<>());
        assertThat(transactionIngestion.getRecords()).doesNotContain(ingestionRecordBack);
        assertThat(ingestionRecordBack.getTransactionIngestion()).isNull();
    }
}
