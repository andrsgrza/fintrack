package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class InternalTransferDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(InternalTransferDTO.class);
        InternalTransferDTO internalTransferDTO1 = new InternalTransferDTO();
        internalTransferDTO1.setId(1L);
        InternalTransferDTO internalTransferDTO2 = new InternalTransferDTO();
        assertThat(internalTransferDTO1).isNotEqualTo(internalTransferDTO2);
        internalTransferDTO2.setId(internalTransferDTO1.getId());
        assertThat(internalTransferDTO1).isEqualTo(internalTransferDTO2);
        internalTransferDTO2.setId(2L);
        assertThat(internalTransferDTO1).isNotEqualTo(internalTransferDTO2);
        internalTransferDTO1.setId(null);
        assertThat(internalTransferDTO1).isNotEqualTo(internalTransferDTO2);
    }
}
