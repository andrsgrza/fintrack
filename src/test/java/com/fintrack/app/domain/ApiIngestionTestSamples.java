package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class ApiIngestionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static ApiIngestion getApiIngestionSample1() {
        return new ApiIngestion()
            .id(1L)
            .requestId("requestId1")
            .idempotencyKey("idempotencyKey1")
            .sourceSystem("sourceSystem1")
            .apiVersion("apiVersion1")
            .endpoint("endpoint1")
            .clientReference("clientReference1");
    }

    public static ApiIngestion getApiIngestionSample2() {
        return new ApiIngestion()
            .id(2L)
            .requestId("requestId2")
            .idempotencyKey("idempotencyKey2")
            .sourceSystem("sourceSystem2")
            .apiVersion("apiVersion2")
            .endpoint("endpoint2")
            .clientReference("clientReference2");
    }

    public static ApiIngestion getApiIngestionRandomSampleGenerator() {
        return new ApiIngestion()
            .id(longCount.incrementAndGet())
            .requestId(UUID.randomUUID().toString())
            .idempotencyKey(UUID.randomUUID().toString())
            .sourceSystem(UUID.randomUUID().toString())
            .apiVersion(UUID.randomUUID().toString())
            .endpoint(UUID.randomUUID().toString())
            .clientReference(UUID.randomUUID().toString());
    }
}
