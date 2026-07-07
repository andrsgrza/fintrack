package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.FinancialTransactionAsserts.*;
import static com.fintrack.app.domain.FinancialTransactionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinancialTransactionMapperTest {

    private FinancialTransactionMapper financialTransactionMapper;

    @BeforeEach
    void setUp() {
        financialTransactionMapper = new FinancialTransactionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getFinancialTransactionSample1();
        var actual = financialTransactionMapper.toEntity(financialTransactionMapper.toDto(expected));
        assertFinancialTransactionAllPropertiesEquals(expected, actual);
    }
}
