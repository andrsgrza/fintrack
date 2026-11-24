package com.fintrack.app.service.budget;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An explicit profile choice; browser detection is intentionally never sent here automatically. */
public record BudgetProfileTimeZoneCommand(@NotBlank @Size(max = 64) String timeZone) {}
