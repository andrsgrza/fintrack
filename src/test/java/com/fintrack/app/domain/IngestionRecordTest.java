package com.fintrack.app.domain;

import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.IngestionRecordTestSamples.*;
import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class IngestionRecordTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(IngestionRecord.class);
        IngestionRecord ingestionRecord1 = getIngestionRecordSample1();
        IngestionRecord ingestionRecord2 = new IngestionRecord();
        assertThat(ingestionRecord1).isNotEqualTo(ingestionRecord2);

        ingestionRecord2.setId(ingestionRecord1.getId());
        assertThat(ingestionRecord1).isEqualTo(ingestionRecord2);

        ingestionRecord2 = getIngestionRecordSample2();
        assertThat(ingestionRecord1).isNotEqualTo(ingestionRecord2);
    }

    @Test
    void financialTransactionTest() {
        IngestionRecord ingestionRecord = getIngestionRecordRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        ingestionRecord.setFinancialTransaction(financialTransactionBack);
        assertThat(ingestionRecord.getFinancialTransaction()).isEqualTo(financialTransactionBack);

        ingestionRecord.financialTransaction(null);
        assertThat(ingestionRecord.getFinancialTransaction()).isNull();
    }

    @Test
    void transactionIngestionTest() {
        IngestionRecord ingestionRecord = getIngestionRecordRandomSampleGenerator();
        TransactionIngestion transactionIngestionBack = getTransactionIngestionRandomSampleGenerator();

        ingestionRecord.setTransactionIngestion(transactionIngestionBack);
        assertThat(ingestionRecord.getTransactionIngestion()).isEqualTo(transactionIngestionBack);

        ingestionRecord.transactionIngestion(null);
        assertThat(ingestionRecord.getTransactionIngestion()).isNull();
    }
}
