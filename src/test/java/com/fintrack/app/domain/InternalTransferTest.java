package com.fintrack.app.domain;

import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;
import static com.fintrack.app.domain.InternalTransferTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class InternalTransferTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(InternalTransfer.class);
        InternalTransfer internalTransfer1 = getInternalTransferSample1();
        InternalTransfer internalTransfer2 = new InternalTransfer();
        assertThat(internalTransfer1).isNotEqualTo(internalTransfer2);

        internalTransfer2.setId(internalTransfer1.getId());
        assertThat(internalTransfer1).isEqualTo(internalTransfer2);

        internalTransfer2 = getInternalTransferSample2();
        assertThat(internalTransfer1).isNotEqualTo(internalTransfer2);
    }

    @Test
    void outgoingTransactionTest() {
        InternalTransfer internalTransfer = getInternalTransferRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        internalTransfer.setOutgoingTransaction(financialTransactionBack);
        assertThat(internalTransfer.getOutgoingTransaction()).isEqualTo(financialTransactionBack);

        internalTransfer.outgoingTransaction(null);
        assertThat(internalTransfer.getOutgoingTransaction()).isNull();
    }

    @Test
    void incomingTransactionTest() {
        InternalTransfer internalTransfer = getInternalTransferRandomSampleGenerator();
        FinancialTransaction financialTransactionBack = getFinancialTransactionRandomSampleGenerator();

        internalTransfer.setIncomingTransaction(financialTransactionBack);
        assertThat(internalTransfer.getIncomingTransaction()).isEqualTo(financialTransactionBack);

        internalTransfer.incomingTransaction(null);
        assertThat(internalTransfer.getIncomingTransaction()).isNull();
    }
}
