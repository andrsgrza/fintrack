import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import RecommendedDashboards from './recommended-dashboards';

const executeSemanticQuery = jest.fn();
const getExplorerAccounts = jest.fn();
const getSemanticAnalyticsCatalog = jest.fn();
const getPersonalDashboardDefault = jest.fn();
const setPersonalDashboardDefault = jest.fn();

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('app/entities/analytics-explorer/analytics-explorer-api', () => ({
  executeSemanticQuery: (...args: unknown[]) => executeSemanticQuery(...args),
  getExplorerAccounts: () => getExplorerAccounts(),
  getSemanticAnalyticsCatalog: () => getSemanticAnalyticsCatalog(),
}));
jest.mock('app/entities/analytics-explorer/analytics-results-panel', () => ({
  AnalyticsResultsPanel: ({
    definition,
    visualization,
    drillTrail,
    onDrillDown,
    onDrillBack,
  }: {
    definition: {
      dataset: string;
      projections: Array<{ alias: string }>;
      groupBy: string[];
      orderBy: Array<{ field: string | null; projectionAlias: string | null }>;
    };
    visualization: string;
    drillTrail: string[];
    onDrillDown: (categoryId: string, categoryLabel: string) => void;
    onDrillBack: () => void;
  }) => (
    <div data-cy={`sharedResult-${definition.projections[0]?.alias ?? 'none'}`}>
      {visualization} · {definition.dataset}
      {definition.groupBy.length === 1 &&
      ['CATEGORY_ROOT', 'CATEGORY_BRANCH_CHILD'].includes(definition.groupBy[0]) &&
      definition.orderBy[0]?.field !== null &&
      definition.orderBy[0]?.projectionAlias === null ? (
        <button type="button" onClick={() => onDrillDown('42', 'Household')}>
          Drill category hierarchy
        </button>
      ) : null}
      {drillTrail.length ? (
        <button type="button" onClick={onDrillBack}>
          Back category hierarchy
        </button>
      ) : null}
    </div>
  ),
}));
jest.mock('app/entities/personal-dashboards/personal-dashboard-api', () => ({
  getPersonalDashboardDefault: () => getPersonalDashboardDefault(),
  setPersonalDashboardDefault: (...args: unknown[]) => setPersonalDashboardDefault(...args),
}));

const catalog = {
  schemaVersion: 3,
  semanticModel: 'FINTRACK_FINANCIAL',
  catalogVersion: '1.3',
  supportedRelativePeriods: ['THIS_MONTH'],
  defaultLimit: 100,
  maxLimit: 500,
  authenticatedOwnerRequired: true,
  datasets: [
    {
      dataset: 'TRANSACTION_LEDGER',
      groupings: ['TRANSACTION_DAY', 'TRANSACTION_WEEK', 'TRANSACTION_MONTH', 'TRANSACTION_YEAR'],
    },
  ],
};
const emptyResult = {
  schemaVersion: 3,
  semanticModel: 'FINTRACK_FINANCIAL',
  effectiveContext: { from: '2026-01-01', through: '2026-01-01', accountIds: [], currencyCodes: [] },
  executedAt: '2026-01-01T00:00:00Z',
  columns: [],
  rows: [],
};

const renderDashboard = (path = '/analytics/dashboards/financial-overview') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/analytics/dashboards/:dashboardId" element={<RecommendedDashboards />} />
        <Route path="/analytics/dashboards" element={<RecommendedDashboards />} />
      </Routes>
    </MemoryRouter>,
  );

describe('recommended dashboards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getSemanticAnalyticsCatalog.mockResolvedValue(catalog);
    getExplorerAccounts.mockResolvedValue([{ id: 7, name: 'Daily account', accountType: 'DEBIT', currency: 'MXN' }]);
    getPersonalDashboardDefault.mockResolvedValue({ dashboardId: null, officialTemplateId: 'financial-overview', version: null });
    setPersonalDashboardDefault.mockResolvedValue({ dashboardId: null, officialTemplateId: 'movement-analysis', version: 0 });
    executeSemanticQuery.mockResolvedValue(emptyResult);
  });

  it('renders exactly three distinct dashboard routes with the shared result renderer and real semantic requests', async () => {
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'Financial overview' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Financial overview' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Movement analysis' })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Accounts and registered position' })).toBeTruthy();
    await waitFor(() => expect(executeSemanticQuery.mock.calls.length).toBeGreaterThanOrEqual(3));
    expect(document.querySelector('[data-cy="sharedResult-ingresos"]')).not.toBeNull();
    expect(document.querySelector('[data-cy="sharedResult-flujo_neto"]')).not.toBeNull();
    expect(
      executeSemanticQuery.mock.calls.some(([definition]) =>
        definition.projections.some((projection: { function: string }) => projection.function === 'NET'),
      ),
    ).toBe(true);

    fireEvent.click(screen.getByRole('link', { name: 'Movement analysis' }));
    expect(await screen.findByRole('heading', { name: 'Movement analysis' })).toBeTruthy();
    expect(screen.getByText('Internal transfer activity')).toBeTruthy();
  });

  it('selects an official dashboard as the default without making its immutable template editable', async () => {
    renderDashboard('/analytics/dashboards/movement-analysis');
    await screen.findByRole('heading', { name: 'Movement analysis' });

    fireEvent.click(screen.getByRole('button', { name: 'Set as default' }));
    await waitFor(() => expect(setPersonalDashboardDefault).toHaveBeenCalledWith(null, null, 'movement-analysis'));
    expect(document.querySelector('[data-cy="recommendedDashboardDefault"]')?.textContent).toBe('Default');
  });

  it('uses a local temporary interval without mutating the canonical default or adding a global control', async () => {
    const firstRender = renderDashboard();
    await screen.findByRole('heading', { name: 'Financial overview' });
    await waitFor(() => expect(executeSemanticQuery.mock.calls.length).toBeGreaterThanOrEqual(7));
    expect(screen.queryByLabelText('Trend by')).toBeNull();
    const initialTrend = executeSemanticQuery.mock.calls.find(
      ([definition]) =>
        definition.projections.length === 3 &&
        definition.projections.some((projection: { alias: string }) => projection.alias === 'flujo_neto'),
    );
    expect(initialTrend?.[0].groupBy).toEqual(['TRANSACTION_MONTH']);
    expect(initialTrend?.[0].orderBy).toEqual([{ field: 'TRANSACTION_MONTH', projectionAlias: null, direction: 'ASC' }]);

    const interval = document.querySelector('[data-cy="recommendedWidgetInterval-economic-trend"]');
    expect(interval.value).toBe('TRANSACTION_MONTH');
    fireEvent.change(interval, { target: { value: 'TRANSACTION_WEEK' } });
    await waitFor(() =>
      expect(
        executeSemanticQuery.mock.calls.some(
          ([definition]) =>
            definition.projections.length === 3 &&
            definition.groupBy[0] === 'TRANSACTION_WEEK' &&
            definition.orderBy[0]?.field === 'TRANSACTION_WEEK',
        ),
      ).toBe(true),
    );
    // The template object and its initial request stay canonical; only the
    // effective request above used the selected local interval.
    expect(initialTrend?.[0].groupBy).toEqual(['TRANSACTION_MONTH']);

    firstRender.unmount();
    renderDashboard();
    await screen.findByRole('heading', { name: 'Financial overview' });
    expect(document.querySelector('[data-cy="recommendedWidgetInterval-economic-trend"]')?.value).toBe('TRANSACTION_MONTH');
  });

  it('keeps current credit facts explicitly inapplicable for a non-current dashboard context', async () => {
    renderDashboard('/analytics/dashboards/accounts-and-position');
    await screen.findByRole('heading', { name: 'Accounts and registered position' });
    fireEvent.change(screen.getByLabelText('Period'), { target: { value: 'ABSOLUTE_RANGE' } });
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2025-01-01' } });
    fireEvent.change(screen.getByLabelText('Through'), { target: { value: '2025-01-31' } });

    expect(await screen.findAllByText(/Select today only to view it/)).toHaveLength(3);
    expect(screen.getByText(/recorded debt/i)).toBeTruthy();
  });

  it('replaces the category ordering when drilling down and restores the unchanged root query on back', async () => {
    renderDashboard('/analytics/dashboards/movement-analysis');
    await screen.findByRole('heading', { name: 'Movement analysis' });
    await waitFor(() => expect(executeSemanticQuery.mock.calls.length).toBeGreaterThanOrEqual(8));

    const rootCall = executeSemanticQuery.mock.calls.find(
      ([definition]) =>
        definition.groupBy.length === 1 && definition.groupBy[0] === 'CATEGORY_ROOT' && definition.orderBy[0]?.field === 'CATEGORY_ROOT',
    );
    expect(rootCall).toBeTruthy();
    const originalDefinition = rootCall?.[0];
    const originalContext = rootCall?.[1];

    fireEvent.click(screen.getByRole('button', { name: 'Drill category hierarchy' }));
    await waitFor(() =>
      expect(
        executeSemanticQuery.mock.calls.some(
          ([definition]) =>
            definition.groupBy[0] === 'CATEGORY_BRANCH_CHILD' &&
            definition.orderBy[0]?.field === 'CATEGORY_BRANCH_CHILD' &&
            definition.orderBy[0]?.projectionAlias === null &&
            definition.predicates.some(
              (predicate: { field: string; operator: string; values: string[] }) =>
                predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF' && predicate.values[0] === '42',
            ),
        ),
      ).toBe(true),
    );

    const drilledCall = executeSemanticQuery.mock.calls.find(
      ([definition]) =>
        definition.groupBy[0] === 'CATEGORY_BRANCH_CHILD' &&
        definition.predicates.some(
          (predicate: { field: string; operator: string }) => predicate.field === 'CATEGORY_ID' && predicate.operator === 'DESCENDANT_OF',
        ),
    );
    expect(drilledCall?.[1]).toEqual(originalContext);

    fireEvent.click(screen.getByRole('button', { name: 'Back category hierarchy' }));
    await waitFor(() =>
      expect(
        executeSemanticQuery.mock.calls.some(([definition, context]) => definition === originalDefinition && context === originalContext),
      ).toBe(true),
    );
  });
});
