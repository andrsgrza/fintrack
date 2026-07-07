package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionRuleTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static TransactionRule getTransactionRuleSample1() {
        return new TransactionRule()
            .id(1L)
            .name("name1")
            .description("description1")
            .priority(1)
            .resultingDescription("resultingDescription1");
    }

    public static TransactionRule getTransactionRuleSample2() {
        return new TransactionRule()
            .id(2L)
            .name("name2")
            .description("description2")
            .priority(2)
            .resultingDescription("resultingDescription2");
    }

    public static TransactionRule getTransactionRuleRandomSampleGenerator() {
        return new TransactionRule()
            .id(longCount.incrementAndGet())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .priority(intCount.incrementAndGet())
            .resultingDescription(UUID.randomUUID().toString());
    }
}
