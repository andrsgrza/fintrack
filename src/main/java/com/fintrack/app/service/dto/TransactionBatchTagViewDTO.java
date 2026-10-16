package com.fintrack.app.service.dto;

import com.fintrack.app.domain.enumeration.TransactionCandidateClassificationSource;

/** Internal tag projection retaining current identity and association provenance. */
public class TransactionBatchTagViewDTO {

    private Long id;
    private String name;
    private String color;
    private Boolean active;
    private TransactionCandidateClassificationSource source;
    private AppliedRuleAttributionDTO appliedByRule;

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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public TransactionCandidateClassificationSource getSource() {
        return source;
    }

    public void setSource(TransactionCandidateClassificationSource source) {
        this.source = source;
    }

    public AppliedRuleAttributionDTO getAppliedByRule() {
        return appliedByRule;
    }

    public void setAppliedByRule(AppliedRuleAttributionDTO appliedByRule) {
        this.appliedByRule = appliedByRule;
    }
}
