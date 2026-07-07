package com.fintrack.app.domain;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class ApiAccessTokenPermissionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static ApiAccessTokenPermission getApiAccessTokenPermissionSample1() {
        return new ApiAccessTokenPermission().id(1L);
    }

    public static ApiAccessTokenPermission getApiAccessTokenPermissionSample2() {
        return new ApiAccessTokenPermission().id(2L);
    }

    public static ApiAccessTokenPermission getApiAccessTokenPermissionRandomSampleGenerator() {
        return new ApiAccessTokenPermission().id(longCount.incrementAndGet());
    }
}
