package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class UserDashboardPreferenceDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(UserDashboardPreferenceDTO.class);
        UserDashboardPreferenceDTO userDashboardPreferenceDTO1 = new UserDashboardPreferenceDTO();
        userDashboardPreferenceDTO1.setId(1L);
        UserDashboardPreferenceDTO userDashboardPreferenceDTO2 = new UserDashboardPreferenceDTO();
        assertThat(userDashboardPreferenceDTO1).isNotEqualTo(userDashboardPreferenceDTO2);
        userDashboardPreferenceDTO2.setId(userDashboardPreferenceDTO1.getId());
        assertThat(userDashboardPreferenceDTO1).isEqualTo(userDashboardPreferenceDTO2);
        userDashboardPreferenceDTO2.setId(2L);
        assertThat(userDashboardPreferenceDTO1).isNotEqualTo(userDashboardPreferenceDTO2);
        userDashboardPreferenceDTO1.setId(null);
        assertThat(userDashboardPreferenceDTO1).isNotEqualTo(userDashboardPreferenceDTO2);
    }
}
