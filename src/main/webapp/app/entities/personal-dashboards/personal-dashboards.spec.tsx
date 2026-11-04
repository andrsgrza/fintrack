import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import PersonalDashboardGallery from './personal-dashboard-gallery';
import PersonalDashboardViewer from './personal-dashboard-viewer';

const getSemanticAnalyticsCatalog = jest.fn();
const getExplorerAccounts = jest.fn();
const listPersonalDashboards = jest.fn();
const createPersonalDashboard = jest.fn();
const copyOfficialDashboardTemplate = jest.fn();
const getPersonalDashboard = jest.fn();
const useDashboardWidgetResults = jest.fn();

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('app/entities/analytics-explorer/analytics-explorer-api', () => ({
  getSemanticAnalyticsCatalog: () => getSemanticAnalyticsCatalog(),
  getExplorerAccounts: () => getExplorerAccounts(),
}));
jest.mock('app/entities/recommended-dashboards/recommended-dashboards', () => ({
  dashboardDiagnostics: (error: unknown) => (error instanceof Error ? error.message : 'Request failed'),
  useDashboardWidgetResults: (...args: unknown[]) => useDashboardWidgetResults(...args),
}));
jest.mock('app/entities/analytics-explorer/analytics-results-panel', () => ({
  AnalyticsResultsPanel: ({
    definition,
    visualization,
  }: {
    definition: { projections: Array<{ alias: string }> };
    visualization: string;
  }) => <div data-cy={`sharedResult-${definition.projections[0]?.alias ?? 'none'}`}>{visualization}</div>,
}));
jest.mock('./personal-dashboard-api', () => ({
  listPersonalDashboards: () => listPersonalDashboards(),
  createPersonalDashboard: (...args: unknown[]) => createPersonalDashboard(...args),
  copyOfficialDashboardTemplate: (...args: unknown[]) => copyOfficialDashboardTemplate(...args),
  getPersonalDashboard: (...args: unknown[]) => getPersonalDashboard(...args),
}));

const catalog = {
  schemaVersion: 1,
  semanticModel: 'FINTRACK_FINANCIAL',
  catalogVersion: 'test',
  supportedRelativePeriods: ['THIS_MONTH', 'THIS_YEAR'],
  defaultLimit: 100,
  maxLimit: 100,
  authenticatedOwnerRequired: true,
  datasets: [],
};
const relativeContext = {
  schemaVersion: 1,
  time: { mode: 'RELATIVE' as const, relativePeriod: 'THIS_MONTH', from: null, through: null },
  accountIds: [],
  currencyCodes: [],
};
const result = {
  schemaVersion: 1,
  semanticModel: 'FINTRACK_FINANCIAL',
  effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: [] },
  executedAt: '2026-01-31T00:00:00Z',
  columns: [],
  rows: [],
};
const blankDashboard = { id: 44, name: 'My dashboard', description: null, version: 0, widgetCount: 0, updatedAt: '2026-01-01T00:00:00Z' };
const personalDashboard = {
  ...blankDashboard,
  schemaVersion: 1,
  createdAt: '2026-01-01T00:00:00Z',
  defaultContext: relativeContext,
  widgets: [
    {
      id: 71,
      title: 'Expense total',
      description: 'Economic expenses',
      definition: {
        schemaVersion: 1,
        semanticModel: 'FINTRACK_FINANCIAL',
        dataset: 'TRANSACTION_LEDGER',
        timeBinding: 'TRANSACTION_DATE',
        projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
        predicates: [],
        groupBy: [],
        orderBy: [],
        limit: 100,
      },
      visualizationType: 'KPI',
      visualizationOptions: {},
      position: 0,
      width: 'COMPACT',
      visible: true,
    },
    {
      id: 72,
      title: 'Unavailable widget',
      description: null,
      definition: {
        schemaVersion: 1,
        semanticModel: 'FINTRACK_FINANCIAL',
        dataset: 'TRANSACTION_LEDGER',
        timeBinding: 'TRANSACTION_DATE',
        projections: [{ alias: 'conteo', function: 'COUNT', field: 'TRANSACTION_ID', conditions: [] }],
        predicates: [],
        groupBy: [],
        orderBy: [],
        limit: 100,
      },
      visualizationType: 'KPI',
      visualizationOptions: {},
      position: 1,
      width: 'COMPACT',
      visible: true,
    },
  ],
};

const Location = () => <output data-cy="location">{useLocation().pathname}</output>;
const renderGallery = () =>
  render(
    <MemoryRouter initialEntries={['/analytics/dashboards']}>
      <Routes>
        <Route path="/analytics/dashboards" element={<PersonalDashboardGallery />} />
        <Route path="/analytics/dashboards/personal/:dashboardId" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  );
const renderViewer = () =>
  render(
    <MemoryRouter initialEntries={['/analytics/dashboards/personal/44']}>
      <Routes>
        <Route path="/analytics/dashboards/personal/:dashboardId" element={<PersonalDashboardViewer />} />
      </Routes>
    </MemoryRouter>,
  );

describe('personal dashboards V2-3', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getSemanticAnalyticsCatalog.mockResolvedValue(catalog);
    getExplorerAccounts.mockResolvedValue([{ id: 7, name: 'Daily account', accountType: 'DEBIT', currency: 'MXN' }]);
    listPersonalDashboards.mockResolvedValue([]);
    createPersonalDashboard.mockResolvedValue({ ...blankDashboard, defaultContext: relativeContext, widgets: [], schemaVersion: 1 });
    copyOfficialDashboardTemplate.mockResolvedValue({
      ...blankDashboard,
      id: 45,
      defaultContext: relativeContext,
      widgets: [],
      schemaVersion: 1,
    });
    getPersonalDashboard.mockResolvedValue(personalDashboard);
    useDashboardWidgetResults.mockImplementation((widgets: Array<{ id: number }>) => ({
      [widgets[0]?.id]: { loading: false, result, error: null },
      [widgets[1]?.id]: { loading: false, result: null, error: 'Temporary service failure' },
    }));
  });

  it('shows exactly three recommended templates and metadata-only personal dashboards', async () => {
    listPersonalDashboards.mockResolvedValue([{ ...blankDashboard, widgetCount: 2, description: 'Saved view' }]);
    renderGallery();

    expect(await screen.findByRole('heading', { name: 'Recommended' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Financial overview' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Movement analysis' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Accounts and registered position' })).toBeTruthy();
    expect(screen.getByRole('link', { name: /My dashboard/ })).toBeTruthy();
    expect(getExplorerAccounts).not.toHaveBeenCalled();
  });

  it('creates a blank dashboard with a relative all-account context and navigates to its empty viewer', async () => {
    renderGallery();
    await screen.findByRole('heading', { name: 'Recommended' });
    fireEvent.click(screen.getAllByRole('button', { name: 'Create dashboard' })[0]);
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Travel plans' } });
    fireEvent.submit(screen.getByLabelText('Name').closest('form'));

    await waitFor(() => expect(createPersonalDashboard).toHaveBeenCalled());
    expect(createPersonalDashboard.mock.calls[0][0]).toEqual(
      expect.objectContaining({ name: 'Travel plans', defaultContext: expect.objectContaining({ accountIds: [], currencyCodes: [] }) }),
    );
    expect(createPersonalDashboard.mock.calls[0][0].defaultContext.time).toEqual({
      mode: 'RELATIVE',
      relativePeriod: 'THIS_MONTH',
      from: null,
      through: null,
    });
    expect(document.querySelector('[data-cy="location"]')?.textContent).toBe('/analytics/dashboards/personal/44');
  });

  it('surfaces duplicate-name failures without navigating away from the creation flow', async () => {
    createPersonalDashboard.mockRejectedValueOnce(new Error('A personal dashboard with that name already exists.'));
    renderGallery();
    await screen.findByRole('heading', { name: 'Recommended' });
    fireEvent.click(screen.getAllByRole('button', { name: 'Create dashboard' })[0]);
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Duplicate' } });
    fireEvent.submit(screen.getByLabelText('Name').closest('form'));
    expect(await screen.findByText(/already exists/i)).toBeTruthy();
    expect(createPersonalDashboard).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['financial overview', 0, 'financial-overview'],
    ['movement analysis', 1, 'movement-analysis'],
    ['accounts and position', 2, 'accounts-and-position'],
  ])('creates an independent copy from the %s recommendation', async (_label, templateIndex, templateId) => {
    renderGallery();
    await screen.findByRole('heading', { name: 'Recommended' });
    fireEvent.click(screen.getAllByRole('button', { name: 'Use as a starting point' })[templateIndex]);
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Independent overview' } });
    fireEvent.submit(screen.getByLabelText('Name').closest('form'));
    await waitFor(() => expect(copyOfficialDashboardTemplate).toHaveBeenCalled());
    const command = copyOfficialDashboardTemplate.mock.calls[0][0];
    expect(command.templateId).toBe(templateId);
    expect(command.widgets.length).toBeGreaterThan(1);
    expect(command.widgets.every((widget: { definition: unknown }) => !!widget.definition)).toBe(true);
    expect(command.widgets.every((widget: { definition: Record<string, unknown> }) => !('context' in widget.definition))).toBe(true);
  });

  it('executes persisted widgets with a temporary viewer context and isolates one failing widget', async () => {
    renderViewer();
    expect(await screen.findByRole('heading', { name: 'My dashboard' })).toBeTruthy();
    expect(useDashboardWidgetResults).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ definition: personalDashboard.widgets[0].definition })]),
      relativeContext,
    );
    expect(document.querySelector('[data-cy="sharedResult-gastos"]')).not.toBeNull();
    expect(await screen.findByText(/Temporary service failure/)).toBeTruthy();
    expect(screen.getByText(/temporary for this view/i)).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Period'), { target: { value: 'THIS_YEAR' } });
    await waitFor(() =>
      expect(useDashboardWidgetResults.mock.calls.some(([, nextContext]) => nextContext?.time?.relativePeriod === 'THIS_YEAR')).toBe(true),
    );
    expect(getPersonalDashboard).toHaveBeenCalledTimes(1);
  });
});
