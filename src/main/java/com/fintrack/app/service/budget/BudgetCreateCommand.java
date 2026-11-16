package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetType;
import com.fintrack.app.domain.enumeration.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Deliberate owner-free command for atomically creating a Budget and revision 1. */
public record BudgetCreateCommand(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @NotNull BudgetType type,
    @NotNull CurrencyCode currency,
    @NotBlank @Size(max = 100) String clientRequestId,
    @NotBlank @Size(max = 100) String timeZone,
    @NotNull @Valid BudgetInitialRevisionCommand initialRevision
) {}
