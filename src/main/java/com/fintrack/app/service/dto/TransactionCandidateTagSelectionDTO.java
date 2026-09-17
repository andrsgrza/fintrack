package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;
import java.io.Serializable;

/** Read-only candidate tag and its server-owned provenance. */
public class TransactionCandidateTagSelectionDTO implements Serializable {

    private Long tagId;
    private String tagName;
    private TransactionCandidateClassificationSource source;

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public TransactionCandidateClassificationSource getSource() {
        return source;
    }

    public void setSource(TransactionCandidateClassificationSource source) {
        this.source = source;
    }
}
