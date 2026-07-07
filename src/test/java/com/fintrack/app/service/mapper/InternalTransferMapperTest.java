package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.InternalTransferAsserts.*;
import static com.fintrack.app.domain.InternalTransferTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InternalTransferMapperTest {

    private InternalTransferMapper internalTransferMapper;

    @BeforeEach
    void setUp() {
        internalTransferMapper = new InternalTransferMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getInternalTransferSample1();
        var actual = internalTransferMapper.toEntity(internalTransferMapper.toDto(expected));
        assertInternalTransferAllPropertiesEquals(expected, actual);
    }
}
