package com.fintrack.app.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class FileIngestionTestSamples {

    private static final Random random = new Random();
    private static final AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    public static FileIngestion getFileIngestionSample1() {
        return new FileIngestion()
            .id(1L)
            .originalFilename("originalFilename1")
            .contentType("contentType1")
            .fileSizeBytes(1L)
            .checksum("checksum1")
            .storageKey("storageKey1")
            .parserName("parserName1")
            .parserVersion("parserVersion1");
    }

    public static FileIngestion getFileIngestionSample2() {
        return new FileIngestion()
            .id(2L)
            .originalFilename("originalFilename2")
            .contentType("contentType2")
            .fileSizeBytes(2L)
            .checksum("checksum2")
            .storageKey("storageKey2")
            .parserName("parserName2")
            .parserVersion("parserVersion2");
    }

    public static FileIngestion getFileIngestionRandomSampleGenerator() {
        return new FileIngestion()
            .id(longCount.incrementAndGet())
            .originalFilename(UUID.randomUUID().toString())
            .contentType(UUID.randomUUID().toString())
            .fileSizeBytes(longCount.incrementAndGet())
            .checksum(UUID.randomUUID().toString())
            .storageKey(UUID.randomUUID().toString())
            .parserName(UUID.randomUUID().toString())
            .parserVersion(UUID.randomUUID().toString());
    }
}
