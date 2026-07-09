package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.IngestionStatus;
import com.fintrack.app.domain.enumeration.IngestionType;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.TransactionIngestion} entity. This class is used
 * in {@link com.fintrack.app.web.rest.TransactionIngestionResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /transaction-ingestions?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TransactionIngestionCriteria implements Serializable, Criteria {

    /**
     * Class for filtering IngestionType
     */
    public static class IngestionTypeFilter extends Filter<IngestionType> {

        public IngestionTypeFilter() {}

        public IngestionTypeFilter(IngestionTypeFilter filter) {
            super(filter);
        }

        @Override
        public IngestionTypeFilter copy() {
            return new IngestionTypeFilter(this);
        }
    }

    /**
     * Class for filtering IngestionStatus
     */
    public static class IngestionStatusFilter extends Filter<IngestionStatus> {

        public IngestionStatusFilter() {}

        public IngestionStatusFilter(IngestionStatusFilter filter) {
            super(filter);
        }

        @Override
        public IngestionStatusFilter copy() {
            return new IngestionStatusFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private IngestionTypeFilter ingestionType;

    private IngestionStatusFilter status;

    private StringFilter sourceLabel;

    private InstantFilter startedAt;

    private InstantFilter completedAt;

    private IntegerFilter recordsReceived;

    private IntegerFilter recordsCreated;

    private IntegerFilter recordsSkipped;

    private IntegerFilter recordsRejected;

    private StringFilter errorMessage;

    private InstantFilter createdAt;

    private LongFilter accountId;

    private LongFilter fileIngestionId;

    private LongFilter apiIngestionId;

    private LongFilter financialTransactionsId;

    private LongFilter recordsId;

    private Boolean distinct;

    public TransactionIngestionCriteria() {}

    public TransactionIngestionCriteria(TransactionIngestionCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.ingestionType = other.optionalIngestionType().map(IngestionTypeFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(IngestionStatusFilter::copy).orElse(null);
        this.sourceLabel = other.optionalSourceLabel().map(StringFilter::copy).orElse(null);
        this.startedAt = other.optionalStartedAt().map(InstantFilter::copy).orElse(null);
        this.completedAt = other.optionalCompletedAt().map(InstantFilter::copy).orElse(null);
        this.recordsReceived = other.optionalRecordsReceived().map(IntegerFilter::copy).orElse(null);
        this.recordsCreated = other.optionalRecordsCreated().map(IntegerFilter::copy).orElse(null);
        this.recordsSkipped = other.optionalRecordsSkipped().map(IntegerFilter::copy).orElse(null);
        this.recordsRejected = other.optionalRecordsRejected().map(IntegerFilter::copy).orElse(null);
        this.errorMessage = other.optionalErrorMessage().map(StringFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.accountId = other.optionalAccountId().map(LongFilter::copy).orElse(null);
        this.fileIngestionId = other.optionalFileIngestionId().map(LongFilter::copy).orElse(null);
        this.apiIngestionId = other.optionalApiIngestionId().map(LongFilter::copy).orElse(null);
        this.financialTransactionsId = other.optionalFinancialTransactionsId().map(LongFilter::copy).orElse(null);
        this.recordsId = other.optionalRecordsId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public TransactionIngestionCriteria copy() {
        return new TransactionIngestionCriteria(this);
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

    public IngestionTypeFilter getIngestionType() {
        return ingestionType;
    }

    public Optional<IngestionTypeFilter> optionalIngestionType() {
        return Optional.ofNullable(ingestionType);
    }

    public IngestionTypeFilter ingestionType() {
        if (ingestionType == null) {
            setIngestionType(new IngestionTypeFilter());
        }
        return ingestionType;
    }

    public void setIngestionType(IngestionTypeFilter ingestionType) {
        this.ingestionType = ingestionType;
    }

    public IngestionStatusFilter getStatus() {
        return status;
    }

    public Optional<IngestionStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public IngestionStatusFilter status() {
        if (status == null) {
            setStatus(new IngestionStatusFilter());
        }
        return status;
    }

    public void setStatus(IngestionStatusFilter status) {
        this.status = status;
    }

    public StringFilter getSourceLabel() {
        return sourceLabel;
    }

    public Optional<StringFilter> optionalSourceLabel() {
        return Optional.ofNullable(sourceLabel);
    }

    public StringFilter sourceLabel() {
        if (sourceLabel == null) {
            setSourceLabel(new StringFilter());
        }
        return sourceLabel;
    }

    public void setSourceLabel(StringFilter sourceLabel) {
        this.sourceLabel = sourceLabel;
    }

    public InstantFilter getStartedAt() {
        return startedAt;
    }

    public Optional<InstantFilter> optionalStartedAt() {
        return Optional.ofNullable(startedAt);
    }

    public InstantFilter startedAt() {
        if (startedAt == null) {
            setStartedAt(new InstantFilter());
        }
        return startedAt;
    }

    public void setStartedAt(InstantFilter startedAt) {
        this.startedAt = startedAt;
    }

    public InstantFilter getCompletedAt() {
        return completedAt;
    }

    public Optional<InstantFilter> optionalCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    public InstantFilter completedAt() {
        if (completedAt == null) {
            setCompletedAt(new InstantFilter());
        }
        return completedAt;
    }

    public void setCompletedAt(InstantFilter completedAt) {
        this.completedAt = completedAt;
    }

    public IntegerFilter getRecordsReceived() {
        return recordsReceived;
    }

    public Optional<IntegerFilter> optionalRecordsReceived() {
        return Optional.ofNullable(recordsReceived);
    }

    public IntegerFilter recordsReceived() {
        if (recordsReceived == null) {
            setRecordsReceived(new IntegerFilter());
        }
        return recordsReceived;
    }

    public void setRecordsReceived(IntegerFilter recordsReceived) {
        this.recordsReceived = recordsReceived;
    }

    public IntegerFilter getRecordsCreated() {
        return recordsCreated;
    }

    public Optional<IntegerFilter> optionalRecordsCreated() {
        return Optional.ofNullable(recordsCreated);
    }

    public IntegerFilter recordsCreated() {
        if (recordsCreated == null) {
            setRecordsCreated(new IntegerFilter());
        }
        return recordsCreated;
    }

    public void setRecordsCreated(IntegerFilter recordsCreated) {
        this.recordsCreated = recordsCreated;
    }

    public IntegerFilter getRecordsSkipped() {
        return recordsSkipped;
    }

    public Optional<IntegerFilter> optionalRecordsSkipped() {
        return Optional.ofNullable(recordsSkipped);
    }

    public IntegerFilter recordsSkipped() {
        if (recordsSkipped == null) {
            setRecordsSkipped(new IntegerFilter());
        }
        return recordsSkipped;
    }

    public void setRecordsSkipped(IntegerFilter recordsSkipped) {
        this.recordsSkipped = recordsSkipped;
    }

    public IntegerFilter getRecordsRejected() {
        return recordsRejected;
    }

    public Optional<IntegerFilter> optionalRecordsRejected() {
        return Optional.ofNullable(recordsRejected);
    }

    public IntegerFilter recordsRejected() {
        if (recordsRejected == null) {
            setRecordsRejected(new IntegerFilter());
        }
        return recordsRejected;
    }

    public void setRecordsRejected(IntegerFilter recordsRejected) {
        this.recordsRejected = recordsRejected;
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

    public LongFilter getAccountId() {
        return accountId;
    }

    public Optional<LongFilter> optionalAccountId() {
        return Optional.ofNullable(accountId);
    }

    public LongFilter accountId() {
        if (accountId == null) {
            setAccountId(new LongFilter());
        }
        return accountId;
    }

    public void setAccountId(LongFilter accountId) {
        this.accountId = accountId;
    }

    public LongFilter getFileIngestionId() {
        return fileIngestionId;
    }

    public Optional<LongFilter> optionalFileIngestionId() {
        return Optional.ofNullable(fileIngestionId);
    }

    public LongFilter fileIngestionId() {
        if (fileIngestionId == null) {
            setFileIngestionId(new LongFilter());
        }
        return fileIngestionId;
    }

    public void setFileIngestionId(LongFilter fileIngestionId) {
        this.fileIngestionId = fileIngestionId;
    }

    public LongFilter getApiIngestionId() {
        return apiIngestionId;
    }

    public Optional<LongFilter> optionalApiIngestionId() {
        return Optional.ofNullable(apiIngestionId);
    }

    public LongFilter apiIngestionId() {
        if (apiIngestionId == null) {
            setApiIngestionId(new LongFilter());
        }
        return apiIngestionId;
    }

    public void setApiIngestionId(LongFilter apiIngestionId) {
        this.apiIngestionId = apiIngestionId;
    }

    public LongFilter getFinancialTransactionsId() {
        return financialTransactionsId;
    }

    public Optional<LongFilter> optionalFinancialTransactionsId() {
        return Optional.ofNullable(financialTransactionsId);
    }

    public LongFilter financialTransactionsId() {
        if (financialTransactionsId == null) {
            setFinancialTransactionsId(new LongFilter());
        }
        return financialTransactionsId;
    }

    public void setFinancialTransactionsId(LongFilter financialTransactionsId) {
        this.financialTransactionsId = financialTransactionsId;
    }

    public LongFilter getRecordsId() {
        return recordsId;
    }

    public Optional<LongFilter> optionalRecordsId() {
        return Optional.ofNullable(recordsId);
    }

    public LongFilter recordsId() {
        if (recordsId == null) {
            setRecordsId(new LongFilter());
        }
        return recordsId;
    }

    public void setRecordsId(LongFilter recordsId) {
        this.recordsId = recordsId;
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
        final TransactionIngestionCriteria that = (TransactionIngestionCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(ingestionType, that.ingestionType) &&
            Objects.equals(status, that.status) &&
            Objects.equals(sourceLabel, that.sourceLabel) &&
            Objects.equals(startedAt, that.startedAt) &&
            Objects.equals(completedAt, that.completedAt) &&
            Objects.equals(recordsReceived, that.recordsReceived) &&
            Objects.equals(recordsCreated, that.recordsCreated) &&
            Objects.equals(recordsSkipped, that.recordsSkipped) &&
            Objects.equals(recordsRejected, that.recordsRejected) &&
            Objects.equals(errorMessage, that.errorMessage) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(accountId, that.accountId) &&
            Objects.equals(fileIngestionId, that.fileIngestionId) &&
            Objects.equals(apiIngestionId, that.apiIngestionId) &&
            Objects.equals(financialTransactionsId, that.financialTransactionsId) &&
            Objects.equals(recordsId, that.recordsId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            ingestionType,
            status,
            sourceLabel,
            startedAt,
            completedAt,
            recordsReceived,
            recordsCreated,
            recordsSkipped,
            recordsRejected,
            errorMessage,
            createdAt,
            accountId,
            fileIngestionId,
            apiIngestionId,
            financialTransactionsId,
            recordsId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TransactionIngestionCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalIngestionType().map(f -> "ingestionType=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalSourceLabel().map(f -> "sourceLabel=" + f + ", ").orElse("") +
            optionalStartedAt().map(f -> "startedAt=" + f + ", ").orElse("") +
            optionalCompletedAt().map(f -> "completedAt=" + f + ", ").orElse("") +
            optionalRecordsReceived().map(f -> "recordsReceived=" + f + ", ").orElse("") +
            optionalRecordsCreated().map(f -> "recordsCreated=" + f + ", ").orElse("") +
            optionalRecordsSkipped().map(f -> "recordsSkipped=" + f + ", ").orElse("") +
            optionalRecordsRejected().map(f -> "recordsRejected=" + f + ", ").orElse("") +
            optionalErrorMessage().map(f -> "errorMessage=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalAccountId().map(f -> "accountId=" + f + ", ").orElse("") +
            optionalFileIngestionId().map(f -> "fileIngestionId=" + f + ", ").orElse("") +
            optionalApiIngestionId().map(f -> "apiIngestionId=" + f + ", ").orElse("") +
            optionalFinancialTransactionsId().map(f -> "financialTransactionsId=" + f + ", ").orElse("") +
            optionalRecordsId().map(f -> "recordsId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
