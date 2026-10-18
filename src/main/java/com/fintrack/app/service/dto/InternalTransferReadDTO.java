package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Read model for the future transfer list, detail page and command responses. */
public class InternalTransferReadDTO implements Serializable {

    private Long transferId;
    private Long version;
    private Instant createdAt;
    private String notes;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private CurrencyCode currency;
    private InternalTransferLegDTO source;
    private InternalTransferLegDTO destination;

    public Long getTransferId() {
        return transferId;
    }

    public void setTransferId(Long transferId) {
        this.transferId = transferId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
    }

    public InternalTransferLegDTO getSource() {
        return source;
    }

    public void setSource(InternalTransferLegDTO source) {
        this.source = source;
    }

    public InternalTransferLegDTO getDestination() {
        return destination;
    }

    public void setDestination(InternalTransferLegDTO destination) {
        this.destination = destination;
    }
}
