package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.BudgetAsserts.*;
import static com.fintrack.app.domain.BudgetTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BudgetMapperTest {

    private BudgetMapper budgetMapper;

    @BeforeEach
    void setUp() {
        budgetMapper = new BudgetMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getBudgetSample1();
        var actual = budgetMapper.toEntity(budgetMapper.toDto(expected));
        assertBudgetAllPropertiesEquals(expected, actual);
    }
}
