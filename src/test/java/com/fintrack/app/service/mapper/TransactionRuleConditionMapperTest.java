package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.TransactionRuleConditionAsserts.*;
import static com.fintrack.app.domain.TransactionRuleConditionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionRuleConditionMapperTest {

    private TransactionRuleConditionMapper transactionRuleConditionMapper;

    @BeforeEach
    void setUp() {
        transactionRuleConditionMapper = new TransactionRuleConditionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getTransactionRuleConditionSample1();
        var actual = transactionRuleConditionMapper.toEntity(transactionRuleConditionMapper.toDto(expected));
        assertTransactionRuleConditionAllPropertiesEquals(expected, actual);
    }
}
