package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.ApiAccessTokenAsserts.*;
import static com.fintrack.app.domain.ApiAccessTokenTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiAccessTokenMapperTest {

    private ApiAccessTokenMapper apiAccessTokenMapper;

    @BeforeEach
    void setUp() {
        apiAccessTokenMapper = new ApiAccessTokenMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getApiAccessTokenSample1();
        var actual = apiAccessTokenMapper.toEntity(apiAccessTokenMapper.toDto(expected));
        assertApiAccessTokenAllPropertiesEquals(expected, actual);
    }
}
