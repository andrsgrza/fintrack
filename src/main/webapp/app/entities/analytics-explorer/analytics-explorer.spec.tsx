import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import AnalyticsExplorer from './analytics-explorer';
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
    'CATEGORY_ID',
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
    jest.spyOn(window, 'confirm').mockReturnValue(true);
  });

  afterEach(() => jest.restoreAllMocks());

  it('keeps the compact entry experience and executes an editable example only after explicit action', async () => {
    render(<AnalyticsExplorer />);

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
    render(<AnalyticsExplorer />);
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: 'Create from scratch' }));
    const source = await screen.findByLabelText('Information');
    expect(screen.getByRole('option', { name: 'Financial transactions' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Historical balances and positions' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Credit' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Internal transfers' })).toBeTruthy();

    fireEvent.change(source, { target: { value: 'CREDIT_ACCOUNT_SNAPSHOT' } });
    await screen.findByDisplayValue('deuda_tarjeta');
    expect(screen.getByLabelText('How do you want to organize results?')).toBeTruthy();
  });

  it('uses compact calculation rows and lets the user add a compatible calculation', async () => {
    render(<AnalyticsExplorer />);
    await screen.findByRole('heading', { name: 'Choose an example or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Expenses by category/ }));
    await waitFor(() => expect(document.querySelector('[data-cy="analyticsProjection-gastos"]')).toBeTruthy());

    fireEvent.click(screen.getByRole('button', { name: '+ Add calculation' }));
    fireEvent.click(screen.getByRole('button', { name: 'Average · Amount' }));
    expect(document.querySelector('[data-cy="analyticsProjection-promedio"]')).toBeTruthy();
    expect(document.querySelector('[data-cy="analyticsDatasetCatalog"]')).toBeNull();
  });

  it('adds an owner-scoped category condition and keeps it in the visual AST draft', async () => {
    render(<AnalyticsExplorer />);
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
    render(<AnalyticsExplorer />);
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

    expect(screen.getByText('Tag groups overlap.')).toBeTruthy();
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
});
