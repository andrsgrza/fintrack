package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TransactionRuleConditionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(TransactionRuleConditionDTO.class);
        TransactionRuleConditionDTO transactionRuleConditionDTO1 = new TransactionRuleConditionDTO();
        transactionRuleConditionDTO1.setId(1L);
        TransactionRuleConditionDTO transactionRuleConditionDTO2 = new TransactionRuleConditionDTO();
        assertThat(transactionRuleConditionDTO1).isNotEqualTo(transactionRuleConditionDTO2);
        transactionRuleConditionDTO2.setId(transactionRuleConditionDTO1.getId());
        assertThat(transactionRuleConditionDTO1).isEqualTo(transactionRuleConditionDTO2);
        transactionRuleConditionDTO2.setId(2L);
        assertThat(transactionRuleConditionDTO1).isNotEqualTo(transactionRuleConditionDTO2);
        transactionRuleConditionDTO1.setId(null);
        assertThat(transactionRuleConditionDTO1).isNotEqualTo(transactionRuleConditionDTO2);
    }
}
