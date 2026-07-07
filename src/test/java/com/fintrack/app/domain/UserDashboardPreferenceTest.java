package com.fintrack.app.domain;

import static com.fintrack.app.domain.UserDashboardPreferenceTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class UserDashboardPreferenceTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(UserDashboardPreference.class);
        UserDashboardPreference userDashboardPreference1 = getUserDashboardPreferenceSample1();
        UserDashboardPreference userDashboardPreference2 = new UserDashboardPreference();
        assertThat(userDashboardPreference1).isNotEqualTo(userDashboardPreference2);

        userDashboardPreference2.setId(userDashboardPreference1.getId());
        assertThat(userDashboardPreference1).isEqualTo(userDashboardPreference2);

        userDashboardPreference2 = getUserDashboardPreferenceSample2();
        assertThat(userDashboardPreference1).isNotEqualTo(userDashboardPreference2);
    }
}
