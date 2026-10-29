package com.fintrack.app.service.analytics;

/** Typed capabilities of a field within one governed semantic dataset. */
public record SemanticFieldDefinition(
    SemanticField field,
    SemanticValueType valueType,
    SemanticUnit unit,
    boolean aggregateable,
    boolean filterable,
    boolean groupable,
    SemanticLocalizedText label,
    SemanticLocalizedText description
) {
    public SemanticFieldDefinition(
        SemanticField field,
        SemanticValueType valueType,
        SemanticUnit unit,
        boolean aggregateable,
        boolean filterable,
        boolean groupable
    ) {
        this(
            field,
            valueType,
            unit,
            aggregateable,
            filterable,
            groupable,
            new SemanticLocalizedText(field.name(), field.name()),
            new SemanticLocalizedText(field.name(), field.name())
        );
    }
}
