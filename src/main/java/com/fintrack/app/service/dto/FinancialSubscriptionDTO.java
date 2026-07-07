package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.RecurrenceUnit;
import com.fintrack.app.domain.enumeration.SubscriptionStatus;
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
 * A DTO for the {@link com.fintrack.app.domain.FinancialSubscription} entity.
 */
@Schema(description = "A recurring expected charge. It does not generate transactions.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FinancialSubscriptionDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull
    private SubscriptionStatus status;

    @DecimalMin(value = "0")
    private BigDecimal expectedAmount;

    @DecimalMin(value = "0")
    @DecimalMax(value = "100")
    private BigDecimal amountTolerancePercentage;

    @NotNull
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    @NotNull
    private RecurrenceUnit recurrenceUnit;

    @NotNull
    @Min(value = 1)
    private Integer intervalCount;

    @NotNull
    private LocalDate startDate;

    private LocalDate nextExpectedDate;

    private LocalDate endDate;

    @NotNull
    private Boolean automaticPayment;

    @Size(max = 500)
    private String notes;

    @NotNull
    private Instant createdAt;

    @NotNull
    private Instant updatedAt;

    @NotNull
    private UserDTO user;

    private FinancialAccountDTO account;

    private CategoryDTO category;

    private Set<TagDTO> tags = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        this.status = status;
    }

    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }

    public void setExpectedAmount(BigDecimal expectedAmount) {
        this.expectedAmount = expectedAmount;
    }

    public BigDecimal getAmountTolerancePercentage() {
        return amountTolerancePercentage;
    }

    public void setAmountTolerancePercentage(BigDecimal amountTolerancePercentage) {
        this.amountTolerancePercentage = amountTolerancePercentage;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public RecurrenceUnit getRecurrenceUnit() {
        return recurrenceUnit;
    }

    public void setRecurrenceUnit(RecurrenceUnit recurrenceUnit) {
        this.recurrenceUnit = recurrenceUnit;
    }

    public Integer getIntervalCount() {
        return intervalCount;
    }

    public void setIntervalCount(Integer intervalCount) {
        this.intervalCount = intervalCount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getNextExpectedDate() {
        return nextExpectedDate;
    }

    public void setNextExpectedDate(LocalDate nextExpectedDate) {
        this.nextExpectedDate = nextExpectedDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Boolean getAutomaticPayment() {
        return automaticPayment;
    }

    public void setAutomaticPayment(Boolean automaticPayment) {
        this.automaticPayment = automaticPayment;
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

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
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
        if (!(o instanceof FinancialSubscriptionDTO)) {
            return false;
        }

        FinancialSubscriptionDTO financialSubscriptionDTO = (FinancialSubscriptionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, financialSubscriptionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FinancialSubscriptionDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", status='" + getStatus() + "'" +
            ", expectedAmount=" + getExpectedAmount() +
            ", amountTolerancePercentage=" + getAmountTolerancePercentage() +
            ", currency='" + getCurrency() + "'" +
            ", recurrenceUnit='" + getRecurrenceUnit() + "'" +
            ", intervalCount=" + getIntervalCount() +
            ", startDate='" + getStartDate() + "'" +
            ", nextExpectedDate='" + getNextExpectedDate() + "'" +
            ", endDate='" + getEndDate() + "'" +
            ", automaticPayment='" + getAutomaticPayment() + "'" +
            ", notes='" + getNotes() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            ", updatedAt='" + getUpdatedAt() + "'" +
            ", user=" + getUser() +
            ", account=" + getAccount() +
            ", category=" + getCategory() +
            ", tags=" + getTags() +
            "}";
    }
}
