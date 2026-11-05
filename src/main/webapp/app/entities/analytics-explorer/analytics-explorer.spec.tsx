import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import AnalyticsExplorer from './analytics-explorer';
import { groupCategoryChildren, replaceGrouping } from './analytics-grouping';
import { SemanticAnalyticsCatalog, SemanticQueryDefinition } from './analytics-explorer.model';
import {
  AnalyticsResultsPanel,
  ResultPanel,
  chartAxisScale,
  chartRows,
  formatMoney,
  visualizationEligibility,
} from './analytics-results-panel';

const executeSemanticQuery = jest.fn();
const formatSemanticQuery = jest.fn();
const getExplorerAccounts = jest.fn();
const getExplorerCategories = jest.fn();
const getExplorerTags = jest.fn();
const getSemanticAnalyticsCatalog = jest.fn();
const parseSemanticQuery = jest.fn();
const listPersonalDashboards = jest.fn();
const getPersonalDashboard = jest.fn();
const createPersonalDashboard = jest.fn();
const addPersonalDashboardWidget = jest.fn();
const updatePersonalDashboardWidget = jest.fn();

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('./analytics-explorer-api', () => ({
  executeSemanticQuery: (...args: unknown[]) => executeSemanticQuery(...args),
  formatSemanticQuery: (...args: unknown[]) => formatSemanticQuery(...args),
  getExplorerAccounts: () => getExplorerAccounts(),
  getExplorerCategories: () => getExplorerCategories(),
  getExplorerTags: () => getExplorerTags(),
  getSemanticAnalyticsCatalog: () => getSemanticAnalyticsCatalog(),
  parseSemanticQuery: (...args: unknown[]) => parseSemanticQuery(...args),
}));
jest.mock('app/entities/personal-dashboards/personal-dashboard-api', () => ({
  listPersonalDashboards: () => listPersonalDashboards(),
  getPersonalDashboard: (...args: unknown[]) => getPersonalDashboard(...args),
  createPersonalDashboard: (...args: unknown[]) => createPersonalDashboard(...args),
  addPersonalDashboardWidget: (...args: unknown[]) => addPersonalDashboardWidget(...args),
  updatePersonalDashboardWidget: (...args: unknown[]) => updatePersonalDashboardWidget(...args),
}));

const ledger = {
  dataset: 'TRANSACTION_LEDGER',
  grain: 'one FinancialTransaction',
  timeBinding: 'TRANSACTION_DATE',
  relationships: [],
  fields: [],
  aggregateCapabilities: [
    { function: 'COUNT' as const, field: 'TRANSACTION_ID', outputUnit: 'COUNT' as const, additivity: 'ADDITIVE' },
    { function: 'SUM' as const, field: 'TRANSACTION_AMOUNT', outputUnit: 'MONEY' as const, additivity: 'ADDITIVE' },
    { function: 'AVG' as const, field: 'TRANSACTION_AMOUNT', outputUnit: 'MONEY' as const, additivity: 'NON_ADDITIVE' },
  ],
  predicateCapabilities: [
    { field: 'TRANSACTION_FLOW', operators: ['EQUALS' as const, 'IN' as const] },
    { field: 'TRANSFER_LINK_STATUS', operators: ['EQUALS' as const] },
    { field: 'CATEGORY_ID', operators: ['EQUALS' as const, 'IN' as const, 'DESCENDANT_OF' as const, 'IS_UNCATEGORIZED' as const] },
    { field: 'TAG_ID', operators: ['HAS_ANY' as const, 'HAS_ALL' as const, 'HAS_NONE' as const] },
  ],
  groupings: [
    'TRANSACTION_DAY',
    'TRANSACTION_WEEK',
    'TRANSACTION_MONTH',
    'TRANSACTION_YEAR',
    'ACCOUNT_INSTITUTION',
    'ACCOUNT_ID',
    'CATEGORY_ID',
    'CATEGORY_ROOT',
    'CATEGORY_SUBCATEGORY',
    'CATEGORY_BRANCH_CHILD',
    'TAG_ID',
  ],
  additivity: 'ADDITIVE',
  currencyPolicy: 'PARTITION_BY_ACCOUNT_CURRENCY',
  temporalSemantics: 'Inclusive transaction date.',
};

const catalog: SemanticAnalyticsCatalog = {
  schemaVersion: 1,
  semanticModel: 'FINTRACK_FINANCIAL',
  catalogVersion: '2.0',
  supportedRelativePeriods: ['THIS_MONTH'],
  defaultLimit: 100,
  maxLimit: 500,
  authenticatedOwnerRequired: true,
  datasets: [
    ledger,
    {
      ...ledger,
      dataset: 'ACCOUNT_POSITION_SNAPSHOT',
      timeBinding: 'OBSERVATION_DATE',
      aggregateCapabilities: [
        { function: 'SUM', field: 'POSITION_BALANCE', outputUnit: 'MONEY', additivity: 'POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH' },
      ],
      predicateCapabilities: [],
      groupings: ['ACCOUNT_ID', 'OBSERVATION_MONTH'],
    },
    {
      ...ledger,
      dataset: 'CREDIT_ACCOUNT_SNAPSHOT',
      timeBinding: 'OBSERVATION_DATE',
      aggregateCapabilities: [
        { function: 'SUM', field: 'CREDIT_DEBT', outputUnit: 'MONEY', additivity: 'POINT_IN_TIME_REQUIRES_ACCOUNT_AND_MONTH' },
      ],
      predicateCapabilities: [],
      groupings: ['ACCOUNT_ID', 'OBSERVATION_MONTH'],
    },
    {
      ...ledger,
      dataset: 'INTERNAL_TRANSFER_EVENT',
      timeBinding: 'TRANSFER_DATE',
      aggregateCapabilities: [{ function: 'COUNT', field: 'TRANSFER_ID', outputUnit: 'COUNT', additivity: 'ADDITIVE' }],
      predicateCapabilities: [],
      groupings: ['TRANSFER_MONTH', 'SOURCE_ACCOUNT_INSTITUTION'],
    },
  ],
};

const definition: SemanticQueryDefinition = {
  schemaVersion: 1,
  semanticModel: 'FINTRACK_FINANCIAL',
  dataset: 'TRANSACTION_LEDGER',
  timeBinding: 'TRANSACTION_DATE',
  projections: [{ alias: 'transactions', function: 'COUNT', field: 'TRANSACTION_ID', conditions: [] }],
  predicates: [],
  groupBy: [],
  orderBy: [],
  limit: 100,
};

const canonical = 'SELECT COUNT(TRANSACTION_ID) AS transactions FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE LIMIT 100';
const dashboardContext = {
  schemaVersion: 1,
  time: { mode: 'RELATIVE' as const, relativePeriod: 'THIS_MONTH', from: null, through: null },
  accountIds: [],
  currencyCodes: [],
};
const personalDashboard = {
  id: 41,
  name: 'My dashboard',
  description: null,
  schemaVersion: 1,
  version: 7,
  defaultContext: dashboardContext,
  createdAt: '2026-11-04T12:00:00Z',
  updatedAt: '2026-11-04T12:00:00Z',
  widgets: [
    {
      id: 91,
      title: 'Saved count',
      description: 'Existing widget',
      definition,
      visualizationType: 'BAR' as const,
      visualizationOptions: { seriesLimit: 5 },
      position: 3,
      width: 'FULL' as const,
      visible: false,
    },
  ],
};

const renderExplorer = (entry = '/analytics/explorer') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <AnalyticsExplorer />
    </MemoryRouter>,
  );

describe('Metric Explorer V2', () => {
  let parsedDefinition: SemanticQueryDefinition;

  beforeEach(() => {
    jest.clearAllMocks();
    parsedDefinition = definition;
    getSemanticAnalyticsCatalog.mockResolvedValue(catalog);
    getExplorerAccounts.mockResolvedValue([]);
    getExplorerCategories.mockResolvedValue([{ id: 14, name: 'Food', color: '#4477aa', active: true, parentId: null }]);
    getExplorerTags.mockResolvedValue([{ id: 27, name: 'Office', color: '#44aa77', active: true }]);
    formatSemanticQuery.mockImplementation((candidate: SemanticQueryDefinition) => {
      parsedDefinition = candidate;
      return Promise.resolve(canonical);
    });
    parseSemanticQuery.mockImplementation((text: string) =>
      text === 'broken query'
        ? Promise.reject(new Error('Invalid query'))
        : Promise.resolve({ definition: parsedDefinition, canonicalText: canonical }),
    );
    executeSemanticQuery.mockResolvedValue({
      schemaVersion: 1,
      semanticModel: 'FINTRACK_FINANCIAL',
      effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: [] },
      executedAt: '2026-01-31T12:00:00Z',
      columns: [{ id: 'transactions', dimension: false, unit: 'COUNT', currencyFaceted: false }],
      rows: [
        { dimensions: [], currencyCode: null, values: [{ alias: 'transactions', value: 3, unit: 'COUNT', availability: 'COMPLETE' }] },
      ],
    });
    listPersonalDashboards.mockResolvedValue([
      { id: 41, name: 'My dashboard', description: null, version: 7, widgetCount: 1, updatedAt: '2026-11-04T12:00:00Z' },
    ]);
    getPersonalDashboard.mockResolvedValue(personalDashboard);
    createPersonalDashboard.mockResolvedValue({ ...personalDashboard, id: 42, name: 'New dashboard', version: 1, widgets: [] });
    addPersonalDashboardWidget.mockResolvedValue(personalDashboard);
    updatePersonalDashboardWidget.mockResolvedValue(personalDashboard);
    jest.spyOn(window, 'confirm').mockReturnValue(true);
  });

  afterEach(() => jest.restoreAllMocks());

  it('keeps the compact entry experience and executes an editable example only after explicit action', async () => {
    renderExplorer();

    expect(await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Which part of your finances do you want to see?' })).toBeTruthy();
    expect(document.querySelector('[data-cy="analyticsDatasetCatalog"]')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());
    expect(executeSemanticQuery).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Run query' }));
    await waitFor(() =>
      expect(executeSemanticQuery).toHaveBeenCalledWith(expect.objectContaining({ dataset: 'TRANSACTION_LEDGER' }), expect.any(Object)),
    );
    expect(screen.getAllByText('3').length).toBeGreaterThan(0);
  });

  it('keeps all governed sources in the compact information selector', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: 'Create from scratch' }));
    const source = await screen.findByLabelText('Information');
    expect(screen.getByRole('option', { name: 'Financial transactions' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Historical balances and positions' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Credit' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Internal transfers' })).toBeTruthy();

    fireEvent.change(source, { target: { value: 'CREDIT_ACCOUNT_SNAPSHOT' } });
    await screen.findByDisplayValue('deuda_tarjeta');
    expect(screen.getByLabelText('Group by')).toBeTruthy();
    expect(screen.getByLabelText('Split by (optional)')).toBeTruthy();
  });

  it('keeps ordered canonical groupings and reconciles only obsolete grouping order fields', () => {
    const categoryThenMonth = replaceGrouping(
      {
        ...definition,
        groupBy: ['CATEGORY_ID', 'ACCOUNT_ID'],
        orderBy: [
          { field: 'CATEGORY_ID', projectionAlias: null, direction: 'ASC' },
          { field: 'ACCOUNT_ID', projectionAlias: null, direction: 'DESC' },
          { field: null, projectionAlias: 'transactions', direction: 'DESC' },
        ],
      },
      1,
      'TRANSACTION_MONTH',
    );
    expect(categoryThenMonth.groupBy).toEqual(['CATEGORY_ID', 'TRANSACTION_MONTH']);
    expect(categoryThenMonth.orderBy).toEqual([
      { field: 'CATEGORY_ID', projectionAlias: null, direction: 'ASC' },
      { field: 'TRANSACTION_MONTH', projectionAlias: null, direction: 'DESC' },
      { field: null, projectionAlias: 'transactions', direction: 'DESC' },
    ]);

    const monthThenCategory = replaceGrouping(categoryThenMonth, 0, 'TRANSACTION_MONTH');
    expect(monthThenCategory).toBe(categoryThenMonth);

    const accountThenCategory = replaceGrouping(
      {
        ...definition,
        groupBy: ['ACCOUNT_ID', 'CATEGORY_ID'],
        orderBy: [{ field: 'ACCOUNT_ID', projectionAlias: null, direction: 'ASC' }],
      },
      1,
      'CATEGORY_ROOT',
    );
    expect(accountThenCategory.groupBy).toEqual(['ACCOUNT_ID', 'CATEGORY_ROOT']);
    expect(accountThenCategory.orderBy).toEqual([{ field: 'ACCOUNT_ID', projectionAlias: null, direction: 'ASC' }]);

    const children = groupCategoryChildren(
      {
        ...definition,
        groupBy: ['CATEGORY_ROOT', 'TRANSACTION_MONTH'],
        orderBy: [{ field: 'CATEGORY_ROOT', projectionAlias: null, direction: 'ASC' }],
      },
      0,
      '14',
    );
    expect(children.groupBy).toEqual(['CATEGORY_BRANCH_CHILD', 'TRANSACTION_MONTH']);
    expect(children.orderBy).toEqual([{ field: 'CATEGORY_BRANCH_CHILD', projectionAlias: null, direction: 'ASC' }]);
    expect(children.predicates).toContainEqual({ field: 'CATEGORY_ID', operator: 'DESCENDANT_OF', values: ['14'] });
  });

  it('authors a secondary time grouping and category-child parameter in the same visual AST', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsPrimaryBreakdown"]')).toBeTruthy());

    fireEvent.change(document.querySelector('[data-cy="analyticsComparisonBreakdown"]'), { target: { value: 'TIME' } });
    const interval = await screen.findByLabelText('Interval');
    fireEvent.change(interval, { target: { value: 'TRANSACTION_MONTH' } });
    fireEvent.click(screen.getByRole('button', { name: 'Written query' }));
    await waitFor(() => expect(formatSemanticQuery).toHaveBeenCalled());
    expect((formatSemanticQuery.mock.calls.at(-1)?.[0] as SemanticQueryDefinition).groupBy).toEqual(['CATEGORY_ID', 'TRANSACTION_MONTH']);

    fireEvent.click(screen.getByRole('button', { name: 'Visual' }));
    fireEvent.change(document.querySelector('[data-cy="analyticsPrimaryBreakdown"]'), { target: { value: 'CATEGORY_BRANCH_CHILD' } });
    expect(await screen.findByText('Choose a category to complete this grouping.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Food' }));
    fireEvent.click(screen.getByRole('button', { name: 'Written query' }));
    await waitFor(() => expect(formatSemanticQuery).toHaveBeenCalled());
    const hierarchy = formatSemanticQuery.mock.calls.at(-1)?.[0] as SemanticQueryDefinition;
    expect(hierarchy.groupBy).toEqual(['CATEGORY_BRANCH_CHILD', 'TRANSACTION_MONTH']);
    expect(hierarchy.predicates).toContainEqual({ field: 'CATEGORY_ID', operator: 'DESCENDANT_OF', values: ['14'] });
  });

  it('saves a category-first temporal line without reordering the canonical breakdowns', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsPrimaryBreakdown"]')).toBeTruthy());

    fireEvent.change(document.querySelector('[data-cy="analyticsPrimaryBreakdown"]'), { target: { value: 'CATEGORY_ROOT' } });
    fireEvent.change(document.querySelector('[data-cy="analyticsComparisonBreakdown"]'), { target: { value: 'TIME' } });
    fireEvent.change(await screen.findByLabelText('Interval'), { target: { value: 'TRANSACTION_MONTH' } });
    fireEvent.click(screen.getByRole('button', { name: 'Line' }));
    fireEvent.click(document.querySelector('[data-cy="analyticsSaveVisualization"]'));
    await screen.findByRole('heading', { name: 'Save visualization' });
    fireEvent.click(document.querySelector('[data-cy="widgetSaveSubmit"]'));

    await waitFor(() => expect(addPersonalDashboardWidget).toHaveBeenCalled());
    const [, payload] = addPersonalDashboardWidget.mock.calls.at(-1);
    expect(payload).toEqual(
      expect.objectContaining({
        visualizationType: 'LINE',
        definition: expect.objectContaining({ groupBy: ['CATEGORY_ROOT', 'TRANSACTION_MONTH'] }),
      }),
    );
  });

  it('uses financial shortcuts for economic calculations and preserves their canonical AST through written mode', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());

    expect(screen.getByRole('button', { name: 'Total spent' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Number of expenses' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Average expense' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Number of expenses' }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-numero_gastos"]')).toBeTruthy());

    fireEvent.click(screen.getByRole('button', { name: '+ Add calculation' }));
    const averageExpense = screen.getAllByRole('button', { name: 'Average expense' });
    fireEvent.click(averageExpense[averageExpense.length - 1]);
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gasto_promedio"]')).toBeTruthy());
    expect(document.querySelector('[data-cy="analyticsDatasetCatalog"]')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Written query' }));
    await waitFor(() => expect(formatSemanticQuery).toHaveBeenCalled());
    const formatted = formatSemanticQuery.mock.calls.at(-1)?.[0] as SemanticQueryDefinition;
    expect(formatted.projections).toEqual([
      { alias: 'numero_gastos', function: 'COUNT', field: 'TRANSACTION_ID', conditions: [] },
      { alias: 'gasto_promedio', function: 'AVG', field: 'TRANSACTION_AMOUNT', conditions: [] },
    ]);
    expect(formatted.predicates).toEqual(
      expect.arrayContaining([
        { field: 'TRANSACTION_FLOW', operator: 'EQUALS', values: ['OUT'] },
        { field: 'TRANSFER_LINK_STATUS', operator: 'EQUALS', values: ['UNLINKED'] },
      ]),
    );
    fireEvent.click(screen.getByRole('button', { name: 'Visual' }));
    expect((await screen.findAllByText('Number of expenses')).length).toBeGreaterThan(1);
    expect(screen.getAllByText('Average expense').length).toBeGreaterThan(0);
  });

  it('keeps the full catalog-backed editor available only when the user asks to customize', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());

    fireEvent.click(screen.getByRole('button', { name: 'Customize calculation…' }));
    expect(screen.getByText('Advanced calculation editor')).toBeTruthy();
    expect(screen.getByDisplayValue('Amount')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '+ Add calculation' }));
    expect(screen.getByRole('button', { name: 'Average · Amount' })).toBeTruthy();
  });

  it('adds an owner-scoped category condition and keeps it in the visual AST draft', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());

    expect(screen.getByRole('button', { name: '2 active' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: '2 active' }));
    fireEvent.click(screen.getByRole('button', { name: '+ Add condition' }));
    fireEvent.change(screen.getByPlaceholderText('Search field'), { target: { value: 'Assigned category' } });
    fireEvent.change(screen.getByLabelText('Available field'), { target: { value: 'CATEGORY_ID' } });
    expect(await screen.findByText('Food')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Food/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(screen.getAllByText(/Assigned category/).length).toBeGreaterThan(0);
  });

  it('preserves an invalid written-query draft instead of silently replacing it', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());
    await waitFor(() => expect(screen.getByRole('button', { name: 'Written query' }).hasAttribute('disabled')).toBe(false));
    fireEvent.click(screen.getByRole('button', { name: 'Written query' }));
    const editor = await screen.findByLabelText('Written query');
    fireEvent.change(editor, { target: { value: 'broken query' } });
    fireEvent.click(screen.getByRole('button', { name: 'Validate and apply' }));
    expect(await screen.findByText('Invalid query')).toBeTruthy();
    expect((editor as HTMLTextAreaElement).value).toBe('broken query');
  });

  it('saves a visual definition to an existing dashboard without persisting preview context', async () => {
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());

    fireEvent.click(document.querySelector('[data-cy="analyticsSaveVisualization"]'));
    await screen.findByRole('heading', { name: 'Save visualization' });
    fireEvent.change(screen.getByLabelText('Visualization title'), { target: { value: 'Expenses by category' } });
    fireEvent.click(document.querySelector('[data-cy="widgetSaveSubmit"]'));

    await waitFor(() => expect(addPersonalDashboardWidget).toHaveBeenCalled());
    const [dashboardId, payload] = addPersonalDashboardWidget.mock.calls[0];
    expect(dashboardId).toBe(41);
    expect(payload).toEqual(
      expect.objectContaining({
        expectedVersion: 7,
        title: 'Expenses by category',
        definition: expect.objectContaining({ dataset: 'TRANSACTION_LEDGER' }),
      }),
    );
    expect(payload.definition).not.toHaveProperty('context');
    expect(payload).not.toHaveProperty('context');
  });

  it('creates a destination dashboard once and then saves the pending visualization', async () => {
    const created = { ...personalDashboard, id: 42, name: 'New dashboard', version: 1, widgets: [] };
    createPersonalDashboard.mockResolvedValue(created);
    addPersonalDashboardWidget.mockResolvedValue({ ...created, widgets: [personalDashboard.widgets[0]], version: 2 });
    renderExplorer();
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());
    fireEvent.click(document.querySelector('[data-cy="analyticsSaveVisualization"]'));
    await screen.findByRole('heading', { name: 'Save visualization' });
    fireEvent.click(screen.getByRole('button', { name: 'Create new dashboard' }));
    fireEvent.change(screen.getByLabelText('Dashboard name'), { target: { value: 'New dashboard' } });
    fireEvent.click(document.querySelector('[data-cy="widgetSaveSubmit"]'));

    await waitFor(() => expect(createPersonalDashboard).toHaveBeenCalled());
    expect(createPersonalDashboard.mock.calls[0][0]).toEqual(
      expect.objectContaining({ name: 'New dashboard', defaultContext: expect.any(Object) }),
    );
    await waitFor(() => expect(addPersonalDashboardWidget).toHaveBeenCalledWith(42, expect.objectContaining({ expectedVersion: 1 })));
  });

  it('loads, updates, and cancels the same saved widget without replacing its layout identity', async () => {
    const first = renderExplorer('/analytics/explorer?dashboardId=41&widgetId=91');
    await waitFor(() => expect(getPersonalDashboard).toHaveBeenCalledWith(41));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-transactions"]')).toBeTruthy());
    fireEvent.click(document.querySelector('[data-cy="analyticsSaveVisualization"]'));
    await screen.findByRole('heading', { name: 'Save visualization changes' });
    fireEvent.change(screen.getByLabelText('Visualization title'), { target: { value: 'Updated count' } });
    fireEvent.click(document.querySelector('[data-cy="widgetSaveSubmit"]'));

    await waitFor(() => expect(updatePersonalDashboardWidget).toHaveBeenCalled());
    expect(updatePersonalDashboardWidget).toHaveBeenCalledWith(
      41,
      91,
      expect.objectContaining({ expectedVersion: 7, title: 'Updated count', width: 'FULL', visible: false }),
    );

    first.unmount();
    renderExplorer('/analytics/explorer?dashboardId=41&widgetId=91');
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-transactions"]')).toBeTruthy());
    fireEvent.change(screen.getByLabelText('Result name'), { target: { value: 'changed_count' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel editing' }));
    expect(window.confirm).toHaveBeenCalledWith('You have unsaved changes. Cancel editing and discard them?');
  });

  it('keeps tag overlap, currency partitions, availability, and hierarchy drilling explicit in results', () => {
    const onDrillDown = jest.fn();
    render(
      <AnalyticsResultsPanel
        definition={{
          ...definition,
          projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          groupBy: ['TAG_ID'],
        }}
        result={{
          schemaVersion: 1,
          semanticModel: 'FINTRACK_FINANCIAL',
          effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: [] },
          executedAt: '2026-01-31T12:00:00Z',
          availability: 'PARTIAL',
          columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
          warnings: [{ code: 'TAG_GROUPS_OVERLAP', message: 'Tag groups overlap.' }],
          rows: [
            {
              dimensions: [
                {
                  field: 'TAG_ID',
                  value: '27',
                  metadata: { stableId: '27', label: 'Office', color: '#44aa77', active: true, synthetic: false, path: ['Office'] },
                },
              ],
              currencyCode: 'MXN',
              values: [{ alias: 'gastos', value: 100, unit: 'MONEY', availability: 'COMPLETE' }],
            },
            {
              dimensions: [
                {
                  field: 'CATEGORY_ID',
                  value: '14',
                  metadata: { stableId: '14', label: 'Food', color: '#4477aa', active: true, synthetic: false, path: ['Food'] },
                },
              ],
              currencyCode: 'USD',
              values: [{ alias: 'gastos', value: null, unit: 'MONEY', availability: 'UNAVAILABLE' }],
            },
          ],
        }}
        visualization="TABLE"
        locale="en"
        loading={false}
        stale={false}
        seriesLimit={5}
        drillTrail={[]}
        onDrillDown={onDrillDown}
        onDrillBack={jest.fn()}
      />,
    );

    expect(screen.getByText('Tags can overlap, so their totals are not exclusive parts of one total.')).toBeTruthy();
    expect(screen.getByText(/shown by currency and unit/i)).toBeTruthy();
    const presentation = visualizationEligibility(
      {
        ...definition,
        projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
        groupBy: ['TAG_ID'],
      },
      {
        schemaVersion: 1,
        semanticModel: 'FINTRACK_FINANCIAL',
        effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: [] },
        executedAt: '2026-01-31T12:00:00Z',
        columns: [],
        rows: [],
      },
      'en',
    );
    expect(presentation.find(option => option.option === 'DONUT')?.enabled).toBe(false);
    expect(presentation.find(option => option.option === 'STACKED_BAR')?.enabled).toBe(false);
    expect(screen.getAllByText('Unavailable').length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole('button', { name: 'View subcategories' }));
    expect(onDrillDown).toHaveBeenCalledWith('14', 'Food');
  });

  it('keeps weekly monetary chart points, labels, and ticks faithful to the table', () => {
    const rows = [
      {
        dimensions: [{ field: 'TRANSACTION_WEEK' as const, value: 'ISO 2026-W06', metadata: null }],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: null, unit: 'MONEY' as const, availability: 'NO_DATA' as const }],
      },
      {
        dimensions: [{ field: 'TRANSACTION_WEEK' as const, value: 'ISO 2026-W05', metadata: null }],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 31275.13, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
      {
        dimensions: [{ field: 'TRANSACTION_WEEK' as const, value: 'ISO 2026-W07', metadata: null }],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 0, unit: 'MONEY' as const, availability: 'ZERO' as const, derived: true }],
      },
    ];
    const panel: ResultPanel = {
      currency: 'MXN',
      unit: 'MONEY',
      rows,
      values: rows.map(row => ({ row, value: row.values[0] })),
    };
    const chart = chartRows(panel, 'ALL', 'es');

    expect(formatMoney(31275.13, 'MXN', 'es')).toBe('$31,275.13 MXN');
    expect(chart.data.map(row => row.xId)).toEqual(['TRANSACTION_WEEK:2026-W05', 'TRANSACTION_WEEK:2026-W06', 'TRANSACTION_WEEK:2026-W07']);
    expect(chart.data[0].x).toMatch(/26.*ene.*1.*feb.*2026/i);
    expect(chart.data[0]['gastos::']).toBe(31275.13);
    expect(chart.data[1]['gastos::']).toBeNull();
    expect(chart.data[2]['gastos::']).toBe(0);
    expect(chartAxisScale([31275.13, null, 0])).toEqual({ domain: [0, 40000], ticks: [0, 10000, 20000, 30000, 40000] });

    render(
      <AnalyticsResultsPanel
        definition={{
          ...definition,
          projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          groupBy: ['TRANSACTION_WEEK'],
        }}
        result={{
          schemaVersion: 1,
          semanticModel: 'FINTRACK_FINANCIAL',
          effectiveContext: { from: '2026-01-26', through: '2026-02-15', accountIds: [], currencyCodes: ['MXN'] },
          executedAt: '2026-02-15T12:00:00Z',
          columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
          rows,
        }}
        visualization="LINE"
        locale="es"
        loading={false}
        stale={false}
        seriesLimit="ALL"
        drillTrail={[]}
        onDrillDown={jest.fn()}
        onDrillBack={jest.fn()}
      />,
    );

    expect(screen.getByText('Importes en MXN')).toBeTruthy();
    expect(screen.getByText('$31,275.13 MXN')).toBeTruthy();
    expect(screen.getByText('Sin movimientos registrados.')).toBeTruthy();
    expect(document.querySelector('[data-cy="analyticsTemporalChartNote"]')).toBeTruthy();
    expect(screen.queryByText('ISO 2026-W05')).toBeNull();
    expect(screen.queryByText(/MONEY/)).toBeNull();
  });

  it('inserts null weekly gaps through the complete context without turning them into zeroes', () => {
    const rows = [
      {
        dimensions: [{ field: 'TRANSACTION_WEEK' as const, value: '2025-W49', metadata: null }],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 2001.26, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
      {
        dimensions: [{ field: 'TRANSACTION_WEEK' as const, value: '2025-W51', metadata: null }],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 0, unit: 'MONEY' as const, availability: 'ZERO' as const }],
      },
    ];
    const panel: ResultPanel = {
      currency: 'MXN',
      unit: 'MONEY',
      rows,
      values: rows.map(row => ({ row, value: row.values[0] })),
    };

    const chart = chartRows(panel, 'ALL', 'es', {
      from: '2025-12-01',
      through: '2026-02-28',
      accountIds: [],
      currencyCodes: ['MXN'],
    });

    expect(chart.data.map(row => row.xId)).toEqual([
      'TRANSACTION_WEEK:2025-W49',
      'TRANSACTION_WEEK:2025-W50',
      'TRANSACTION_WEEK:2025-W51',
      'TRANSACTION_WEEK:2025-W52',
      'TRANSACTION_WEEK:2026-W01',
      'TRANSACTION_WEEK:2026-W02',
      'TRANSACTION_WEEK:2026-W03',
      'TRANSACTION_WEEK:2026-W04',
      'TRANSACTION_WEEK:2026-W05',
      'TRANSACTION_WEEK:2026-W06',
      'TRANSACTION_WEEK:2026-W07',
      'TRANSACTION_WEEK:2026-W08',
      'TRANSACTION_WEEK:2026-W09',
    ]);
    expect(chart.data[0]['gastos::']).toBe(2001.26);
    expect(chart.data[1].x).toMatch(/8.*dic.*14.*dic.*2025/i);
    expect(chart.data[1]['gastos::']).toBeNull();
    expect(chart.data[2]['gastos::']).toBe(0);
    expect(chart.data.at(-1)?.['gastos::']).toBeNull();
    expect(chart.fillsContext).toBe(true);
    expect(chart.missingPeriods).toBe(11);
  });

  it('keeps the primary category axis when the secondary grouping is temporal', () => {
    const rows = [
      {
        dimensions: [
          {
            field: 'CATEGORY_ROOT' as const,
            value: 'food',
            metadata: { stableId: 'food', label: 'Food', color: '#4477aa', active: true, synthetic: false, path: ['Food'] },
          },
          { field: 'TRANSACTION_MONTH' as const, value: '2026-01', metadata: null },
        ],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 100, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
      {
        dimensions: [
          {
            field: 'CATEGORY_ROOT' as const,
            value: 'travel',
            metadata: { stableId: 'travel', label: 'Travel', color: '#cc7733', active: true, synthetic: false, path: ['Travel'] },
          },
          { field: 'TRANSACTION_MONTH' as const, value: '2026-01', metadata: null },
        ],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 40, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
      {
        dimensions: [
          {
            field: 'CATEGORY_ROOT' as const,
            value: 'food',
            metadata: { stableId: 'food', label: 'Food', color: '#4477aa', active: true, synthetic: false, path: ['Food'] },
          },
          { field: 'TRANSACTION_MONTH' as const, value: '2026-02', metadata: null },
        ],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 50, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
      {
        dimensions: [
          {
            field: 'CATEGORY_ROOT' as const,
            value: 'travel',
            metadata: { stableId: 'travel', label: 'Travel', color: '#cc7733', active: true, synthetic: false, path: ['Travel'] },
          },
          { field: 'TRANSACTION_MONTH' as const, value: '2026-02', metadata: null },
        ],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 75, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
    ];
    const panel: ResultPanel = { currency: 'MXN', unit: 'MONEY', rows, values: rows.map(row => ({ row, value: row.values[0] })) };
    const line = chartRows(panel, 'ALL', 'en', undefined, 'LINE');
    const verticalBars = chartRows(panel, 'ALL', 'en', undefined, 'BAR');
    const bars = chartRows(panel, 'ALL', 'en', undefined, 'GROUPED_BAR');
    const stackedBars = chartRows(panel, 'ALL', 'en', undefined, 'STACKED_BAR');
    const monthThenCategoryRows = rows.map(row => ({ ...row, dimensions: [row.dimensions[1], row.dimensions[0]] }));
    const monthThenCategoryPanel: ResultPanel = {
      currency: 'MXN',
      unit: 'MONEY',
      rows: monthThenCategoryRows,
      values: monthThenCategoryRows.map(row => ({ row, value: row.values[0] })),
    };
    const monthThenCategoryLine = chartRows(monthThenCategoryPanel, 'ALL', 'en', undefined, 'LINE');

    expect(line.data.map(point => point.x)).toEqual(['Food', 'Travel']);
    expect(line.series.map(series => series.label)).toEqual(['January 2026', 'February 2026']);
    expect(line.series.map(series => series.semanticId)).toEqual(['gastos:TRANSACTION_MONTH:2026-01', 'gastos:TRANSACTION_MONTH:2026-02']);
    expect(line.series.map(series => series.color)).not.toEqual(expect.arrayContaining(['#4477aa', '#cc7733']));
    expect(line.series[0].color).not.toBe(line.series[1].color);
    expect(line.data[0]['gastos::TRANSACTION_MONTH:2026-01']).toBe(100);
    expect(line.data[1]['gastos::TRANSACTION_MONTH:2026-02']).toBe(75);
    expect(line.temporal).toBe(false);
    expect(line.temporalSeries).toBe(true);
    expect(verticalBars.data.map(point => point.x)).toEqual(['Food', 'Travel']);
    expect(bars.data.map(point => point.x)).toEqual(['Food', 'Travel']);
    expect(stackedBars.data.map(point => point.x)).toEqual(['Food', 'Travel']);
    expect(monthThenCategoryLine.data.map(point => point.x)).toEqual(['January 2026', 'February 2026']);
    expect(monthThenCategoryLine.series.map(series => series.label)).toEqual(['Expenses · Food', 'Expenses · Travel']);
    expect(monthThenCategoryLine.data[0]['gastos::food']).toBe(100);
    expect(monthThenCategoryLine.data[1]['gastos::travel']).toBe(75);
    expect(
      visualizationEligibility({ ...definition, groupBy: ['CATEGORY_ROOT', 'TRANSACTION_MONTH'] }, null, 'en').find(
        item => item.option === 'LINE',
      )?.enabled,
    ).toBe(true);
    expect(
      visualizationEligibility({ ...definition, groupBy: ['TRANSACTION_MONTH', 'CATEGORY_ROOT'] }, null, 'en').find(
        item => item.option === 'LINE',
      )?.enabled,
    ).toBe(true);

    render(
      <AnalyticsResultsPanel
        definition={{
          ...definition,
          projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          groupBy: ['CATEGORY_ROOT', 'TRANSACTION_MONTH'],
        }}
        result={{
          schemaVersion: 1,
          semanticModel: 'FINTRACK_FINANCIAL',
          effectiveContext: { from: '2026-01-01', through: '2026-02-28', accountIds: [], currencyCodes: ['MXN'] },
          executedAt: '2026-02-28T12:00:00Z',
          columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
          rows,
        }}
        visualization="LINE"
        locale="en"
        loading={false}
        stale={false}
        seriesLimit="ALL"
        drillTrail={[]}
        onDrillDown={jest.fn()}
        onDrillBack={jest.fn()}
      />,
    );
    expect(screen.getByText(/Each line represents a period/)).toBeTruthy();
  });

  it('keeps category colors and the complete result table when the interactive chart legend hides a visual series', () => {
    const rows = [
      {
        dimensions: [
          { field: 'TRANSACTION_MONTH' as const, value: '2026-01', metadata: null },
          {
            field: 'CATEGORY_ROOT' as const,
            value: 'food',
            metadata: { stableId: 'food', label: 'Food', color: '#4477aa', active: true, synthetic: false, path: ['Food'] },
          },
        ],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 100, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
      {
        dimensions: [
          { field: 'TRANSACTION_MONTH' as const, value: '2026-01', metadata: null },
          {
            field: 'CATEGORY_ROOT' as const,
            value: 'travel',
            metadata: { stableId: 'travel', label: 'Travel', color: '#cc7733', active: true, synthetic: false, path: ['Travel'] },
          },
        ],
        currencyCode: 'MXN',
        values: [{ alias: 'gastos', value: 40, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
      },
    ];
    const panel: ResultPanel = { currency: 'MXN', unit: 'MONEY', rows, values: rows.map(row => ({ row, value: row.values[0] })) };
    const chart = chartRows(panel, 'ALL', 'en');

    expect(chart.data).toHaveLength(1);
    expect(chart.data[0].x).toMatch(/january 2026/i);
    expect(chart.temporal).toBe(true);
    expect(chart.temporalSeries).toBe(false);
    expect(chart.series).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ semanticId: 'gastos:food', color: '#4477aa' }),
        expect.objectContaining({ semanticId: 'gastos:travel', color: '#cc7733' }),
      ]),
    );

    render(
      <AnalyticsResultsPanel
        definition={{
          ...definition,
          projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          groupBy: ['TRANSACTION_MONTH', 'CATEGORY_ROOT'],
        }}
        result={{
          schemaVersion: 1,
          semanticModel: 'FINTRACK_FINANCIAL',
          effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: ['MXN'] },
          executedAt: '2026-01-31T12:00:00Z',
          columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
          rows,
        }}
        visualization="LINE"
        locale="en"
        loading={false}
        stale={false}
        seriesLimit="ALL"
        drillTrail={[]}
        onDrillDown={jest.fn()}
        onDrillBack={jest.fn()}
      />,
    );

    const foodToggle = document.querySelector<HTMLButtonElement>('[data-cy="analyticsLegendToggle-gastos:food"]');
    if (!foodToggle) throw new Error('Food legend toggle was not rendered.');
    fireEvent.click(foodToggle);
    expect(foodToggle.getAttribute('aria-pressed')).toBe('false');
    expect(screen.getByText(/Root category: Food/)).toBeTruthy();
    expect(screen.getByText(/Root category: Travel/)).toBeTruthy();
  });

  it('keeps dashboard rows and financial notices available behind an accessible compact details control', () => {
    render(
      <AnalyticsResultsPanel
        definition={{
          ...definition,
          dataset: 'FINANCIAL_POSITION_SNAPSHOT',
          timeBinding: 'OBSERVATION_DATE',
          projections: [{ alias: 'posicion_financiera', function: 'SUM', field: 'FINANCIAL_POSITION', conditions: [] }],
        }}
        result={{
          schemaVersion: 1,
          semanticModel: 'FINTRACK_FINANCIAL',
          effectiveContext: { from: '2026-01-01', through: '2026-03-31', accountIds: [], currencyCodes: ['MXN'] },
          executedAt: '2026-03-31T12:00:00Z',
          availability: 'PARTIAL',
          columns: [{ id: 'posicion_financiera', dimension: false, unit: 'MONEY', currencyFaceted: true }],
          warnings: [{ code: 'FINANCIAL_POSITION_AT_CUTOFF', message: 'One cutoff only.' }],
          rows: [
            {
              dimensions: [],
              currencyCode: 'MXN',
              values: [{ alias: 'posicion_financiera', value: 750, unit: 'MONEY', availability: 'PARTIAL' }],
            },
          ],
        }}
        visualization="KPI"
        locale="en"
        loading={false}
        stale={false}
        seriesLimit="ALL"
        drillTrail={[]}
        onDrillDown={jest.fn()}
        onDrillBack={jest.fn()}
        compact
      />,
    );

    expect(screen.getByText('$750.00 MXN')).toBeTruthy();
    expect(screen.getByText(/Position uses one cutoff per account/)).toBeTruthy();
    const details = screen.getByRole('button', { name: 'View data' });
    expect(details.getAttribute('aria-expanded')).toBe('false');
    expect(document.querySelector('[data-cy="analyticsResultTable"]')).toBeNull();
    fireEvent.click(details);
    expect(details.getAttribute('aria-expanded')).toBe('true');
    expect(document.querySelector('[data-cy="analyticsResultTable"]')).not.toBeNull();
    expect(screen.getAllByText('$750.00 MXN')).toHaveLength(2);
  });

  it('keeps all twenty tag series available with stable IDs, fallback colors, search, show/hide, and isolate controls', () => {
    const rows = Array.from({ length: 20 }, (_, index) => ({
      dimensions: [
        { field: 'TRANSACTION_MONTH' as const, value: '2026-01', metadata: null },
        {
          field: 'TAG_ID' as const,
          value: `${index + 1}`,
          metadata: {
            stableId: `tag-${index + 1}`,
            label: index < 2 ? 'Duplicate display name' : `Tag ${index + 1}`,
            color: index === 2 ? '#44aa77' : null,
            active: true,
            synthetic: false,
            path: [],
          },
        },
      ],
      currencyCode: 'MXN',
      values: [{ alias: 'gastos', value: index + 1, unit: 'MONEY' as const, availability: 'COMPLETE' as const }],
    }));
    const panel: ResultPanel = { currency: 'MXN', unit: 'MONEY', rows, values: rows.map(row => ({ row, value: row.values[0] })) };
    const first = chartRows(panel, 'ALL', 'en');
    const second = chartRows(panel, 'ALL', 'en');

    expect(first.series).toHaveLength(20);
    expect(first.hidden).toBe(0);
    expect(first.series.map(series => series.semanticId)).toContain('gastos:tag-1');
    expect(first.series.map(series => series.semanticId)).toContain('gastos:tag-2');
    expect(first.series.find(series => series.semanticId === 'gastos:tag-3')?.color).toBe('#44aa77');
    expect(first.series.map(series => series.color)).toEqual(second.series.map(series => series.color));

    render(
      <AnalyticsResultsPanel
        definition={{
          ...definition,
          projections: [{ alias: 'gastos', function: 'SUM', field: 'TRANSACTION_AMOUNT', conditions: [] }],
          groupBy: ['TRANSACTION_MONTH', 'TAG_ID'],
        }}
        result={{
          schemaVersion: 1,
          semanticModel: 'FINTRACK_FINANCIAL',
          effectiveContext: { from: '2026-01-01', through: '2026-01-31', accountIds: [], currencyCodes: ['MXN'] },
          executedAt: '2026-01-31T12:00:00Z',
          columns: [{ id: 'gastos', dimension: false, unit: 'MONEY', currencyFaceted: true }],
          rows,
          warnings: [{ code: 'NON_ADDITIVE_OVERLAPPING_TAG_GROUPS', message: 'Tag groups overlap.' }],
        }}
        visualization="LINE"
        locale="en"
        loading={false}
        stale={false}
        seriesLimit="ALL"
        drillTrail={[]}
        onDrillDown={jest.fn()}
        onDrillBack={jest.fn()}
      />,
    );

    const search = document.querySelector<HTMLInputElement>('[data-cy="analyticsLegendSearch"]');
    if (!search) throw new Error('Dense chart legend search was not rendered.');
    fireEvent.change(search, { target: { value: 'Tag 20' } });
    expect(document.querySelector('[data-cy="analyticsLegendToggle-gastos:tag-20"]')).not.toBeNull();
    fireEvent.change(search, { target: { value: '' } });
    const hideAll = document.querySelector<HTMLButtonElement>('[data-cy="analyticsLegendHideAll"]');
    const showAll = document.querySelector<HTMLButtonElement>('[data-cy="analyticsLegendShowAll"]');
    const isolateTagTwenty = document.querySelector<HTMLButtonElement>('[aria-label="Show only Expenses · Tag 20"]');
    if (!hideAll || !showAll || !isolateTagTwenty) throw new Error('Dense chart legend controls were not rendered.');
    fireEvent.click(hideAll);
    expect(document.querySelectorAll('[aria-pressed="false"]').length).toBeGreaterThanOrEqual(20);
    fireEvent.click(showAll);
    fireEvent.click(isolateTagTwenty);
    expect(document.querySelectorAll('[aria-pressed="true"]').length).toBe(1);
    expect(document.querySelectorAll('[data-cy="analyticsResultTable"] tbody tr')).toHaveLength(20);
    expect(screen.getByText('Tags can overlap, so their totals are not exclusive parts of one total.')).toBeTruthy();
  });
});
