package com.fintrack.app.domain;

import static com.fintrack.app.domain.CreditAccountDetailsTestSamples.*;
import static com.fintrack.app.domain.FinancialAccountTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CreditAccountDetailsTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(CreditAccountDetails.class);
        CreditAccountDetails creditAccountDetails1 = getCreditAccountDetailsSample1();
        CreditAccountDetails creditAccountDetails2 = new CreditAccountDetails();
        assertThat(creditAccountDetails1).isNotEqualTo(creditAccountDetails2);

        creditAccountDetails2.setId(creditAccountDetails1.getId());
        assertThat(creditAccountDetails1).isEqualTo(creditAccountDetails2);

        creditAccountDetails2 = getCreditAccountDetailsSample2();
        assertThat(creditAccountDetails1).isNotEqualTo(creditAccountDetails2);
    }

    @Test
    void accountTest() {
        CreditAccountDetails creditAccountDetails = getCreditAccountDetailsRandomSampleGenerator();
        FinancialAccount financialAccountBack = getFinancialAccountRandomSampleGenerator();

        creditAccountDetails.setAccount(financialAccountBack);
        assertThat(creditAccountDetails.getAccount()).isEqualTo(financialAccountBack);

        creditAccountDetails.account(null);
        assertThat(creditAccountDetails.getAccount()).isNull();
    }
}
