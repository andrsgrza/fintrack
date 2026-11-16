package com.fintrack.app.service.budget;

/** Metadata is mutable without changing immutable Budget type, currency, revisions, or calendar. */
public record BudgetMetadataUpdateCommand(Long expectedVersion, String name, String description) {}
