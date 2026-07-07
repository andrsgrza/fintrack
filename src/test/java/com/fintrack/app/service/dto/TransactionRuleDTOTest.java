package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionRuleDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionRuleDTO.class);
        TransactionRuleDTO transactionRuleDTO1 = new TransactionRuleDTO();
        transactionRuleDTO1.setId(1L);
        TransactionRuleDTO transactionRuleDTO2 = new TransactionRuleDTO();
        assertThat(transactionRuleDTO1).isNotEqualTo(transactionRuleDTO2);
        transactionRuleDTO2.setId(transactionRuleDTO1.getId());
        assertThat(transactionRuleDTO1).isEqualTo(transactionRuleDTO2);
        transactionRuleDTO2.setId(2L);
        assertThat(transactionRuleDTO1).isNotEqualTo(transactionRuleDTO2);
        transactionRuleDTO1.setId(null);
        assertThat(transactionRuleDTO1).isNotEqualTo(transactionRuleDTO2);
    }
}
