package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.TransactionRuleAsserts.*;
import static com.fintrack.app.domain.TransactionRuleTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRuleMapperTest {

    private TransactionRuleMapper transactionRuleMapper;

    @BeforeEach
    void setUp() {
        transactionRuleMapper = new TransactionRuleMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getTransactionRuleSample1();
        var actual = transactionRuleMapper.toEntity(transactionRuleMapper.toDto(expected));
        assertTransactionRuleAllPropertiesEquals(expected, actual);
    }
}
