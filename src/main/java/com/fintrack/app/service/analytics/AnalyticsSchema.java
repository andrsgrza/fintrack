package com.fintrack.app.service.analytics;

/** Version constants for the clean Analytics contracts. */
public final class AnalyticsSchema {

    public static final int VERSION = 1;
    public static final String SEMANTIC_MODEL = "FINTRACK_FINANCIAL";
    public static final String CATALOG_VERSION = "2026-10-27";
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;
    public static final int MAX_MEASURES = 4;
    public static final int MAX_GROUPINGS = 2;
    public static final int MAX_PREDICATES = 10;
    public static final int MAX_REFERENCE_VALUES = 100;
    public static final int MAX_DATE_RANGE_DAYS = 3660;

    private AnalyticsSchema() {}
}
