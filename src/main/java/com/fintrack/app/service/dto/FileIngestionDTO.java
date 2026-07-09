package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.ImportFileType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A DTO for the {@link com.fintrack.app.domain.FileIngestion} entity.
 */
@Schema(description = "File-specific metadata for a FILE ingestion.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FileIngestionDTO implements Serializable {

    private Long id;

    @NotNull
    @Size(min = 1, max = 255)
    private String originalFilename;

    @NotNull
    private ImportFileType fileType;

    @Size(max = 100)
    private String contentType;

    @Min(value = 0L)
    private Long fileSizeBytes;

    @Size(max = 128)
    private String checksum;

    @Size(max = 500)
    private String storageKey;

    @Size(max = 100)
    private String parserName;

    @Size(max = 50)
    private String parserVersion;

    private LocalDate statementStartDate;

    private LocalDate statementEndDate;

    private Instant createdAt;

    @NotNull
    private TransactionIngestionDTO transactionIngestion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public ImportFileType getFileType() {
        return fileType;
    }

    public void setFileType(ImportFileType fileType) {
        this.fileType = fileType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getParserName() {
        return parserName;
    }

    public void setParserName(String parserName) {
        this.parserName = parserName;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public LocalDate getStatementStartDate() {
        return statementStartDate;
    }

    public void setStatementStartDate(LocalDate statementStartDate) {
        this.statementStartDate = statementStartDate;
    }

    public LocalDate getStatementEndDate() {
        return statementEndDate;
    }

    public void setStatementEndDate(LocalDate statementEndDate) {
        this.statementEndDate = statementEndDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public TransactionIngestionDTO getTransactionIngestion() {
        return transactionIngestion;
    }

    public void setTransactionIngestion(TransactionIngestionDTO transactionIngestion) {
        this.transactionIngestion = transactionIngestion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FileIngestionDTO)) {
            return false;
        }

        FileIngestionDTO fileIngestionDTO = (FileIngestionDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, fileIngestionDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FileIngestionDTO{" +
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
            ", transactionIngestion=" + getTransactionIngestion() +
            "}";
    }
}
