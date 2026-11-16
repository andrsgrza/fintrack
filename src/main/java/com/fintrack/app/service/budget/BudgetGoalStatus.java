package com.fintrack.app.service.budget;

/** Financial interpretation of an authoritative actual against the effective period target. */
public enum BudgetGoalStatus {
    WITHIN_LIMIT,
    FINISHED_WITHIN_LIMIT,
    LIMIT_REACHED,
    EXCEEDED,
    GOAL_PENDING,
    GOAL_NOT_MET,
    GOAL_REACHED,
    GOAL_EXCEEDED,
    NOT_EVALUATED,
    UNKNOWN,
}
