package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionRuleConditionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static TransactionRuleCondition getTransactionRuleConditionSample1() {
        return new TransactionRuleCondition().id(1L).value("value1").secondValue("secondValue1").position(1);
    }

    public static TransactionRuleCondition getTransactionRuleConditionSample2() {
        return new TransactionRuleCondition().id(2L).value("value2").secondValue("secondValue2").position(2);
    }

    public static TransactionRuleCondition getTransactionRuleConditionRandomSampleGenerator() {
        return new TransactionRuleCondition()
            .id(longCount.incrementAndGet())
            .value(UUID.randomUUID().toString())
            .secondValue(UUID.randomUUID().toString())
            .position(intCount.incrementAndGet());
    }
}
