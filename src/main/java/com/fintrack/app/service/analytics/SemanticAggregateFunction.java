package com.fintrack.app.service.analytics;

public enum SemanticAggregateFunction {
    COUNT,
    AVG,
    SUM,
    /**
     * The governed economic-flow aggregate: incoming unlinked movements less outgoing unlinked movements.
     * This deliberately lives in the semantic executor rather than in a client-side subtraction.
     */
    NET,
}
