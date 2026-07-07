package com.fintrack.app.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class UserDashboardPreferenceTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static UserDashboardPreference getUserDashboardPreferenceSample1() {
        return new UserDashboardPreference().id(1L);
    }

    public static UserDashboardPreference getUserDashboardPreferenceSample2() {
        return new UserDashboardPreference().id(2L);
    }

    public static UserDashboardPreference getUserDashboardPreferenceRandomSampleGenerator() {
        return new UserDashboardPreference().id(longCount.incrementAndGet());
    }
}
