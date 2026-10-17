package com.fintrack.app.service.dto;

import java.io.Serializable;

/**
 * Candidate-lifetime explanation of one already-applied rule output.
 *
 * The nullable id refers to the live rule when it still exists. The name is a snapshot captured at apply time and
 * deliberately remains available after a rule is renamed or deleted.
 */
public class AppliedRuleAttributionDTO implements Serializable {

    private Long id;
    private String name;

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
}
