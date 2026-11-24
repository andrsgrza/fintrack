package com.fintrack.app.service.budget;

import com.fintrack.app.domain.enumeration.BudgetType;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Read-only request for displaying the current, owner-scoped expansion of a proposed Budget category scope. */
public record BudgetScopePreviewCommand(@NotNull BudgetType type, @NotNull List<@NotNull Long> categoryIds) {}
