package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** Sorting can only target an explicitly requested semantic grouping. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.SortDeserializer.class)
public record AnalyticsSort(String groupingId, String direction) {}
