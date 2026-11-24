package com.fintrack.app.service.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fintrack.app.domain.enumeration.BudgetNotificationType;
import java.time.Instant;

/** Safe, owner-scoped presentation of an immutable notification event and its separate read state. */
public record BudgetNotificationView(
    Long id,
    Long budgetId,
    BudgetNotificationType notificationType,
    JsonNode snapshot,
    Instant eventAt,
    Instant materializedAt,
    Instant readAt
) {}
