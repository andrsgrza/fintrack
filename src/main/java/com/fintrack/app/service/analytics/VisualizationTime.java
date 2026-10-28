package com.fintrack.app.service.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;

/** A context is either backend-resolved relative time or an inclusive explicit date range. */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonDeserialize(using = StrictAnalyticsContractDeserializers.VisualizationTimeDeserializer.class)
public record VisualizationTime(String mode, String relativePeriod, LocalDate from, LocalDate through) {}
