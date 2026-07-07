package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class FinancialAccountDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(FinancialAccountDTO.class);
        FinancialAccountDTO financialAccountDTO1 = new FinancialAccountDTO();
        financialAccountDTO1.setId(1L);
        FinancialAccountDTO financialAccountDTO2 = new FinancialAccountDTO();
        assertThat(financialAccountDTO1).isNotEqualTo(financialAccountDTO2);
        financialAccountDTO2.setId(financialAccountDTO1.getId());
        assertThat(financialAccountDTO1).isEqualTo(financialAccountDTO2);
        financialAccountDTO2.setId(2L);
        assertThat(financialAccountDTO1).isNotEqualTo(financialAccountDTO2);
        financialAccountDTO1.setId(null);
        assertThat(financialAccountDTO1).isNotEqualTo(financialAccountDTO2);
    }
}
