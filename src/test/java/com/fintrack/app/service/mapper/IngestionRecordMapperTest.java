package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.IngestionRecordAsserts.*;
import static com.fintrack.app.domain.IngestionRecordTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IngestionRecordMapperTest {

    private IngestionRecordMapper ingestionRecordMapper;

    @BeforeEach
    void setUp() {
        ingestionRecordMapper = new IngestionRecordMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getIngestionRecordSample1();
        var actual = ingestionRecordMapper.toEntity(ingestionRecordMapper.toDto(expected));
        assertIngestionRecordAllPropertiesEquals(expected, actual);
    }
}
