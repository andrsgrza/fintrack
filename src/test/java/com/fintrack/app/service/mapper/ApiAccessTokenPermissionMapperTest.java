package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.ApiAccessTokenPermissionAsserts.*;
import static com.fintrack.app.domain.ApiAccessTokenPermissionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiAccessTokenPermissionMapperTest {

    private ApiAccessTokenPermissionMapper apiAccessTokenPermissionMapper;

    @BeforeEach
    void setUp() {
        apiAccessTokenPermissionMapper = new ApiAccessTokenPermissionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getApiAccessTokenPermissionSample1();
        var actual = apiAccessTokenPermissionMapper.toEntity(apiAccessTokenPermissionMapper.toDto(expected));
        assertApiAccessTokenPermissionAllPropertiesEquals(expected, actual);
    }
}
