package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.domain.enumeration.TransactionOrigin;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link com.fintrack.app.domain.FinancialTransaction} entity.
 */
@Schema(
    description = "A monetary movement in an account.\n\nThe amount is always positive. The flow determines whether money enters\nor leaves the account. There is no transaction-level currency field."
)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialTransactionDTO implements Serializable {

    private Long id;

    @NotNull
    private LocalDate transactionDate;

    private LocalDate postingDate;

    @NotNull
    @Size(min = 1, max = 500)
    private String description;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal amount;

    @NotNull
    private TransactionFlow flow;

    @NotNull
    private TransactionOrigin origin;

    @Size(max = 150)
    private String externalReference;

    @Size(max = 1000)
    private String notes;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    @NotNull
    private FinancialAccountDTO account;

    private CategoryDTO category;

    private FinancialSubscriptionDTO financialSubscription;

    private TransactionIngestionDTO transactionIngestion;

    private Set<TagDTO> tags = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getPostingDate() {
        return postingDate;
    }

    public void setPostingDate(LocalDate postingDate) {
        this.postingDate = postingDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionFlow getFlow() {
        return flow;
    }

    public void setFlow(TransactionFlow flow) {
        this.flow = flow;
    }

    public TransactionOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(TransactionOrigin origin) {
        this.origin = origin;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public FinancialAccountDTO getAccount() {
        return account;
    }

    public void setAccount(FinancialAccountDTO account) {
        this.account = account;
    }

    public CategoryDTO getCategory() {
        return category;
    }

    public void setCategory(CategoryDTO category) {
        this.category = category;
    }

    public FinancialSubscriptionDTO getFinancialSubscription() {
        return financialSubscription;
    }

    public void setFinancialSubscription(FinancialSubscriptionDTO financialSubscription) {
        this.financialSubscription = financialSubscription;
    }

    public TransactionIngestionDTO getTransactionIngestion() {
        return transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestionDTO transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public Set<TagDTO> getTags() {
        return tags;
    }

    public void setTags(Set<TagDTO> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FinancialTransactionDTO)) {
            return false;
        }

        FinancialTransactionDTO financialTransactionDTO = (FinancialTransactionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, financialTransactionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialTransactionDTO{" +
            "id=" + getId() +
            ", transactionDate='" + getTransactionDate() + "'" +
            ", postingDate='" + getPostingDate() + "'" +
            ", description='" + getDescription() + "'" +
            ", amount=" + getAmount() +
            ", flow='" + getFlow() + "'" +
            ", origin='" + getOrigin() + "'" +
            ", externalReference='" + getExternalReference() + "'" +
            ", notes='" + getNotes() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", account=" + getAccount() +
            ", category=" + getCategory() +
            ", financialSubscription=" + getFinancialSubscription() +
            ", transactionIngestion=" + getTransactionIngestion() +
            ", tags=" + getTags() +
            "}";
    }
}
