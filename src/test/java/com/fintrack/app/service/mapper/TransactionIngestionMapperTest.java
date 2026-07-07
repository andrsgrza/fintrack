package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.TransactionIngestionAsserts.*;
import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionIngestionMapperTest {

    private TransactionIngestionMapper transactionIngestionMapper;

    @BeforeEach
    void setUp() {
        transactionIngestionMapper = new TransactionIngestionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getTransactionIngestionSample1();
        var actual = transactionIngestionMapper.toEntity(transactionIngestionMapper.toDto(expected));
        assertTransactionIngestionAllPropertiesEquals(expected, actual);
    }
}
