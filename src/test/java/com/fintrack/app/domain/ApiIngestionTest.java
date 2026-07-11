package com.fintrack.app.domain;

import static com.fintrack.app.domain.ApiIngestionTestSamples.*;
import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ApiIngestionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ApiIngestion.class);
        ApiIngestion apiIngestion1 = getApiIngestionSample1();
        ApiIngestion apiIngestion2 = new ApiIngestion();
        assertThat(apiIngestion1).isNotEqualTo(apiIngestion2);

        apiIngestion2.setId(apiIngestion1.getId());
        assertThat(apiIngestion1).isEqualTo(apiIngestion2);

        apiIngestion2 = getApiIngestionSample2();
        assertThat(apiIngestion1).isNotEqualTo(apiIngestion2);
    }

    @Test
    void transactionIngestionTest() {
        ApiIngestion apiIngestion = getApiIngestionRandomSampleGenerator();
        TransactionIngestion transactionIngestionBack = getTransactionIngestionRandomSampleGenerator();

        apiIngestion.setTransactionIngestion(transactionIngestionBack);
        assertThat(apiIngestion.getTransactionIngestion()).isEqualTo(transactionIngestionBack);

        apiIngestion.transactionIngestion(null);
        assertThat(apiIngestion.getTransactionIngestion()).isNull();
    }

    @Test
    void tokenSnapshotFieldsTest() {
        ApiIngestion apiIngestion = getApiIngestionRandomSampleGenerator();
        apiIngestion.setApiTokenIdSnapshot(99L);
        apiIngestion.setApiTokenPrefixSnapshot("ftk_abc");
        apiIngestion.setApiTokenNameSnapshot("My Token");

        assertThat(apiIngestion.getApiTokenIdSnapshot()).isEqualTo(99L);
        assertThat(apiIngestion.getApiTokenPrefixSnapshot()).isEqualTo("ftk_abc");
        assertThat(apiIngestion.getApiTokenNameSnapshot()).isEqualTo("My Token");
    }
}
