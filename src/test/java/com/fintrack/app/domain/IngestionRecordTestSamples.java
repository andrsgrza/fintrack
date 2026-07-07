package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IngestionRecordTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static IngestionRecord getIngestionRecordSample1() {
        return new IngestionRecord()
            .id(1L)
            .recordIndex(1)
            .externalRecordId("externalRecordId1")
            .errorCode("errorCode1")
            .errorMessage("errorMessage1");
    }

    public static IngestionRecord getIngestionRecordSample2() {
        return new IngestionRecord()
            .id(2L)
            .recordIndex(2)
            .externalRecordId("externalRecordId2")
            .errorCode("errorCode2")
            .errorMessage("errorMessage2");
    }

    public static IngestionRecord getIngestionRecordRandomSampleGenerator() {
        return new IngestionRecord()
            .id(longCount.incrementAndGet())
            .recordIndex(intCount.incrementAndGet())
            .externalRecordId(UUID.randomUUID().toString())
            .errorCode(UUID.randomUUID().toString())
            .errorMessage(UUID.randomUUID().toString());
    }
}
