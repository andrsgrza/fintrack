package com.fintrack.app.service.criteria;

import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.*;

/**
 * Criteria class for the {@link com.fintrack.app.domain.FinancialTransaction} entity. This class is used
 * in {@link com.fintrack.app.web.rest.FinancialTransactionResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /financial-transactions?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialTransactionCriteria implements Serializable, Criteria {

    /**
     * Class for filtering TransactionFlow
     */
    public static class TransactionFlowFilter extends Filter<TransactionFlow> {

        public TransactionFlowFilter() {}

        public TransactionFlowFilter(TransactionFlowFilter filter) {
            super(filter);
        }

        @Override
        public TransactionFlowFilter copy() {
            return new TransactionFlowFilter(this);
        }
    }

    /**
     * Class for filtering TransactionOrigin
     */
    public static class TransactionOriginFilter extends Filter<TransactionOrigin> {

        public TransactionOriginFilter() {}

        public TransactionOriginFilter(TransactionOriginFilter filter) {
            super(filter);
        }

        @Override
        public TransactionOriginFilter copy() {
            return new TransactionOriginFilter(this);
        }
    }

    private static final long serialVersionUID = 1L;

    private LongFilter id;

    private LocalDateFilter transactionDate;

    private LocalDateFilter postingDate;

    private StringFilter description;

    private BigDecimalFilter amount;

    private TransactionFlowFilter flow;

    private TransactionOriginFilter origin;

    private StringFilter externalReference;

    private StringFilter notes;

    private InstantFilter createdAt;

    private InstantFilter updatedAt;

    private LongFilter accountId;

    private LongFilter categoryId;

    private LongFilter financialSubscriptionId;

    private LongFilter transactionIngestionId;

    private LongFilter tagsId;

    private LongFilter outgoingInternalTransferId;

    private LongFilter incomingInternalTransferId;

    private LongFilter ingestionRecordId;

    private Boolean distinct;

    public FinancialTransactionCriteria() {}

    public FinancialTransactionCriteria(FinancialTransactionCriteria other) {
        this.id = other.optionalId().map(LongFilter::copy).orElse(null);
        this.transactionDate = other.optionalTransactionDate().map(LocalDateFilter::copy).orElse(null);
        this.postingDate = other.optionalPostingDate().map(LocalDateFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.amount = other.optionalAmount().map(BigDecimalFilter::copy).orElse(null);
        this.flow = other.optionalFlow().map(TransactionFlowFilter::copy).orElse(null);
        this.origin = other.optionalOrigin().map(TransactionOriginFilter::copy).orElse(null);
        this.externalReference = other.optionalExternalReference().map(StringFilter::copy).orElse(null);
        this.notes = other.optionalNotes().map(StringFilter::copy).orElse(null);
        this.createdAt = other.optionalCreatedAt().map(InstantFilter::copy).orElse(null);
        this.updatedAt = other.optionalUpdatedAt().map(InstantFilter::copy).orElse(null);
        this.accountId = other.optionalAccountId().map(LongFilter::copy).orElse(null);
        this.categoryId = other.optionalCategoryId().map(LongFilter::copy).orElse(null);
        this.financialSubscriptionId = other.optionalFinancialSubscriptionId().map(LongFilter::copy).orElse(null);
        this.transactionIngestionId = other.optionalTransactionIngestionId().map(LongFilter::copy).orElse(null);
        this.tagsId = other.optionalTagsId().map(LongFilter::copy).orElse(null);
        this.outgoingInternalTransferId = other.optionalOutgoingInternalTransferId().map(LongFilter::copy).orElse(null);
        this.incomingInternalTransferId = other.optionalIncomingInternalTransferId().map(LongFilter::copy).orElse(null);
        this.ingestionRecordId = other.optionalIngestionRecordId().map(LongFilter::copy).orElse(null);
        this.distinct = other.distinct;
    }

    @Override
    public FinancialTransactionCriteria copy() {
        return new FinancialTransactionCriteria(this);
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

    public LocalDateFilter getTransactionDate() {
        return transactionDate;
    }

    public Optional<LocalDateFilter> optionalTransactionDate() {
        return Optional.ofNullable(transactionDate);
    }

    public LocalDateFilter transactionDate() {
        if (transactionDate == null) {
            setTransactionDate(new LocalDateFilter());
        }
        return transactionDate;
    }

    public void setTransactionDate(LocalDateFilter transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDateFilter getPostingDate() {
        return postingDate;
    }

    public Optional<LocalDateFilter> optionalPostingDate() {
        return Optional.ofNullable(postingDate);
    }

    public LocalDateFilter postingDate() {
        if (postingDate == null) {
            setPostingDate(new LocalDateFilter());
        }
        return postingDate;
    }

    public void setPostingDate(LocalDateFilter postingDate) {
        this.postingDate = postingDate;
    }

    public StringFilter getDescription() {
        return description;
    }

    public Optional<StringFilter> optionalDescription() {
        return Optional.ofNullable(description);
    }

    public StringFilter description() {
        if (description == null) {
            setDescription(new StringFilter());
        }
        return description;
    }

    public void setDescription(StringFilter description) {
        this.description = description;
    }

    public BigDecimalFilter getAmount() {
        return amount;
    }

    public Optional<BigDecimalFilter> optionalAmount() {
        return Optional.ofNullable(amount);
    }

    public BigDecimalFilter amount() {
        if (amount == null) {
            setAmount(new BigDecimalFilter());
        }
        return amount;
    }

    public void setAmount(BigDecimalFilter amount) {
        this.amount = amount;
    }

    public TransactionFlowFilter getFlow() {
        return flow;
    }

    public Optional<TransactionFlowFilter> optionalFlow() {
        return Optional.ofNullable(flow);
    }

    public TransactionFlowFilter flow() {
        if (flow == null) {
            setFlow(new TransactionFlowFilter());
        }
        return flow;
    }

    public void setFlow(TransactionFlowFilter flow) {
        this.flow = flow;
    }

    public TransactionOriginFilter getOrigin() {
        return origin;
    }

    public Optional<TransactionOriginFilter> optionalOrigin() {
        return Optional.ofNullable(origin);
    }

    public TransactionOriginFilter origin() {
        if (origin == null) {
            setOrigin(new TransactionOriginFilter());
        }
        return origin;
    }

    public void setOrigin(TransactionOriginFilter origin) {
        this.origin = origin;
    }

    public StringFilter getExternalReference() {
        return externalReference;
    }

    public Optional<StringFilter> optionalExternalReference() {
        return Optional.ofNullable(externalReference);
    }

    public StringFilter externalReference() {
        if (externalReference == null) {
            setExternalReference(new StringFilter());
        }
        return externalReference;
    }

    public void setExternalReference(StringFilter externalReference) {
        this.externalReference = externalReference;
    }

    public StringFilter getNotes() {
        return notes;
    }

    public Optional<StringFilter> optionalNotes() {
        return Optional.ofNullable(notes);
    }

    public StringFilter notes() {
        if (notes == null) {
            setNotes(new StringFilter());
        }
        return notes;
    }

    public void setNotes(StringFilter notes) {
        this.notes = notes;
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

    public InstantFilter getUpdatedAt() {
        return updatedAt;
    }

    public Optional<InstantFilter> optionalUpdatedAt() {
        return Optional.ofNullable(updatedAt);
    }

    public InstantFilter updatedAt() {
        if (updatedAt == null) {
            setUpdatedAt(new InstantFilter());
        }
        return updatedAt;
    }

    public void setUpdatedAt(InstantFilter updatedAt) {
        this.updatedAt = updatedAt;
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

    public LongFilter getCategoryId() {
        return categoryId;
    }

    public Optional<LongFilter> optionalCategoryId() {
        return Optional.ofNullable(categoryId);
    }

    public LongFilter categoryId() {
        if (categoryId == null) {
            setCategoryId(new LongFilter());
        }
        return categoryId;
    }

    public void setCategoryId(LongFilter categoryId) {
        this.categoryId = categoryId;
    }

    public LongFilter getFinancialSubscriptionId() {
        return financialSubscriptionId;
    }

    public Optional<LongFilter> optionalFinancialSubscriptionId() {
        return Optional.ofNullable(financialSubscriptionId);
    }

    public LongFilter financialSubscriptionId() {
        if (financialSubscriptionId == null) {
            setFinancialSubscriptionId(new LongFilter());
        }
        return financialSubscriptionId;
    }

    public void setFinancialSubscriptionId(LongFilter financialSubscriptionId) {
        this.financialSubscriptionId = financialSubscriptionId;
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

    public LongFilter getTagsId() {
        return tagsId;
    }

    public Optional<LongFilter> optionalTagsId() {
        return Optional.ofNullable(tagsId);
    }

    public LongFilter tagsId() {
        if (tagsId == null) {
            setTagsId(new LongFilter());
        }
        return tagsId;
    }

    public void setTagsId(LongFilter tagsId) {
        this.tagsId = tagsId;
    }

    public LongFilter getOutgoingInternalTransferId() {
        return outgoingInternalTransferId;
    }

    public Optional<LongFilter> optionalOutgoingInternalTransferId() {
        return Optional.ofNullable(outgoingInternalTransferId);
    }

    public LongFilter outgoingInternalTransferId() {
        if (outgoingInternalTransferId == null) {
            setOutgoingInternalTransferId(new LongFilter());
        }
        return outgoingInternalTransferId;
    }

    public void setOutgoingInternalTransferId(LongFilter outgoingInternalTransferId) {
        this.outgoingInternalTransferId = outgoingInternalTransferId;
    }

    public LongFilter getIncomingInternalTransferId() {
        return incomingInternalTransferId;
    }

    public Optional<LongFilter> optionalIncomingInternalTransferId() {
        return Optional.ofNullable(incomingInternalTransferId);
    }

    public LongFilter incomingInternalTransferId() {
        if (incomingInternalTransferId == null) {
            setIncomingInternalTransferId(new LongFilter());
        }
        return incomingInternalTransferId;
    }

    public void setIncomingInternalTransferId(LongFilter incomingInternalTransferId) {
        this.incomingInternalTransferId = incomingInternalTransferId;
    }

    public LongFilter getIngestionRecordId() {
        return ingestionRecordId;
    }

    public Optional<LongFilter> optionalIngestionRecordId() {
        return Optional.ofNullable(ingestionRecordId);
    }

    public LongFilter ingestionRecordId() {
        if (ingestionRecordId == null) {
            setIngestionRecordId(new LongFilter());
        }
        return ingestionRecordId;
    }

    public void setIngestionRecordId(LongFilter ingestionRecordId) {
        this.ingestionRecordId = ingestionRecordId;
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
        final FinancialTransactionCriteria that = (FinancialTransactionCriteria) o;
        return (
            Objects.equals(id, that.id) &&
            Objects.equals(transactionDate, that.transactionDate) &&
            Objects.equals(postingDate, that.postingDate) &&
            Objects.equals(description, that.description) &&
            Objects.equals(amount, that.amount) &&
            Objects.equals(flow, that.flow) &&
            Objects.equals(origin, that.origin) &&
            Objects.equals(externalReference, that.externalReference) &&
            Objects.equals(notes, that.notes) &&
            Objects.equals(createdAt, that.createdAt) &&
            Objects.equals(updatedAt, that.updatedAt) &&
            Objects.equals(accountId, that.accountId) &&
            Objects.equals(categoryId, that.categoryId) &&
            Objects.equals(financialSubscriptionId, that.financialSubscriptionId) &&
            Objects.equals(transactionIngestionId, that.transactionIngestionId) &&
            Objects.equals(tagsId, that.tagsId) &&
            Objects.equals(outgoingInternalTransferId, that.outgoingInternalTransferId) &&
            Objects.equals(incomingInternalTransferId, that.incomingInternalTransferId) &&
            Objects.equals(ingestionRecordId, that.ingestionRecordId) &&
            Objects.equals(distinct, that.distinct)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id,
            transactionDate,
            postingDate,
            description,
            amount,
            flow,
            origin,
            externalReference,
            notes,
            createdAt,
            updatedAt,
            accountId,
            categoryId,
            financialSubscriptionId,
            transactionIngestionId,
            tagsId,
            outgoingInternalTransferId,
            incomingInternalTransferId,
            ingestionRecordId,
            distinct
        );
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialTransactionCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalTransactionDate().map(f -> "transactionDate=" + f + ", ").orElse("") +
            optionalPostingDate().map(f -> "postingDate=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalAmount().map(f -> "amount=" + f + ", ").orElse("") +
            optionalFlow().map(f -> "flow=" + f + ", ").orElse("") +
            optionalOrigin().map(f -> "origin=" + f + ", ").orElse("") +
            optionalExternalReference().map(f -> "externalReference=" + f + ", ").orElse("") +
            optionalNotes().map(f -> "notes=" + f + ", ").orElse("") +
            optionalCreatedAt().map(f -> "createdAt=" + f + ", ").orElse("") +
            optionalUpdatedAt().map(f -> "updatedAt=" + f + ", ").orElse("") +
            optionalAccountId().map(f -> "accountId=" + f + ", ").orElse("") +
            optionalCategoryId().map(f -> "categoryId=" + f + ", ").orElse("") +
            optionalFinancialSubscriptionId().map(f -> "financialSubscriptionId=" + f + ", ").orElse("") +
            optionalTransactionIngestionId().map(f -> "transactionIngestionId=" + f + ", ").orElse("") +
            optionalTagsId().map(f -> "tagsId=" + f + ", ").orElse("") +
            optionalOutgoingInternalTransferId().map(f -> "outgoingInternalTransferId=" + f + ", ").orElse("") +
            optionalIncomingInternalTransferId().map(f -> "incomingInternalTransferId=" + f + ", ").orElse("") +
            optionalIngestionRecordId().map(f -> "ingestionRecordId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
        "}";
    }
}
