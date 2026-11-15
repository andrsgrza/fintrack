import { ExplorerVisualization } from 'app/entities/analytics-explorer/analytics-explorer.model';

import { PersonalDashboardWidget } from './personal-dashboard-api';

export type DashboardWidgetWidth = PersonalDashboardWidget['width'];
export type DashboardLayoutPreset = 'BALANCED' | 'COMPACT' | 'CHART_FOCUSED';
export type DashboardLayoutItem = {
  widgetId: number;
  width: DashboardWidgetWidth;
  visible: boolean;
};

type LayoutWidget = Pick<PersonalDashboardWidget, 'id' | 'position' | 'width' | 'visible' | 'visualizationType'>;

const chartVisualizations = new Set<ExplorerVisualization>(['LINE', 'BAR', 'GROUPED_BAR', 'STACKED_BAR']);

export const isChartVisualization = (visualization: ExplorerVisualization) => chartVisualizations.has(visualization);

export const layoutFromWidgets = (widgets: LayoutWidget[]): DashboardLayoutItem[] =>
  [...widgets]
    .sort((left, right) => left.position - right.position || left.id - right.id)
    .map(widget => ({ widgetId: widget.id, width: widget.width, visible: widget.visible }));

export const sameLayout = (left: DashboardLayoutItem[], right: DashboardLayoutItem[]) =>
  left.length === right.length &&
  left.every(
    (item, index) =>
      item.widgetId === right[index]?.widgetId && item.width === right[index]?.width && item.visible === right[index]?.visible,
  );

export const moveLayoutItem = (layout: DashboardLayoutItem[], widgetId: number, targetIndex: number): DashboardLayoutItem[] => {
  const currentIndex = layout.findIndex(item => item.widgetId === widgetId);
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= layout.length || currentIndex === targetIndex) return layout;
  const next = [...layout];
  const [item] = next.splice(currentIndex, 1);
  next.splice(targetIndex, 0, item);
  return next;
};

export const changeLayoutWidth = (layout: DashboardLayoutItem[], widgetId: number, width: DashboardWidgetWidth): DashboardLayoutItem[] =>
  layout.map(item => (item.widgetId === widgetId ? { ...item, width } : item));

export const changeLayoutVisibility = (layout: DashboardLayoutItem[], widgetId: number, visible: boolean): DashboardLayoutItem[] =>
  layout.map(item => (item.widgetId === widgetId ? { ...item, visible } : item));

const widthForPreset = (preset: DashboardLayoutPreset, widget: LayoutWidget): DashboardWidgetWidth => {
  if (widget.visualizationType === 'KPI') return 'COMPACT';
  if (preset === 'COMPACT') return 'MEDIUM';
  if (preset === 'CHART_FOCUSED') return isChartVisualization(widget.visualizationType) ? 'FULL' : 'MEDIUM';
  return isChartVisualization(widget.visualizationType) || widget.visualizationType === 'TABLE' ? 'FULL' : 'MEDIUM';
};

/** Presets change only local layout attributes; titles, queries, context, and visibility remain untouched. */
export const applyLayoutPreset = (
  preset: DashboardLayoutPreset,
  layout: DashboardLayoutItem[],
  widgets: LayoutWidget[],
): DashboardLayoutItem[] => {
  const widgetById = new Map(widgets.map(widget => [widget.id, widget]));
  return layout.map(item => {
    const widget = widgetById.get(item.widgetId);
    return widget ? { ...item, width: widthForPreset(preset, widget) } : item;
  });
};
