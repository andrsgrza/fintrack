package com.fintrack.app.domain;

import static com.fintrack.app.domain.FileIngestionTestSamples.*;
import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class FileIngestionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(FileIngestion.class);
        FileIngestion fileIngestion1 = getFileIngestionSample1();
        FileIngestion fileIngestion2 = new FileIngestion();
        assertThat(fileIngestion1).isNotEqualTo(fileIngestion2);

        fileIngestion2.setId(fileIngestion1.getId());
        assertThat(fileIngestion1).isEqualTo(fileIngestion2);

        fileIngestion2 = getFileIngestionSample2();
        assertThat(fileIngestion1).isNotEqualTo(fileIngestion2);
    }

    @Test
    void transactionIngestionTest() {
        FileIngestion fileIngestion = getFileIngestionRandomSampleGenerator();
        TransactionIngestion transactionIngestionBack = getTransactionIngestionRandomSampleGenerator();

        fileIngestion.setTransactionIngestion(transactionIngestionBack);
        assertThat(fileIngestion.getTransactionIngestion()).isEqualTo(transactionIngestionBack);

        fileIngestion.transactionIngestion(null);
        assertThat(fileIngestion.getTransactionIngestion()).isNull();
    }
}
