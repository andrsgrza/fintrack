import { PersonalDashboardWidget } from './personal-dashboard-api';
import {
  applyLayoutPreset,
  changeLayoutVisibility,
  changeLayoutWidth,
  layoutFromWidgets,
  moveLayoutItem,
} from './personal-dashboard-layout';

const widgets = [
  {
    id: 11,
    position: 2,
    width: 'FULL',
    visible: false,
    visualizationType: 'KPI',
  },
  {
    id: 12,
    position: 0,
    width: 'MEDIUM',
    visible: true,
    visualizationType: 'LINE',
  },
  {
    id: 13,
    position: 1,
    width: 'COMPACT',
    visible: true,
    visualizationType: 'TABLE',
  },
] as PersonalDashboardWidget[];

describe('personal dashboard layout', () => {
  it('keeps the complete local layout draft ordered, resizable, and hideable without changing widget definitions', () => {
    const draft = layoutFromWidgets(widgets);
    const moved = moveLayoutItem(draft, 11, 0);
    const resized = changeLayoutWidth(moved, 12, 'FULL');
    const hidden = changeLayoutVisibility(resized, 13, false);

    expect(hidden).toEqual([
      { widgetId: 11, width: 'FULL', visible: false },
      { widgetId: 12, width: 'FULL', visible: true },
      { widgetId: 13, width: 'COMPACT', visible: false },
    ]);
    expect(widgets.map(widget => widget.position)).toEqual([2, 0, 1]);
    expect(widgets.map(widget => widget.visualizationType)).toEqual(['KPI', 'LINE', 'TABLE']);
  });

  it('applies presets only to local presentation sizes, preserving ordering and visibility', () => {
    const original = layoutFromWidgets(widgets);
    const compact = applyLayoutPreset('COMPACT', original, widgets);
    const focused = applyLayoutPreset('CHART_FOCUSED', original, widgets);

    expect(compact.map(item => item.widgetId)).toEqual(original.map(item => item.widgetId));
    expect(compact.map(item => item.visible)).toEqual(original.map(item => item.visible));
    expect(compact).toEqual([
      { widgetId: 12, width: 'MEDIUM', visible: true },
      { widgetId: 13, width: 'MEDIUM', visible: true },
      { widgetId: 11, width: 'COMPACT', visible: false },
    ]);
    expect(focused).toEqual([
      { widgetId: 12, width: 'FULL', visible: true },
      { widgetId: 13, width: 'MEDIUM', visible: true },
      { widgetId: 11, width: 'COMPACT', visible: false },
    ]);
  });
});
