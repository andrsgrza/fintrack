package com.fintrack.app.service.analytics;

/** Distinguishes a calculated zero from history that FINTRACK cannot authoritatively reconstruct. */
public enum SemanticResultAvailability {
    COMPLETE,
    ZERO,
    NO_DATA,
    UNAVAILABLE,
    NOT_APPLICABLE,
    PARTIAL,
}
