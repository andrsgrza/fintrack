package com.fintrack.app.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CreditAccountDetailsTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static CreditAccountDetails getCreditAccountDetailsSample1() {
        return new CreditAccountDetails().id(1L).statementDay(1).paymentDueDay(1);
    }

    public static CreditAccountDetails getCreditAccountDetailsSample2() {
        return new CreditAccountDetails().id(2L).statementDay(2).paymentDueDay(2);
    }

    public static CreditAccountDetails getCreditAccountDetailsRandomSampleGenerator() {
        return new CreditAccountDetails()
            .id(longCount.incrementAndGet())
            .statementDay(intCount.incrementAndGet())
            .paymentDueDay(intCount.incrementAndGet());
    }
}
