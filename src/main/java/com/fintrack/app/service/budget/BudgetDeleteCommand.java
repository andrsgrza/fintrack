package com.fintrack.app.service.budget;

/** Explicit optimistic-lock material for a permanent owner-scoped Budget deletion. */
public record BudgetDeleteCommand(Long expectedVersion) {}
