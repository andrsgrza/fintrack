package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.FinancialSubscriptionAsserts.*;
import static com.fintrack.app.domain.FinancialSubscriptionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinancialSubscriptionMapperTest {

    private FinancialSubscriptionMapper financialSubscriptionMapper;

    @BeforeEach
    void setUp() {
        financialSubscriptionMapper = new FinancialSubscriptionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getFinancialSubscriptionSample1();
        var actual = financialSubscriptionMapper.toEntity(financialSubscriptionMapper.toDto(expected));
        assertFinancialSubscriptionAllPropertiesEquals(expected, actual);
    }
}
