package com.fintrack.app.service.budget;

import java.util.List;

/** Deliberately bounded server-side notification page. */
public record BudgetNotificationPage(List<BudgetNotificationView> notifications, int page, int size, long totalElements, int totalPages) {}
