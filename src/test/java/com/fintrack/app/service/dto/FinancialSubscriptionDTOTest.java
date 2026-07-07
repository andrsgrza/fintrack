package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class FinancialSubscriptionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(FinancialSubscriptionDTO.class);
        FinancialSubscriptionDTO financialSubscriptionDTO1 = new FinancialSubscriptionDTO();
        financialSubscriptionDTO1.setId(1L);
        FinancialSubscriptionDTO financialSubscriptionDTO2 = new FinancialSubscriptionDTO();
        assertThat(financialSubscriptionDTO1).isNotEqualTo(financialSubscriptionDTO2);
        financialSubscriptionDTO2.setId(financialSubscriptionDTO1.getId());
        assertThat(financialSubscriptionDTO1).isEqualTo(financialSubscriptionDTO2);
        financialSubscriptionDTO2.setId(2L);
        assertThat(financialSubscriptionDTO1).isNotEqualTo(financialSubscriptionDTO2);
        financialSubscriptionDTO1.setId(null);
        assertThat(financialSubscriptionDTO1).isNotEqualTo(financialSubscriptionDTO2);
    }
}
