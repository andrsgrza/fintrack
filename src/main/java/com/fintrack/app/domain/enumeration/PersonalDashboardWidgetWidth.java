package com.fintrack.app.domain.enumeration;

/** The fixed twelve-column desktop spans accepted for a personal dashboard widget. */
public enum PersonalDashboardWidgetWidth {
    COMPACT,
    MEDIUM,
    FULL;

    public int columns() {
        return switch (this) {
            case COMPACT -> 4;
            case MEDIUM -> 6;
            case FULL -> 12;
        };
    }
}
