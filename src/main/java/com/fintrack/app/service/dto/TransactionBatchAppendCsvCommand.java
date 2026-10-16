package com.fintrack.app.service.dto;

import java.util.Arrays;

/** Internal append input. File bytes are transient request material and are never persisted. */
public class TransactionBatchAppendCsvCommand {

    private Long expectedBatchVersion;
    private String operationKey;
    private String fileName;
    private String contentType = "text/csv";
    private String parserVersion = "canonical-csv-v1";
    private byte[] bytes;

    public Long getExpectedBatchVersion() {
        return expectedBatchVersion;
    }

    public void setExpectedBatchVersion(Long expectedBatchVersion) {
        this.expectedBatchVersion = expectedBatchVersion;
    }

    public String getOperationKey() {
        return operationKey;
    }

    public void setOperationKey(String operationKey) {
        this.operationKey = operationKey;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public byte[] getBytes() {
        return bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes == null ? null : Arrays.copyOf(bytes, bytes.length);
    }
}
