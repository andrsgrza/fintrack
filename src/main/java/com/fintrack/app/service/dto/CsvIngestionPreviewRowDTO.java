package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.CurrencyCode;
import com.fintrack.app.domain.enumeration.IngestionRecordStatus;
import com.fintrack.app.domain.enumeration.TransactionFlow;
import com.fintrack.app.service.csv.CsvIngestionValidationMessage;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CsvIngestionPreviewRowDTO implements Serializable {

    private Long ingestionRecordId;

    private Integer recordIndex;

    private IngestionRecordStatus status;

    private LocalDate transactionDate;

    private LocalDate postingDate;

    private String description;

    private String signedAmount;

    private String amount;

    private TransactionFlow flow;

    private CurrencyCode currency;

    private String externalReference;

    private String notes;

    private String errorCode;

    private String errorMessage;

    private List<CsvIngestionValidationMessage> warnings = new ArrayList<>();

    public Long getIngestionRecordId() {
        return ingestionRecordId;
    }

    public void setIngestionRecordId(Long ingestionRecordId) {
        this.ingestionRecordId = ingestionRecordId;
    }

    public Integer getRecordIndex() {
        return recordIndex;
    }

    public void setRecordIndex(Integer recordIndex) {
        this.recordIndex = recordIndex;
    }

    public IngestionRecordStatus getStatus() {
        return status;
    }

    public void setStatus(IngestionRecordStatus status) {
        this.status = status;
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

    public String getSignedAmount() {
        return signedAmount;
    }

    public void setSignedAmount(String signedAmount) {
        this.signedAmount = signedAmount;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public TransactionFlow getFlow() {
        return flow;
    }

    public void setFlow(TransactionFlow flow) {
        this.flow = flow;
    }

    public CurrencyCode getCurrency() {
        return currency;
    }

    public void setCurrency(CurrencyCode currency) {
        this.currency = currency;
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

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<CsvIngestionValidationMessage> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<CsvIngestionValidationMessage> warnings) {
        this.warnings = warnings;
    }
}
