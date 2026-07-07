package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CreditAccountDetailsDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(CreditAccountDetailsDTO.class);
        CreditAccountDetailsDTO creditAccountDetailsDTO1 = new CreditAccountDetailsDTO();
        creditAccountDetailsDTO1.setId(1L);
        CreditAccountDetailsDTO creditAccountDetailsDTO2 = new CreditAccountDetailsDTO();
        assertThat(creditAccountDetailsDTO1).isNotEqualTo(creditAccountDetailsDTO2);
        creditAccountDetailsDTO2.setId(creditAccountDetailsDTO1.getId());
        assertThat(creditAccountDetailsDTO1).isEqualTo(creditAccountDetailsDTO2);
        creditAccountDetailsDTO2.setId(2L);
        assertThat(creditAccountDetailsDTO1).isNotEqualTo(creditAccountDetailsDTO2);
        creditAccountDetailsDTO1.setId(null);
        assertThat(creditAccountDetailsDTO1).isNotEqualTo(creditAccountDetailsDTO2);
    }
}
