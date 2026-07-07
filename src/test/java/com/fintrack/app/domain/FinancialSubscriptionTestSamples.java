package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FinancialSubscriptionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static FinancialSubscription getFinancialSubscriptionSample1() {
        return new FinancialSubscription()
            .id(1L)
            .name("name1")
            .description("description1")
            .currency("currency1")
            .intervalCount(1)
            .notes("notes1");
    }

    public static FinancialSubscription getFinancialSubscriptionSample2() {
        return new FinancialSubscription()
            .id(2L)
            .name("name2")
            .description("description2")
            .currency("currency2")
            .intervalCount(2)
            .notes("notes2");
    }

    public static FinancialSubscription getFinancialSubscriptionRandomSampleGenerator() {
        return new FinancialSubscription()
            .id(longCount.incrementAndGet())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .currency(UUID.randomUUID().toString())
            .intervalCount(intCount.incrementAndGet())
            .notes(UUID.randomUUID().toString());
    }
}
