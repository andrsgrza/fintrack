package com.fintrack.app.service.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public class CsvIngestionDescriptionReviewDTO implements Serializable {

    private String source;

    private String originalDescription;

    private String normalizedDescription;

    private Long ruleId;

    private String ruleName;

    private String resultingDescription;

    private Instant editedAt;

    private String editedBy;

    public static CsvIngestionDescriptionReviewDTO fromRawData(JsonNode rawData) {
        if (rawData == null || rawData.isMissingNode() || rawData.isNull()) {
            return null;
        }

        JsonNode descriptionReview = rawData.path("review").path("description");
        String originalDescription = textOrNull(rawData.path("raw"), "description");
        String normalizedDescription = textOrNull(rawData.path("normalized"), "description");

        if (!descriptionReview.isObject() && originalDescription == null && normalizedDescription == null) {
            return null;
        }

        CsvIngestionDescriptionReviewDTO dto = new CsvIngestionDescriptionReviewDTO();
        dto.setOriginalDescription(originalDescription);
        dto.setNormalizedDescription(normalizedDescription);

        if (descriptionReview.isObject()) {
            dto.setSource(textOrNull(descriptionReview, "source"));
            dto.setRuleId(longOrNull(descriptionReview, "ruleId"));
            dto.setRuleName(textOrNull(descriptionReview, "ruleName"));
            dto.setResultingDescription(textOrNull(descriptionReview, "resultingDescription"));
            dto.setEditedAt(instantOrNull(descriptionReview, "editedAt"));
            dto.setEditedBy(textOrNull(descriptionReview, "editedBy"));
        }

        return dto;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOriginalDescription() {
        return originalDescription;
    }

    public void setOriginalDescription(String originalDescription) {
        this.originalDescription = originalDescription;
    }

    public String getNormalizedDescription() {
        return normalizedDescription;
    }

    public void setNormalizedDescription(String normalizedDescription) {
        this.normalizedDescription = normalizedDescription;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getResultingDescription() {
        return resultingDescription;
    }

    public void setResultingDescription(String resultingDescription) {
        this.resultingDescription = resultingDescription;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(Instant editedAt) {
        this.editedAt = editedAt;
    }

    public String getEditedBy() {
        return editedBy;
    }

    public void setEditedBy(String editedBy) {
        this.editedBy = editedBy;
    }

    private static String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static Long longOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asLong();
    }

    private static Instant instantOrNull(JsonNode node, String fieldName) {
        String value = textOrNull(node, fieldName);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
