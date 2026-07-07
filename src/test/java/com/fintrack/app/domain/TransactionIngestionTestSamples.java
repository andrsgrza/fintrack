package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TransactionIngestionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static TransactionIngestion getTransactionIngestionSample1() {
        return new TransactionIngestion()
            .id(1L)
            .sourceLabel("sourceLabel1")
            .recordsReceived(1)
            .recordsCreated(1)
            .recordsSkipped(1)
            .recordsRejected(1)
            .errorMessage("errorMessage1");
    }

    public static TransactionIngestion getTransactionIngestionSample2() {
        return new TransactionIngestion()
            .id(2L)
            .sourceLabel("sourceLabel2")
            .recordsReceived(2)
            .recordsCreated(2)
            .recordsSkipped(2)
            .recordsRejected(2)
            .errorMessage("errorMessage2");
    }

    public static TransactionIngestion getTransactionIngestionRandomSampleGenerator() {
        return new TransactionIngestion()
            .id(longCount.incrementAndGet())
            .sourceLabel(UUID.randomUUID().toString())
            .recordsReceived(intCount.incrementAndGet())
            .recordsCreated(intCount.incrementAndGet())
            .recordsSkipped(intCount.incrementAndGet())
            .recordsRejected(intCount.incrementAndGet())
            .errorMessage(UUID.randomUUID().toString());
    }
}
