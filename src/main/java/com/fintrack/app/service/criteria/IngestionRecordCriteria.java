package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.IngestionRecord} entity. This class is used
 * in {@link com.fintrack.app.web.rest.IngestionRecordResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /ingestion-records?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class IngestionRecordCriteria implements Serializable, Criteria {

    /**
     * Class for filtering IngestionRecordStatus
     */
    public static class IngestionRecordStatusFilter extends Filter<IngestionRecordStatus> {

        public IngestionRecordStatusFilter() {}

        public IngestionRecordStatusFilter(IngestionRecordStatusFilter filter) {
            super(filter);
        }

        @Override
        public IngestionRecordStatusFilter copy() {
            return new IngestionRecordStatusFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private IntegerFilter recordIndex;

    private StringFilter externalRecordId;

    private IngestionRecordStatusFilter status;

    private StringFilter errorCode;

    private StringFilter errorMessage;

    private InstantFilter createdAt;

    private LongFilter financialTransactionId;

    private LongFilter transactionIngestionId;

    private Boolean distinct;

    public IngestionRecordCriteria() {}

    public IngestionRecordCriteria(IngestionRecordCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.recordIndex = other.optionalRecordIndex().map(IntegerFilter::copy).orElse(null);
        this.externalRecordId = other.optionalExternalRecordId().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(IngestionRecordStatusFilter::copy).orElse(null);
        this.errorCode = other.optionalErrorCode().map(StringFilter::copy).orElse(null);
        this.errorMessage = other.optionalErrorMessage().map(StringFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.financialTransactionId = other.optionalFinancialTransactionId().map(LongFilter::copy).orElse(null);
        this.transactionIngestionId = other.optionalTransactionIngestionId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public IngestionRecordCriteria copy() {
        return new IngestionRecordCriteria(this);
    }

    public LongFilter getId() {
        return id;
    }

    public Optional<LongFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public LongFilter id() {
        if (id == null) {
            setId(new LongFilter());
        }
        return id;
    }

    public void setId(LongFilter id) {
        this.id = id;
    }

    public IntegerFilter getRecordIndex() {
        return recordIndex;
    }

    public Optional<IntegerFilter> optionalRecordIndex() {
        return Optional.ofNullable(recordIndex);
    }

    public IntegerFilter recordIndex() {
        if (recordIndex == null) {
            setRecordIndex(new IntegerFilter());
        }
        return recordIndex;
    }

    public void setRecordIndex(IntegerFilter recordIndex) {
        this.recordIndex = recordIndex;
    }

    public StringFilter getExternalRecordId() {
        return externalRecordId;
    }

    public Optional<StringFilter> optionalExternalRecordId() {
        return Optional.ofNullable(externalRecordId);
    }

    public StringFilter externalRecordId() {
        if (externalRecordId == null) {
            setExternalRecordId(new StringFilter());
        }
        return externalRecordId;
    }

    public void setExternalRecordId(StringFilter externalRecordId) {
        this.externalRecordId = externalRecordId;
    }

    public IngestionRecordStatusFilter getStatus() {
        return status;
    }

    public Optional<IngestionRecordStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public IngestionRecordStatusFilter status() {
        if (status == null) {
            setStatus(new IngestionRecordStatusFilter());
        }
        return status;
    }

    public void setStatus(IngestionRecordStatusFilter status) {
        this.status = status;
    }

    public StringFilter getErrorCode() {
        return errorCode;
    }

    public Optional<StringFilter> optionalErrorCode() {
        return Optional.ofNullable(errorCode);
    }

    public StringFilter errorCode() {
        if (errorCode == null) {
            setErrorCode(new StringFilter());
        }
        return errorCode;
    }

    public void setErrorCode(StringFilter errorCode) {
        this.errorCode = errorCode;
    }

    public StringFilter getErrorMessage() {
        return errorMessage;
    }

    public Optional<StringFilter> optionalErrorMessage() {
        return Optional.ofNullable(errorMessage);
    }

    public StringFilter errorMessage() {
        if (errorMessage == null) {
            setErrorMessage(new StringFilter());
        }
        return errorMessage;
    }

    public void setErrorMessage(StringFilter errorMessage) {
        this.errorMessage = errorMessage;
    }

    public InstantFilter getCreatedAt() {
        return createdAt;
    }

    public Optional<InstantFilter> optionalCreatedAt() {
        return Optional.ofNullable(createdAt);
    }

    public InstantFilter createdAt() {
        if (createdAt == null) {
            setCreatedAt(new InstantFilter());
        }
        return createdAt;
    }

    public void setCreatedAt(InstantFilter createdAt) {
        this.createdAt = createdAt;
    }

    public LongFilter getFinancialTransactionId() {
        return financialTransactionId;
    }

    public Optional<LongFilter> optionalFinancialTransactionId() {
        return Optional.ofNullable(financialTransactionId);
    }

    public LongFilter financialTransactionId() {
        if (financialTransactionId == null) {
            setFinancialTransactionId(new LongFilter());
        }
        return financialTransactionId;
    }

    public void setFinancialTransactionId(LongFilter financialTransactionId) {
        this.financialTransactionId = financialTransactionId;
    }

    public LongFilter getTransactionIngestionId() {
        return transactionIngestionId;
    }

    public Optional<LongFilter> optionalTransactionIngestionId() {
        return Optional.ofNullable(transactionIngestionId);
    }

    public LongFilter transactionIngestionId() {
        if (transactionIngestionId == null) {
            setTransactionIngestionId(new LongFilter());
        }
        return transactionIngestionId;
    }

    public void setTransactionIngestionId(LongFilter transactionIngestionId) {
        this.transactionIngestionId = transactionIngestionId;
    }

    public Boolean getDistinct() {
        return distinct;
    }

    public Optional<Boolean> optionalDistinct() {
        return Optional.ofNullable(distinct);
    }

    public Boolean distinct() {
        if (distinct == null) {
            setDistinct(true);
        }
        return distinct;
    }

    public void setDistinct(Boolean distinct) {
        this.distinct = distinct;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IngestionRecordCriteria that = (IngestionRecordCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(recordIndex, that.recordIndex) &&
            Objects.equals(externalRecordId, that.externalRecordId) &&
            Objects.equals(status, that.status) &&
            Objects.equals(errorCode, that.errorCode) &&
            Objects.equals(errorMessage, that.errorMessage) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(financialTransactionId, that.financialTransactionId) &&
            Objects.equals(transactionIngestionId, that.transactionIngestionId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            recordIndex,
            externalRecordId,
            status,
            errorCode,
            errorMessage,
            createdAt,
            financialTransactionId,
            transactionIngestionId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "IngestionRecordCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalRecordIndex().map(f -> "recordIndex=" + f + ", ").orElse("") +
            optionalExternalRecordId().map(f -> "externalRecordId=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalErrorCode().map(f -> "errorCode=" + f + ", ").orElse("") +
            optionalErrorMessage().map(f -> "errorMessage=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalFinancialTransactionId().map(f -> "financialTransactionId=" + f + ", ").orElse("") +
            optionalTransactionIngestionId().map(f -> "transactionIngestionId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
