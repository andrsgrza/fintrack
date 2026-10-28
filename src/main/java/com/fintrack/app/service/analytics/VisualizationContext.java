package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.util.List;

/** Supplies when and where a definition runs; an empty account/currency selection means all accessible values. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.VisualizationContextDeserializer.class)
public record VisualizationContext(Integer schemaVersion, VisualizationTime time, List<Long> accountIds, List<String> currencyCodes) {
    public VisualizationContext {
        accountIds = accountIds == null ? List.of() : List.copyOf(accountIds);
        currencyCodes = currencyCodes == null ? List.of() : List.copyOf(currencyCodes);
    }
}
