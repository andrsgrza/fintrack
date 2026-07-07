package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class BudgetTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static Budget getBudgetSample1() {
        return new Budget().id(1L).name("name1");
    }

    public static Budget getBudgetSample2() {
        return new Budget().id(2L).name("name2");
    }

    public static Budget getBudgetRandomSampleGenerator() {
        return new Budget().id(longCount.incrementAndGet()).name(UUID.randomUUID().toString());
    }
}
