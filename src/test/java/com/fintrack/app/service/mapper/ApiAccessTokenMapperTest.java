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
        actual.setTokenHash(expected.getTokenHash());
        assertApiAccessTokenAllPropertiesEquals(expected, actual);
    }

    @Test
    void shouldNotExposeTokenHashInDto() {
        var expected = getApiAccessTokenSample1();
        var dto = apiAccessTokenMapper.toDto(expected);
        org.assertj.core.api.Assertions.assertThat(dto.getTokenHash()).isNull();
        org.assertj.core.api.Assertions.assertThat(dto.getName()).isEqualTo(expected.getName());
    }
}
