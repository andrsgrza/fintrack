package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ApiAccessTokenTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static ApiAccessToken getApiAccessTokenSample1() {
        return new ApiAccessToken().id(1L).name("name1").tokenPrefix("tokenPrefix1").tokenHash("tokenHash1");
    }

    public static ApiAccessToken getApiAccessTokenSample2() {
        return new ApiAccessToken().id(2L).name("name2").tokenPrefix("tokenPrefix2").tokenHash("tokenHash2");
    }

    public static ApiAccessToken getApiAccessTokenRandomSampleGenerator() {
        return new ApiAccessToken()
            .id(longCount.incrementAndGet())
            .name(UUID.randomUUID().toString())
            .tokenPrefix(UUID.randomUUID().toString())
            .tokenHash(UUID.randomUUID().toString());
    }
}
