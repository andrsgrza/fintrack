package com.fintrack.app.domain;

import static com.fintrack.app.domain.ApiAccessTokenPermissionTestSamples.*;
import static com.fintrack.app.domain.ApiAccessTokenTestSamples.*;
import static com.fintrack.app.domain.ApiIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fintrack.app.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApiAccessTokenTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ApiAccessToken.class);
        ApiAccessToken apiAccessToken1 = getApiAccessTokenSample1();
        ApiAccessToken apiAccessToken2 = new ApiAccessToken();
        assertThat(apiAccessToken1).isNotEqualTo(apiAccessToken2);

        apiAccessToken2.setId(apiAccessToken1.getId());
        assertThat(apiAccessToken1).isEqualTo(apiAccessToken2);

        apiAccessToken2 = getApiAccessTokenSample2();
        assertThat(apiAccessToken1).isNotEqualTo(apiAccessToken2);
    }

    @Test
    void apiIngestionsTest() {
        ApiAccessToken apiAccessToken = getApiAccessTokenRandomSampleGenerator();
        ApiIngestion apiIngestionBack = getApiIngestionRandomSampleGenerator();

        apiAccessToken.addApiIngestions(apiIngestionBack);
        assertThat(apiAccessToken.getApiIngestions()).containsOnly(apiIngestionBack);
        assertThat(apiIngestionBack.getApiAccessToken()).isEqualTo(apiAccessToken);

        apiAccessToken.removeApiIngestions(apiIngestionBack);
        assertThat(apiAccessToken.getApiIngestions()).doesNotContain(apiIngestionBack);
        assertThat(apiIngestionBack.getApiAccessToken()).isNull();

        apiAccessToken.apiIngestions(new HashSet<>(Set.of(apiIngestionBack)));
        assertThat(apiAccessToken.getApiIngestions()).containsOnly(apiIngestionBack);
        assertThat(apiIngestionBack.getApiAccessToken()).isEqualTo(apiAccessToken);

        apiAccessToken.setApiIngestions(new HashSet<>());
        assertThat(apiAccessToken.getApiIngestions()).doesNotContain(apiIngestionBack);
        assertThat(apiIngestionBack.getApiAccessToken()).isNull();
    }

    @Test
    void permissionsTest() {
        ApiAccessToken apiAccessToken = getApiAccessTokenRandomSampleGenerator();
        ApiAccessTokenPermission apiAccessTokenPermissionBack = getApiAccessTokenPermissionRandomSampleGenerator();

        apiAccessToken.addPermissions(apiAccessTokenPermissionBack);
        assertThat(apiAccessToken.getPermissions()).containsOnly(apiAccessTokenPermissionBack);
        assertThat(apiAccessTokenPermissionBack.getApiAccessToken()).isEqualTo(apiAccessToken);

        apiAccessToken.removePermissions(apiAccessTokenPermissionBack);
        assertThat(apiAccessToken.getPermissions()).doesNotContain(apiAccessTokenPermissionBack);
        assertThat(apiAccessTokenPermissionBack.getApiAccessToken()).isNull();

        apiAccessToken.permissions(new HashSet<>(Set.of(apiAccessTokenPermissionBack)));
        assertThat(apiAccessToken.getPermissions()).containsOnly(apiAccessTokenPermissionBack);
        assertThat(apiAccessTokenPermissionBack.getApiAccessToken()).isEqualTo(apiAccessToken);

        apiAccessToken.setPermissions(new HashSet<>());
        assertThat(apiAccessToken.getPermissions()).doesNotContain(apiAccessTokenPermissionBack);
        assertThat(apiAccessTokenPermissionBack.getApiAccessToken()).isNull();
    }
}
