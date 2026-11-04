package com.fintrack.app.service.dashboard;

import com.fintrack.app.domain.enumeration.PersonalDashboardWidgetWidth;
import java.util.List;

/** Replaces the complete bounded layout atomically; partial layout mutations cannot leave duplicate positions. */
public record PersonalDashboardLayoutCommand(Long expectedVersion, List<WidgetLayoutItem> widgets) {
    public PersonalDashboardLayoutCommand {
        widgets = widgets == null ? List.of() : List.copyOf(widgets);
    }

    public record WidgetLayoutItem(Long widgetId, PersonalDashboardWidgetWidth width, Boolean visible) {}
}
