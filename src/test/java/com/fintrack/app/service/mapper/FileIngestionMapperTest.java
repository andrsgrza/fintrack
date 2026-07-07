package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.FileIngestionAsserts.*;
import static com.fintrack.app.domain.FileIngestionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileIngestionMapperTest {

    private FileIngestionMapper fileIngestionMapper;

    @BeforeEach
    void setUp() {
        fileIngestionMapper = new FileIngestionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getFileIngestionSample1();
        var actual = fileIngestionMapper.toEntity(fileIngestionMapper.toDto(expected));
        assertFileIngestionAllPropertiesEquals(expected, actual);
    }
}
