package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.FinancialAccountAsserts.*;
import static com.fintrack.app.domain.FinancialAccountTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinancialAccountMapperTest {

    private FinancialAccountMapper financialAccountMapper;

    @BeforeEach
    void setUp() {
        financialAccountMapper = new FinancialAccountMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getFinancialAccountSample1();
        var actual = financialAccountMapper.toEntity(financialAccountMapper.toDto(expected));
        assertFinancialAccountAllPropertiesEquals(expected, actual);
    }
}
