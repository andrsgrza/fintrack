import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import AnalyticsExplorer from './analytics-explorer';
import { SemanticAnalyticsCatalog, SemanticQueryDefinition } from './analytics-explorer.model';

const executeSemanticQuery = jest.fn();
const formatSemanticQuery = jest.fn();
const getExplorerAccounts = jest.fn();
const getSemanticAnalyticsCatalog = jest.fn();
const parseSemanticQuery = jest.fn();

jest.mock('app/config/store', () => ({
  useAppSelector: () => 'en',
}));

jest.mock('./analytics-explorer-api', () => ({
  executeSemanticQuery: (...args: unknown[]) => executeSemanticQuery(...args),
  formatSemanticQuery: (...args: unknown[]) => formatSemanticQuery(...args),
  getExplorerAccounts: () => getExplorerAccounts(),
  getSemanticAnalyticsCatalog: () => getSemanticAnalyticsCatalog(),
  parseSemanticQuery: (...args: unknown[]) => parseSemanticQuery(...args),
}));

const definition: SemanticQueryDefinition = {
  schemaVersion: 1,
  semanticModel: 'FINTRACK_FINANCIAL',
  dataset: 'TRANSACTION_LEDGER',
  timeBinding: 'TRANSACTION_DATE',
  projections: [{ alias: 'transactions', function: 'COUNT', field: 'TRANSACTION_ID' }],
  predicates: [],
  groupBy: [],
  orderBy: [],
  limit: 100,
};

const catalog: SemanticAnalyticsCatalog = {
  schemaVersion: 1,
  semanticModel: 'FINTRACK_FINANCIAL',
  catalogVersion: '1.0',
  supportedRelativePeriods: ['THIS_MONTH'],
  defaultLimit: 100,
  maxLimit: 500,
  authenticatedOwnerRequired: true,
  datasets: [
    {
      dataset: 'TRANSACTION_LEDGER',
      grain: 'one FinancialTransaction',
      timeBinding: 'TRANSACTION_DATE',
      relationships: [{ name: 'ACCOUNT', target: 'FinancialAccount', cardinality: 'MANY_TO_ONE', description: 'Account' }],
      fields: [
        { field: 'TRANSACTION_ID', valueType: 'IDENTIFIER', unit: null, aggregateable: true, filterable: false, groupable: false },
        { field: 'TRANSACTION_DATE', valueType: 'DATE', unit: null, aggregateable: false, filterable: false, groupable: false },
      ],
      aggregateCapabilities: [{ function: 'COUNT', field: 'TRANSACTION_ID', outputUnit: 'COUNT', additivity: 'ADDITIVE' }],
      predicateCapabilities: [],
      groupings: [],
      additivity: 'ADDITIVE',
      currencyPolicy: 'NOT_APPLICABLE',
      temporalSemantics: 'Inclusive transaction date.',
    },
  ],
};

const canonical = 'SELECT COUNT(TRANSACTION_ID) AS transactions FROM TRANSACTION_LEDGER TIME BINDING TRANSACTION_DATE LIMIT 100';

describe('Semantic analytics explorer', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getSemanticAnalyticsCatalog.mockResolvedValue(catalog);
    getExplorerAccounts.mockResolvedValue([]);
    formatSemanticQuery.mockResolvedValue(canonical);
    parseSemanticQuery.mockImplementation((text: string) =>
      text === 'broken query' ? Promise.reject(new Error('Invalid query')) : Promise.resolve({ definition, canonicalText: canonical }),
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
  });

  it('uses an editable semantic example and does not execute until asked', async () => {
    render(<AnalyticsExplorer />);

    expect(await screen.findByRole('heading', { name: 'Start quickly or create a query from scratch' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /Transactions by institution/i }));
    await waitFor(() => expect(formatSemanticQuery).toHaveBeenCalled());
    await screen.findByText('Guided builder');
    expect(executeSemanticQuery).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Run query' }));
    await waitFor(() => expect(executeSemanticQuery).toHaveBeenCalledWith(definition, expect.any(Object)));
    expect(screen.getAllByText('3').length).toBeGreaterThan(0);
  });

  it('keeps an invalid SQL-like draft editable instead of silently replacing it', async () => {
    render(<AnalyticsExplorer />);
    await screen.findByRole('heading', { name: 'Start quickly or create a query from scratch' });
    fireEvent.click(screen.getByRole('button', { name: /Transactions by institution/i }));
    await waitFor(() => expect(formatSemanticQuery).toHaveBeenCalled());
    await screen.findByText('Guided builder');
    fireEvent.click(screen.getByRole('button', { name: 'Advanced SQL-like' }));
    const editor = await screen.findByLabelText('SQL-like');
    fireEvent.change(editor, { target: { value: 'broken query' } });
    fireEvent.click(screen.getByRole('button', { name: 'Validate and apply' }));
    expect(await screen.findByText('Invalid query')).toBeTruthy();
    expect((editor as HTMLTextAreaElement).value).toBe('broken query');
  });
});
