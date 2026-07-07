package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionIngestionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionIngestionDTO.class);
        TransactionIngestionDTO transactionIngestionDTO1 = new TransactionIngestionDTO();
        transactionIngestionDTO1.setId(1L);
        TransactionIngestionDTO transactionIngestionDTO2 = new TransactionIngestionDTO();
        assertThat(transactionIngestionDTO1).isNotEqualTo(transactionIngestionDTO2);
        transactionIngestionDTO2.setId(transactionIngestionDTO1.getId());
        assertThat(transactionIngestionDTO1).isEqualTo(transactionIngestionDTO2);
        transactionIngestionDTO2.setId(2L);
        assertThat(transactionIngestionDTO1).isNotEqualTo(transactionIngestionDTO2);
        transactionIngestionDTO1.setId(null);
        assertThat(transactionIngestionDTO1).isNotEqualTo(transactionIngestionDTO2);
    }
}
