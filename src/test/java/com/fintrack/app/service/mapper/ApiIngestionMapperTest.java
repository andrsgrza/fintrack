package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.ApiIngestionAsserts.*;
import static com.fintrack.app.domain.ApiIngestionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiIngestionMapperTest {

    private ApiIngestionMapper apiIngestionMapper;

    @BeforeEach
    void setUp() {
        apiIngestionMapper = new ApiIngestionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getApiIngestionSample1();
        var actual = apiIngestionMapper.toEntity(apiIngestionMapper.toDto(expected));
        assertApiIngestionAllPropertiesEquals(expected, actual);
    }
}
