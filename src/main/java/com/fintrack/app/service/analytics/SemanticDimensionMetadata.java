package com.fintrack.app.service.analytics;

import java.util.List;

/** Owner-safe display data for a semantic dimension. IDs remain stable identities; labels are presentation metadata. */
public record SemanticDimensionMetadata(String stableId, String label, String color, Boolean active, boolean synthetic, List<String> path) {
    public SemanticDimensionMetadata {
        path = path == null ? List.of() : List.copyOf(path);
    }
}
