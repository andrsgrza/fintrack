import { SemanticAnalyticsCatalog } from 'app/entities/analytics-explorer/analytics-explorer.model';
import { visualizationEligibility } from 'app/entities/analytics-explorer/analytics-visualization-compatibility';

import { recommendedDashboardTemplates } from './recommended-dashboard-templates';

const catalog: SemanticAnalyticsCatalog = {
  schemaVersion: 3,
  semanticModel: 'FINTRACK_FINANCIAL',
  catalogVersion: '1.3',
  supportedRelativePeriods: ['THIS_MONTH'],
  defaultLimit: 100,
  maxLimit: 500,
  authenticatedOwnerRequired: true,
  datasets: [],
};

describe('recommended dashboard templates', () => {
  it('defines exactly the three official templates with live semantic contract details', () => {
    const templates = recommendedDashboardTemplates(catalog);

    expect(templates.map(template => template.id)).toEqual(['financial-overview', 'movement-analysis', 'accounts-and-position']);
    expect(templates.every(template => template.version === 2)).toBe(true);
    const widgets = templates.flatMap(template => template.widgets);
    expect([...new Set(widgets.map(widget => widget.id))]).toHaveLength(widgets.length);
    expect(widgets.every(widget => widget.definition.schemaVersion === catalog.schemaVersion)).toBe(true);
    expect(widgets.every(widget => widget.definition.semanticModel === catalog.semanticModel)).toBe(true);
    expect(widgets.every(widget => widget.definition.limit === catalog.maxLimit)).toBe(true);
  });

  it('uses the governed net-flow aggregate and does not turn templates into precomputed results', () => {
    const widgets = recommendedDashboardTemplates(catalog).flatMap(template => template.widgets);
    const netWidgets = widgets.filter(widget => widget.definition.projections.some(projection => projection.function === 'NET'));

    expect(netWidgets.map(widget => widget.id)).toEqual(expect.arrayContaining(['net-economic-flow', 'economic-trend', 'net-flow-trend']));
    expect(netWidgets.every(widget => widget.definition.dataset === 'TRANSACTION_LEDGER')).toBe(true);
    expect(widgets.some(widget => 'result' in widget.definition)).toBe(false);
  });

  it('marks current credit facts rather than pretending there is limit history', () => {
    const widgets = recommendedDashboardTemplates(catalog).flatMap(template => template.widgets);
    const currentFacts = widgets.filter(widget => widget.contextPolicy === 'CURRENT_ONLY').map(widget => widget.id);

    expect(currentFacts).toEqual(
      expect.arrayContaining(['current-credit-limit', 'current-available-credit', 'current-credit-utilization']),
    );
    expect(widgets.filter(widget => widget.contextPolicy === 'CURRENT_ONLY').every(widget => widget.visualization === 'BAR')).toBe(true);
  });

  it('keeps every official widget compatible with the shared presentation contract', () => {
    const widgets = recommendedDashboardTemplates(catalog).flatMap(template => template.widgets);

    for (const widget of widgets) {
      expect(visualizationEligibility(widget.definition, null, 'en').find(option => option.option === widget.visualization)?.enabled).toBe(
        true,
      );
    }
  });

  it('uses a governed per-currency financial-position KPI and a separate as-of account view', () => {
    const templates = recommendedDashboardTemplates(catalog);
    const overview = templates.find(template => template.id === 'financial-overview');
    const accounts = templates.find(template => template.id === 'accounts-and-position');
    const positionKpi = overview?.widgets.find(widget => widget.id === 'registered-position');
    const positionByAccount = accounts?.widgets.find(widget => widget.id === 'position-by-account');

    expect(positionKpi?.definition).toMatchObject({
      dataset: 'FINANCIAL_POSITION_SNAPSHOT',
      groupBy: [],
      projections: [{ alias: 'posicion_financiera', function: 'SUM', field: 'FINANCIAL_POSITION' }],
    });
    expect(positionKpi?.visualization).toBe('KPI');
    expect(positionByAccount?.definition.groupBy).toEqual(['ACCOUNT_ID']);
    expect(positionByAccount?.definition.groupBy).not.toContain('OBSERVATION_MONTH');
  });
});
