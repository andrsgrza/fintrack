package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class InternalTransferTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static InternalTransfer getInternalTransferSample1() {
        return new InternalTransfer().id(1L).notes("notes1");
    }

    public static InternalTransfer getInternalTransferSample2() {
        return new InternalTransfer().id(2L).notes("notes2");
    }

    public static InternalTransfer getInternalTransferRandomSampleGenerator() {
        return new InternalTransfer().id(longCount.incrementAndGet()).notes(UUID.randomUUID().toString());
    }
}
