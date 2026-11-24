import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';

import PersonalDashboardGallery from './personal-dashboard-gallery';
import PersonalDashboardViewer from './personal-dashboard-viewer';
import PersonalDashboardDefaultRedirect from './personal-dashboard-default-redirect';
import { Home } from 'app/modules/home/home';

const getSemanticAnalyticsCatalog = jest.fn();
const getExplorerAccounts = jest.fn();
const listPersonalDashboards = jest.fn();
const createPersonalDashboard = jest.fn();
const copyOfficialDashboardTemplate = jest.fn();
const getPersonalDashboard = jest.fn();
const savePersonalDashboardLayout = jest.fn();
const getPersonalDashboardDefault = jest.fn();
const setPersonalDashboardDefault = jest.fn();
const updatePersonalDashboardMetadata = jest.fn();
const duplicatePersonalDashboard = jest.fn();
const deletePersonalDashboard = jest.fn();
const updatePersonalDashboardWidget = jest.fn();
const duplicatePersonalDashboardWidget = jest.fn();
const deletePersonalDashboardWidget = jest.fn();
const useDashboardWidgetResults = jest.fn();
const useAppSelector = jest.fn();

jest.mock('app/config/store', () => ({ useAppSelector: (...args) => useAppSelector(...args) }));
jest.mock('app/entities/analytics-explorer/analytics-explorer-api', () => ({
  getSemanticAnalyticsCatalog: () => getSemanticAnalyticsCatalog(),
  getExplorerAccounts: () => getExplorerAccounts(),
}));
jest.mock('app/entities/recommended-dashboards/recommended-dashboards', () => ({
  dashboardDiagnostics: error => (error instanceof Error ? error.message : 'Request failed'),
  useDashboardWidgetResults: (...args) => useDashboardWidgetResults(...args),
}));
jest.mock('app/entities/analytics-explorer/analytics-results-panel', () => ({
  AnalyticsResultsPanel: ({ definition, visualization }) =>
    React.createElement('div', { 'data-cy': `sharedResult-${definition.projections[0]?.alias ?? 'none'}` }, visualization),
}));
jest.mock('./personal-dashboard-api', () => ({
  listPersonalDashboards: () => listPersonalDashboards(),
  createPersonalDashboard: (...args) => createPersonalDashboard(...args),
  copyOfficialDashboardTemplate: (...args) => copyOfficialDashboardTemplate(...args),
  getPersonalDashboard: (...args) => getPersonalDashboard(...args),
  savePersonalDashboardLayout: (...args) => savePersonalDashboardLayout(...args),
  getPersonalDashboardDefault: () => getPersonalDashboardDefault(),
  setPersonalDashboardDefault: (...args) => setPersonalDashboardDefault(...args),
  updatePersonalDashboardMetadata: (...args) => updatePersonalDashboardMetadata(...args),
  duplicatePersonalDashboard: (...args) => duplicatePersonalDashboard(...args),
  deletePersonalDashboard: (...args) => deletePersonalDashboard(...args),
  updatePersonalDashboardWidget: (...args) => updatePersonalDashboardWidget(...args),
  duplicatePersonalDashboardWidget: (...args) => duplicatePersonalDashboardWidget(...args),
  deletePersonalDashboardWidget: (...args) => deletePersonalDashboardWidget(...args),
}));

const catalog = {
  schemaVersion: 1,
  semanticModel: 'FINTRACK_FINANCIAL',
  catalogVersion: 'test',
  supportedRelativePeriods: ['THIS_MONTH', 'THIS_YEAR'],
  defaultLimit: 100,
  maxLimit: 100,
  authenticatedOwnerRequired: true,
  datasets: [
    {
      dataset: 'TRANSACTION_LEDGER',
      groupings: ['TRANSACTION_DAY', 'TRANSACTION_WEEK', 'TRANSACTION_MONTH', 'TRANSACTION_YEAR', 'CATEGORY_ROOT'],
    },
  ],
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
        <Route path="/analytics/dashboards" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  );
const renderDefaultLanding = () =>
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/analytics/dashboards/default" element={<PersonalDashboardDefaultRedirect />} />
        <Route path="/analytics/dashboards/personal/:dashboardId" element={<Location />} />
        <Route path="/analytics/dashboards/:dashboardId" element={<Location />} />
      </Routes>
    </MemoryRouter>,
  );

describe('personal dashboards V2-3', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useAppSelector.mockReturnValue('en');
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
    getPersonalDashboardDefault.mockResolvedValue({ dashboardId: null, officialTemplateId: 'financial-overview', version: null });
    setPersonalDashboardDefault.mockResolvedValue({ dashboardId: 44, officialTemplateId: null, version: 0 });
    updatePersonalDashboardMetadata.mockImplementation((_dashboardId: number, command: { name: string; description: string | null }) =>
      Promise.resolve({ ...personalDashboard, name: command.name, description: command.description, version: 1 }),
    );
    duplicatePersonalDashboard.mockResolvedValue({ ...personalDashboard, id: 45, name: 'My dashboard (copy)', version: 0 });
    deletePersonalDashboard.mockResolvedValue(undefined);
    updatePersonalDashboardWidget.mockImplementation(
      (_dashboardId: number, widgetId: number, command: { title: string; description: string | null }) =>
        Promise.resolve({
          ...personalDashboard,
          version: 1,
          widgets: personalDashboard.widgets.map(widget =>
            widget.id === widgetId ? { ...widget, title: command.title, description: command.description } : widget,
          ),
        }),
    );
    duplicatePersonalDashboardWidget.mockResolvedValue({
      ...personalDashboard,
      version: 1,
      widgets: [
        ...personalDashboard.widgets,
        { ...personalDashboard.widgets[0], id: 73, title: 'Expense total (copy)', position: 2, visible: true },
      ],
    });
    deletePersonalDashboardWidget.mockImplementation((_dashboardId: number, widgetId: number) =>
      Promise.resolve({
        ...personalDashboard,
        version: 1,
        widgets: personalDashboard.widgets.filter(widget => widget.id !== widgetId).map((widget, position) => ({ ...widget, position })),
      }),
    );
    savePersonalDashboardLayout.mockImplementation(
      (_dashboardId: number, command: { widgets: Array<{ widgetId: number; width: string; visible: boolean }> }) =>
        Promise.resolve({
          ...personalDashboard,
          version: personalDashboard.version + 1,
          widgets: command.widgets.map((item, position) => ({
            ...personalDashboard.widgets.find(widget => widget.id === item.widgetId),
            ...item,
            id: item.widgetId,
            position,
          })),
        }),
    );
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
    expect(screen.getByRole('heading', { name: 'My dashboard' })).toBeTruthy();
    expect(getExplorerAccounts).not.toHaveBeenCalled();
  });

  it('marks and persists one personal dashboard as the default from the gallery', async () => {
    listPersonalDashboards.mockResolvedValue([{ ...blankDashboard, widgetCount: 2, description: 'Saved view' }]);
    renderGallery();
    await screen.findByRole('heading', { name: 'My dashboards' });

    fireEvent.click(document.querySelector('[data-cy="personalDashboardSetDefault-44"]'));
    await waitFor(() => expect(setPersonalDashboardDefault).toHaveBeenCalledWith(null, 44));
    expect(document.querySelector('[data-cy="personalDashboardDefault-44"]')?.textContent).toBe('Default');
  });

  it('marks and persists an immutable official dashboard as the default without creating a personal copy', async () => {
    setPersonalDashboardDefault.mockResolvedValueOnce({ dashboardId: null, officialTemplateId: 'movement-analysis', version: 0 });
    renderGallery();
    await screen.findByRole('heading', { name: 'Recommended' });

    fireEvent.click(document.querySelector('[data-cy="recommendedDashboardSetDefault-movement-analysis"]'));
    await waitFor(() => expect(setPersonalDashboardDefault).toHaveBeenCalledWith(null, null, 'movement-analysis'));
    expect(document.querySelector('[data-cy="recommendedDashboardDefault-movement-analysis"]')?.textContent).toBe('Default');
    expect(createPersonalDashboard).not.toHaveBeenCalled();
    expect(copyOfficialDashboardTemplate).not.toHaveBeenCalled();
  });

  it('revisits the personal default from Home and falls back to Financial overview when none is stored', async () => {
    useAppSelector.mockImplementation(selector => selector({ locale: 'en', authentication: { account: { login: 'user' } } }));
    getPersonalDashboardDefault.mockResolvedValueOnce({ dashboardId: 44, officialTemplateId: null, version: 0 });
    const first = renderDefaultLanding();
    await waitFor(() => expect(document.querySelector('[data-cy="location"]')?.textContent).toBe('/analytics/dashboards/personal/44'));
    first.unmount();

    getPersonalDashboardDefault.mockResolvedValueOnce({ dashboardId: null, officialTemplateId: 'movement-analysis', version: 1 });
    renderDefaultLanding();
    await waitFor(() =>
      expect(document.querySelector('[data-cy="location"]')?.textContent).toBe('/analytics/dashboards/movement-analysis'),
    );
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

  it.each([
    ['en', 'The dashboard starts empty. Add visualizations from Metric Explorer.'],
    ['es', 'El dashboard iniciará vacío. Agrega visualizaciones desde el Explorador de métricas.'],
  ])('explains how to add visualizations to a new blank dashboard in %s', async (locale, expectedCopy) => {
    useAppSelector.mockReturnValue(locale);
    renderGallery();
    await screen.findByRole('heading', { name: locale === 'es' ? 'Recomendados' : 'Recommended' });

    fireEvent.click(screen.getAllByRole('button', { name: locale === 'es' ? 'Crear dashboard' : 'Create dashboard' })[0]);

    expect(screen.getByText(expectedCopy)).toBeTruthy();
    expect(screen.queryByText(/próxima versión|upcoming version/i)).toBeNull();
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

  it('edits only dashboard metadata from a compact management dialog', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardManage"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardEditMetadata"]'));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Renamed dashboard' } });
    fireEvent.change(screen.getByLabelText('Optional description'), { target: { value: 'A concise description' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(updatePersonalDashboardMetadata).toHaveBeenCalledWith(44, {
        expectedVersion: 0,
        name: 'Renamed dashboard',
        description: 'A concise description',
      }),
    );
    expect(await screen.findByRole('heading', { name: 'Renamed dashboard' })).toBeTruthy();
    expect(savePersonalDashboardLayout).not.toHaveBeenCalled();
  });

  it('keeps personal widget actions in the overflow menu when no interval selector applies', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });

    const header = document.querySelector('[data-cy="personalDashboardWidgetHeader-71"]');
    expect(header?.classList.contains('personal-dashboard-widget-header')).toBe(true);
    expect(header?.querySelector('[data-cy="personalDashboardWidgetTitle-71"]')?.textContent).toContain('Expense total');
    expect(header?.querySelector('[data-cy="personalDashboardWidgetDescription-71"]')?.textContent).toContain('Economic expenses');
    expect(document.querySelector('[data-cy="personalDashboardEditWidget-71"]')).toBeNull();
    expect(document.querySelector('[data-cy="personalDashboardWidgetInterval-71"]')).toBeNull();
    const menu = document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]');
    expect(menu).not.toBeNull();

    fireEvent.click(menu);
    expect(document.querySelector('[data-cy="personalDashboardWidgetRename-71"]')).not.toBeNull();
    expect(document.querySelector('[data-cy="personalDashboardWidgetMenuEdit-71"]')).not.toBeNull();
    expect(document.querySelector('[data-cy="personalDashboardWidgetDuplicate-71"]')).not.toBeNull();
    expect(document.querySelector('[data-cy="personalDashboardWidgetDelete-71"]')).not.toBeNull();
  });

  it('keeps title actions separate from long descriptions across widget widths', async () => {
    const longDescription =
      'This longer description explains the financial calculation in enough detail to wrap naturally without moving the widget actions below it.';
    const longTitle = 'A deliberately long widget title that must still leave the menu anchored at the top of the header';
    getPersonalDashboard.mockResolvedValueOnce({
      ...personalDashboard,
      widgets: [
        { ...personalDashboard.widgets[0], id: 81, title: 'Short description', description: 'Short description', width: 'COMPACT' },
        { ...personalDashboard.widgets[0], id: 82, title: longTitle, description: longDescription, width: 'MEDIUM' },
        {
          ...personalDashboard.widgets[0],
          id: 83,
          title: longTitle,
          description: longDescription,
          width: 'FULL',
          definition: { ...personalDashboard.widgets[0].definition, groupBy: ['TRANSACTION_MONTH'] },
        },
      ],
    });
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });

    [81, 82, 83].forEach(widgetId => {
      const header = document.querySelector(`[data-cy="personalDashboardWidgetHeader-${widgetId}"]`);
      const actions = document.querySelector(`[data-cy="personalDashboardWidgetActions-${widgetId}"]`);
      expect(header?.querySelector(`[data-cy="personalDashboardWidgetTitle-${widgetId}"]`)).not.toBeNull();
      expect(header?.querySelector(`[data-cy="personalDashboardWidgetDescription-${widgetId}"]`)).not.toBeNull();
      expect(actions?.querySelector(`[data-cy="personalDashboardWidgetMenu-${widgetId}"]`)).not.toBeNull();
    });
    expect(document.querySelector('[data-cy="personalDashboardWidgetInterval-81"]')).toBeNull();
    expect(document.querySelector('[data-cy="personalDashboardWidgetInterval-82"]')).toBeNull();
    expect(screen.getByLabelText('Interval').value).toBe('TRANSACTION_MONTH');
  });

  it('keeps interval and overflow actions together in the header of a temporal widget', async () => {
    getPersonalDashboard.mockResolvedValueOnce({
      ...personalDashboard,
      widgets: [
        {
          ...personalDashboard.widgets[0],
          definition: { ...personalDashboard.widgets[0].definition, groupBy: ['TRANSACTION_MONTH'] },
        },
      ],
    });
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });

    expect(screen.getByLabelText('Interval').value).toBe('TRANSACTION_MONTH');
    expect(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]')).not.toBeNull();
    expect(document.querySelector('[data-cy="personalDashboardEditWidget-71"]')).toBeNull();
  });

  it('manages a widget from its accessible menu without altering its saved query or layout', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetRename-71"]'));
    expect(screen.getByLabelText('Visualization title').value).toBe('Expense total');
    fireEvent.change(screen.getByLabelText('Visualization title'), { target: { value: 'Updated expense total' } });
    fireEvent.change(screen.getByLabelText('Optional description'), { target: { value: 'Updated explanation' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(updatePersonalDashboardWidget).toHaveBeenCalledWith(
        44,
        71,
        expect.objectContaining({
          expectedVersion: 0,
          title: 'Updated expense total',
          description: 'Updated explanation',
          definition: personalDashboard.widgets[0].definition,
          visualizationType: 'KPI',
          width: 'COMPACT',
          visible: true,
        }),
      ),
    );
    expect(await screen.findByRole('heading', { name: 'Updated expense total' })).toBeTruthy();
    expect(savePersonalDashboardLayout).not.toHaveBeenCalled();
  });

  it('cancels widget metadata editing without sending a mutation', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetRename-71"]'));
    fireEvent.change(screen.getByLabelText('Visualization title'), { target: { value: 'Should not save' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(updatePersonalDashboardWidget).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Change name and description' })).toBeNull());
  });

  it('keeps the rename dialog open and explains a concurrent update failure', async () => {
    updatePersonalDashboardWidget.mockRejectedValueOnce(new Error('Dashboard changed elsewhere'));
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetRename-71"]'));
    fireEvent.change(screen.getByLabelText('Visualization title'), { target: { value: 'Conflicting title' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Dashboard changed elsewhere')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Change name and description' })).toBeTruthy();
    expect(screen.getByLabelText('Visualization title').value).toBe('Conflicting title');
  });

  it('duplicates a widget from its menu and keeps the original in place', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetDuplicate-71"]'));

    await waitFor(() =>
      expect(duplicatePersonalDashboardWidget).toHaveBeenCalledWith(44, 71, expect.objectContaining({ expectedVersion: 0 })),
    );
    expect(await screen.findByRole('heading', { name: 'Expense total (copy)' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Expense total' })).toBeTruthy();
  });

  it('explains a failed duplication and leaves the original widget intact', async () => {
    duplicatePersonalDashboardWidget.mockRejectedValueOnce(new Error('Dashboard changed elsewhere'));
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetDuplicate-71"]'));

    await waitFor(() =>
      expect(document.querySelector('[data-cy="personalDashboardManagementError"]')?.textContent).toContain('Dashboard changed elsewhere'),
    );
    expect(screen.getByRole('heading', { name: 'Expense total' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Expense total (copy)' })).toBeNull();
  });

  it('confirms widget deletion and refreshes only after backend success', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetDelete-71"]'));
    expect(screen.getByText(/permanently delete “Expense total”/i)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(deletePersonalDashboardWidget).not.toHaveBeenCalled();

    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetDelete-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetManageSubmit"]'));
    await waitFor(() => expect(deletePersonalDashboardWidget).toHaveBeenCalledWith(44, 71, 0));
    await waitFor(() => expect(document.querySelector('[data-cy="personalDashboardWidget-71"]')).toBeNull());
    expect(document.querySelector('[data-cy="personalDashboardWidget-72"]')).not.toBeNull();
  });

  it('keeps deletion confirmation open when the current dashboard version is stale', async () => {
    deletePersonalDashboardWidget.mockRejectedValueOnce(new Error('Dashboard changed elsewhere'));
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetMenu-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetDelete-71"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardWidgetManageSubmit"]'));

    expect(await screen.findByText('Dashboard changed elsewhere')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Delete widget' })).toBeTruthy();
    expect(document.querySelector('[data-cy="personalDashboardWidget-71"]')).not.toBeNull();
  });

  it('keeps widget lifecycle commands out of unsaved layout customization', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(screen.getByRole('button', { name: 'Customize dashboard' }));
    expect(document.querySelector('[data-cy^="personalDashboardWidgetMenu-"]')).toBeNull();
    fireEvent.click(document.querySelector('[data-cy="personalDashboardHide-71"]'));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(duplicatePersonalDashboardWidget).not.toHaveBeenCalled();
    expect(deletePersonalDashboardWidget).not.toHaveBeenCalled();
  });

  it('reopens management controls after saving metadata', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardManage"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardEditMetadata"]'));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Saved dashboard' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await screen.findByRole('heading', { name: 'Saved dashboard' });

    fireEvent.click(document.querySelector('[data-cy="personalDashboardManage"]'));
    fireEvent.click(await screen.findByText('Duplicate dashboard'));
    expect(screen.getByLabelText('Optional description').disabled).toBe(false);
  });

  it('duplicates a dashboard independently', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardManage"]'));
    fireEvent.click(await screen.findByText('Duplicate dashboard'));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Independent dashboard' } });
    fireEvent.click(screen.getByRole('button', { name: 'Duplicate dashboard' }));
    await waitFor(() =>
      expect(duplicatePersonalDashboard).toHaveBeenCalledWith(44, expect.objectContaining({ name: 'Independent dashboard' })),
    );
    await waitFor(() => expect(getPersonalDashboard).toHaveBeenCalledTimes(2));
    expect(deletePersonalDashboard).not.toHaveBeenCalled();
  });

  it('requires confirmation before deletion', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardManage"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardDelete"]'));
    expect(screen.getByText(/will delete “My dashboard”/i)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(deletePersonalDashboard).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Delete dashboard' })).toBeNull());

    fireEvent.click(document.querySelector('[data-cy="personalDashboardManage"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardDelete"]'));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardManageSubmit"]'));
    await waitFor(() => expect(deletePersonalDashboard).toHaveBeenCalledWith(44, 0));
    expect(document.querySelector('[data-cy="location"]')?.textContent).toBe('/analytics/dashboards');
  });

  it('reloads a saved ordered grouping exactly as its canonical widget query', async () => {
    const groupedDefinition = {
      ...personalDashboard.widgets[0].definition,
      groupBy: ['CATEGORY_ROOT', 'TRANSACTION_MONTH'],
      orderBy: [
        { field: 'CATEGORY_ROOT', projectionAlias: null, direction: 'ASC' as const },
        { field: 'TRANSACTION_MONTH', projectionAlias: null, direction: 'ASC' as const },
      ],
    };
    getPersonalDashboard.mockResolvedValue({
      ...personalDashboard,
      widgets: [{ ...personalDashboard.widgets[0], definition: groupedDefinition }],
    });
    useDashboardWidgetResults.mockImplementation((widgets: Array<{ id: number }>) => ({
      [widgets[0]?.id]: { loading: false, result, error: null },
    }));

    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    await waitFor(() => expect(useDashboardWidgetResults).toHaveBeenCalled());
    expect(useDashboardWidgetResults.mock.calls.at(-1)?.[0][0].definition).toEqual(groupedDefinition);
  });

  it('uses a local interval execution copy and restores the saved default on reload', async () => {
    const temporalDefinition = {
      ...personalDashboard.widgets[0].definition,
      groupBy: ['TRANSACTION_MONTH'],
      orderBy: [{ field: 'TRANSACTION_MONTH', projectionAlias: null, direction: 'ASC' as const }],
    };
    const storedDashboard = {
      ...personalDashboard,
      widgets: [{ ...personalDashboard.widgets[0], definition: temporalDefinition }],
    };
    getPersonalDashboard.mockResolvedValue(storedDashboard);
    useDashboardWidgetResults.mockImplementation((widgets: Array<{ id: number }>) => ({
      [widgets[0]?.id]: { loading: false, result, error: null },
    }));

    const firstRender = renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    const interval = document.querySelector('[data-cy="personalDashboardWidgetInterval-71"]');
    expect(interval.value).toBe('TRANSACTION_MONTH');
    fireEvent.change(interval, { target: { value: 'TRANSACTION_WEEK' } });
    await waitFor(() =>
      expect(
        useDashboardWidgetResults.mock.calls.some(
          ([widgets]) =>
            widgets[0]?.definition.groupBy[0] === 'TRANSACTION_WEEK' && widgets[0]?.definition.orderBy[0]?.field === 'TRANSACTION_WEEK',
        ),
      ).toBe(true),
    );
    expect(temporalDefinition.groupBy).toEqual(['TRANSACTION_MONTH']);
    expect(temporalDefinition.orderBy[0].field).toBe('TRANSACTION_MONTH');

    firstRender.unmount();
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });
    expect(document.querySelector('[data-cy="personalDashboardWidgetInterval-71"]')?.value).toBe('TRANSACTION_MONTH');
  });

  it('keeps layout changes local until save, then persists only the complete layout command', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });

    fireEvent.click(screen.getByRole('button', { name: 'Customize dashboard' }));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardMoveDown-71"]'));
    fireEvent.change(document.querySelector('[data-cy="personalDashboardWidth-71"]'), {
      target: { value: 'FULL' },
    });
    fireEvent.click(document.querySelector('[data-cy="personalDashboardHide-72"]'));

    expect(savePersonalDashboardLayout).not.toHaveBeenCalled();
    expect(document.querySelector('[data-cy="personalDashboardHiddenWidgets"]')?.textContent).toContain('Unavailable widget');
    await waitFor(() => {
      const lastWidgets = useDashboardWidgetResults.mock.calls.at(-1)?.[0];
      expect(lastWidgets).toHaveLength(1);
      expect(lastWidgets[0]?.id).toBe(71);
    });

    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(savePersonalDashboardLayout).toHaveBeenCalledTimes(1));
    expect(savePersonalDashboardLayout).toHaveBeenCalledWith(44, {
      expectedVersion: 0,
      widgets: [
        { widgetId: 72, width: 'COMPACT', visible: false },
        { widgetId: 71, width: 'FULL', visible: true },
      ],
    });
    expect(personalDashboard.widgets.map(widget => widget.definition)).toEqual([
      personalDashboard.widgets[0].definition,
      personalDashboard.widgets[1].definition,
    ]);
    expect(screen.queryByText('Unsaved changes')).toBeNull();
  });

  it('cancels a draft without an API mutation and exposes hidden widgets for recovery', async () => {
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });

    fireEvent.click(screen.getByRole('button', { name: 'Customize dashboard' }));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardHide-71"]'));
    expect(document.querySelector('[data-cy="personalDashboardHiddenWidgets"]')?.textContent).toContain('Expense total');
    fireEvent.click(document.querySelector('[data-cy="personalDashboardPreset-COMPACT"]'));
    expect(screen.queryByRole('button', { name: 'Restore layout' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(document.querySelector('[data-cy="personalDashboardWidget-71"]')).not.toBeNull());
    await waitFor(() => expect(document.querySelector('[data-cy="personalDashboardWidget-72"]')).not.toBeNull());
    expect(savePersonalDashboardLayout).not.toHaveBeenCalled();
  });

  it('keeps a failed layout save as an editable local draft', async () => {
    savePersonalDashboardLayout.mockRejectedValueOnce(new Error('Version conflict'));
    renderViewer();
    await screen.findByRole('heading', { name: 'My dashboard' });

    fireEvent.click(screen.getByRole('button', { name: 'Customize dashboard' }));
    fireEvent.click(document.querySelector('[data-cy="personalDashboardHide-72"]'));
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText(/layout changes could not be saved/i)).toBeTruthy();
    expect(document.querySelector('[data-cy="personalDashboardHiddenWidgets"]')?.textContent).toContain('Unavailable widget');
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeTruthy();
  });
});
