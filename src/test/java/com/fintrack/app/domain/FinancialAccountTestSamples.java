package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class FinancialAccountTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static FinancialAccount getFinancialAccountSample1() {
        return new FinancialAccount()
            .id(1L)
            .name("name1")
            .institutionName("institutionName1")
            .lastFourDigits("lastFourDigits1")
            .description("description1")
            .color("color1")
            .icon("icon1");
    }

    public static FinancialAccount getFinancialAccountSample2() {
        return new FinancialAccount()
            .id(2L)
            .name("name2")
            .institutionName("institutionName2")
            .lastFourDigits("lastFourDigits2")
            .description("description2")
            .color("color2")
            .icon("icon2");
    }

    public static FinancialAccount getFinancialAccountRandomSampleGenerator() {
        return new FinancialAccount()
            .id(longCount.incrementAndGet())
            .name(UUID.randomUUID().toString())
            .institutionName(UUID.randomUUID().toString())
            .lastFourDigits(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .color(UUID.randomUUID().toString())
            .icon(UUID.randomUUID().toString());
    }
}
