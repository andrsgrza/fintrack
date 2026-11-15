package com.fintrack.app.service.analytics;

/** Defines whether a semantic value can be combined over the selected temporal grain. */
public enum SemanticAdditivityPolicy {
    ADDITIVE,
    NON_ADDITIVE,
    /** Deliberately expanded many-to-many groups, such as transaction tags. */
    NON_ADDITIVE_OVERLAPPING,
    POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH,
    POINT_IN_TIME_REQUIRES_ACCOUNT,
}
