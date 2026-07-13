package com.fintrack.app.service.mapper;

import static com.fintrack.app.domain.TransactionIngestionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionIngestionMapperTest {

    private TransactionIngestionMapper transactionIngestionMapper;

    @BeforeEach
    void setUp() {
        transactionIngestionMapper = new TransactionIngestionMapperImpl();
    }

    @Test
    void shouldMapServerManagedFieldsToDtoButNotBackToEntity() {
        var expected = getTransactionIngestionSample1();
        var dto = transactionIngestionMapper.toDto(expected);

        assertThat(dto.getRecordsReceived()).isEqualTo(expected.getRecordsReceived());
        assertThat(dto.getRecordsCreated()).isEqualTo(expected.getRecordsCreated());
        assertThat(dto.getRecordsSkipped()).isEqualTo(expected.getRecordsSkipped());
        assertThat(dto.getRecordsRejected()).isEqualTo(expected.getRecordsRejected());
        assertThat(dto.getErrorMessage()).isEqualTo(expected.getErrorMessage());

        var actual = transactionIngestionMapper.toEntity(dto);

        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getSourceLabel()).isEqualTo(expected.getSourceLabel());
        assertThat(actual.getStatus()).isNull();
        assertThat(actual.getStartedAt()).isNull();
        assertThat(actual.getCompletedAt()).isNull();
        assertThat(actual.getRecordsReceived()).isNull();
        assertThat(actual.getRecordsCreated()).isNull();
        assertThat(actual.getRecordsSkipped()).isNull();
        assertThat(actual.getRecordsRejected()).isNull();
        assertThat(actual.getErrorMessage()).isNull();
        assertThat(actual.getCreatedAt()).isNull();
    }
}
