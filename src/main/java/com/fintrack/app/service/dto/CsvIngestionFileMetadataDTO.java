package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.ImportFileType;
import java.io.Serializable;
import java.time.LocalDate;

public class CsvIngestionFileMetadataDTO implements Serializable {

    private String originalFilename;

    private ImportFileType fileType;

    private String contentType;

    private Long fileSizeBytes;

    private String checksum;

    private String parserName;

    private String parserVersion;

    private LocalDate statementStartDate;

    private LocalDate statementEndDate;

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
}
