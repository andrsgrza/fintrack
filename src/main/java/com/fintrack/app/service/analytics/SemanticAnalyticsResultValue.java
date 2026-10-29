package com.fintrack.app.service.analytics;

import java.math.BigDecimal;

public record SemanticAnalyticsResultValue(String alias, BigDecimal value, SemanticUnit unit, SemanticResultAvailability availability) {}
