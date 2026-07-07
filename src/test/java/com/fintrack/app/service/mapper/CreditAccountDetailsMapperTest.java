package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.CreditAccountDetailsAsserts.*;
import static com.fintrack.app.domain.CreditAccountDetailsTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreditAccountDetailsMapperTest {

    private CreditAccountDetailsMapper creditAccountDetailsMapper;

    @BeforeEach
    void setUp() {
        creditAccountDetailsMapper = new CreditAccountDetailsMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getCreditAccountDetailsSample1();
        var actual = creditAccountDetailsMapper.toEntity(creditAccountDetailsMapper.toDto(expected));
        assertCreditAccountDetailsAllPropertiesEquals(expected, actual);
    }
}
