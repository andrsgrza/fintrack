package com.fintrack.app.domain;

import static com.fintrack.app.domain.ApiAccessTokenPermissionTestSamples.*;
import static com.fintrack.app.domain.ApiAccessTokenTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ApiAccessTokenPermissionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ApiAccessTokenPermission.class);
        ApiAccessTokenPermission apiAccessTokenPermission1 = getApiAccessTokenPermissionSample1();
        ApiAccessTokenPermission apiAccessTokenPermission2 = new ApiAccessTokenPermission();
        assertThat(apiAccessTokenPermission1).isNotEqualTo(apiAccessTokenPermission2);

        apiAccessTokenPermission2.setId(apiAccessTokenPermission1.getId());
        assertThat(apiAccessTokenPermission1).isEqualTo(apiAccessTokenPermission2);

        apiAccessTokenPermission2 = getApiAccessTokenPermissionSample2();
        assertThat(apiAccessTokenPermission1).isNotEqualTo(apiAccessTokenPermission2);
    }

    @Test
    void apiAccessTokenTest() {
        ApiAccessTokenPermission apiAccessTokenPermission = getApiAccessTokenPermissionRandomSampleGenerator();
        ApiAccessToken apiAccessTokenBack = getApiAccessTokenRandomSampleGenerator();

        apiAccessTokenPermission.setApiAccessToken(apiAccessTokenBack);
        assertThat(apiAccessTokenPermission.getApiAccessToken()).isEqualTo(apiAccessTokenBack);

        apiAccessTokenPermission.apiAccessToken(null);
        assertThat(apiAccessTokenPermission.getApiAccessToken()).isNull();
    }
}
