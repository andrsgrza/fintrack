package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ApiAccessTokenPermissionDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ApiAccessTokenPermissionDTO.class);
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO1 = new ApiAccessTokenPermissionDTO();
        apiAccessTokenPermissionDTO1.setId(1L);
        ApiAccessTokenPermissionDTO apiAccessTokenPermissionDTO2 = new ApiAccessTokenPermissionDTO();
        assertThat(apiAccessTokenPermissionDTO1).isNotEqualTo(apiAccessTokenPermissionDTO2);
        apiAccessTokenPermissionDTO2.setId(apiAccessTokenPermissionDTO1.getId());
        assertThat(apiAccessTokenPermissionDTO1).isEqualTo(apiAccessTokenPermissionDTO2);
        apiAccessTokenPermissionDTO2.setId(2L);
        assertThat(apiAccessTokenPermissionDTO1).isNotEqualTo(apiAccessTokenPermissionDTO2);
        apiAccessTokenPermissionDTO1.setId(null);
        assertThat(apiAccessTokenPermissionDTO1).isNotEqualTo(apiAccessTokenPermissionDTO2);
    }
}
