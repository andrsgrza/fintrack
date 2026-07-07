package com.fintrack.app.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ApiAccessTokenDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ApiAccessTokenDTO.class);
        ApiAccessTokenDTO apiAccessTokenDTO1 = new ApiAccessTokenDTO();
        apiAccessTokenDTO1.setId(1L);
        ApiAccessTokenDTO apiAccessTokenDTO2 = new ApiAccessTokenDTO();
        assertThat(apiAccessTokenDTO1).isNotEqualTo(apiAccessTokenDTO2);
        apiAccessTokenDTO2.setId(apiAccessTokenDTO1.getId());
        assertThat(apiAccessTokenDTO1).isEqualTo(apiAccessTokenDTO2);
        apiAccessTokenDTO2.setId(2L);
        assertThat(apiAccessTokenDTO1).isNotEqualTo(apiAccessTokenDTO2);
        apiAccessTokenDTO1.setId(null);
        assertThat(apiAccessTokenDTO1).isNotEqualTo(apiAccessTokenDTO2);
    }
}
