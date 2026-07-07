package com.fintrack.app.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fintrack.app.domain.enumeration.ImportFileType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * File-specific metadata for a FILE ingestion.
 */
@Entity
@Table(name = "file_ingestion")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FileIngestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(min = 1, max = 255)
    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false)
    private ImportFileType fileType;

    @Size(max = 100)
    @Column(name = "content_type", length = 100)
    private String contentType;

    @Min(value = 0L)
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Size(max = 128)
    @Column(name = "checksum", length = 128)
    private String checksum;

    @Size(max = 500)
    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Size(max = 100)
    @Column(name = "parser_name", length = 100)
    private String parserName;

    @Size(max = 50)
    @Column(name = "parser_version", length = 50)
    private String parserVersion;

    @Column(name = "statement_start_date")
    private LocalDate statementStartDate;

    @Column(name = "statement_end_date")
    private LocalDate statementEndDate;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JsonIgnoreProperties(value = { "accounts", "fileIngestion", "apiIngestion", "financialTransactions", "records" }, allowSetters = true)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @NotNull
    @JoinColumn(unique = true)
    private TransactionIngestion transactionIngestion;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public FileIngestion id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return this.originalFilename;
    }

    public FileIngestion originalFilename(String originalFilename) {
        this.setOriginalFilename(originalFilename);
        return this;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public ImportFileType getFileType() {
        return this.fileType;
    }

    public FileIngestion fileType(ImportFileType fileType) {
        this.setFileType(fileType);
        return this;
    }

    public void setFileType(ImportFileType fileType) {
        this.fileType = fileType;
    }

    public String getContentType() {
        return this.contentType;
    }

    public FileIngestion contentType(String contentType) {
        this.setContentType(contentType);
        return this;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSizeBytes() {
        return this.fileSizeBytes;
    }

    public FileIngestion fileSizeBytes(Long fileSizeBytes) {
        this.setFileSizeBytes(fileSizeBytes);
        return this;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getChecksum() {
        return this.checksum;
    }

    public FileIngestion checksum(String checksum) {
        this.setChecksum(checksum);
        return this;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getStorageKey() {
        return this.storageKey;
    }

    public FileIngestion storageKey(String storageKey) {
        this.setStorageKey(storageKey);
        return this;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getParserName() {
        return this.parserName;
    }

    public FileIngestion parserName(String parserName) {
        this.setParserName(parserName);
        return this;
    }

    public void setParserName(String parserName) {
        this.parserName = parserName;
    }

    public String getParserVersion() {
        return this.parserVersion;
    }

    public FileIngestion parserVersion(String parserVersion) {
        this.setParserVersion(parserVersion);
        return this;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public LocalDate getStatementStartDate() {
        return this.statementStartDate;
    }

    public FileIngestion statementStartDate(LocalDate statementStartDate) {
        this.setStatementStartDate(statementStartDate);
        return this;
    }

    public void setStatementStartDate(LocalDate statementStartDate) {
        this.statementStartDate = statementStartDate;
    }

    public LocalDate getStatementEndDate() {
        return this.statementEndDate;
    }

    public FileIngestion statementEndDate(LocalDate statementEndDate) {
        this.setStatementEndDate(statementEndDate);
        return this;
    }

    public void setStatementEndDate(LocalDate statementEndDate) {
        this.statementEndDate = statementEndDate;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public FileIngestion createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public TransactionIngestion getTransactionIngestion() {
        return this.transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestion transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    public FileIngestion transactionIngestion(TransactionIngestion transactionIngestion) {
        this.setTransactionIngestion(transactionIngestion);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileIngestion)) {
            return false;
        }
        return getId() != null && getId().equals(((FileIngestion) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FileIngestion{" +
            "id=" + getId() +
            ", originalFilename='" + getOriginalFilename() + "'" +
            ", fileType='" + getFileType() + "'" +
            ", contentType='" + getContentType() + "'" +
            ", fileSizeBytes=" + getFileSizeBytes() +
            ", checksum='" + getChecksum() + "'" +
            ", storageKey='" + getStorageKey() + "'" +
            ", parserName='" + getParserName() + "'" +
            ", parserVersion='" + getParserVersion() + "'" +
            ", statementStartDate='" + getStatementStartDate() + "'" +
            ", statementEndDate='" + getStatementEndDate() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
