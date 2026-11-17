import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import BudgetDetail from './budget-detail';
import { getBudgetEffectiveRevision, getBudgetEvaluationHistory, getBudgetOverview } from './budget-api';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('./budget-api', () => ({
  getBudgetOverview: jest.fn(),
  getBudgetEvaluationHistory: jest.fn(),
  getBudgetEffectiveRevision: jest.fn(),
}));
jest.mock('app/entities/financial-account/financial-account-selectable.service', () => ({
  getSelectableFinancialAccounts: jest.fn(),
}));
jest.mock('app/entities/category/category-selectable.service', () => ({ getSelectableCategories: jest.fn() }));
jest.mock('app/entities/tag/tag-selectable.service', () => ({ getSelectableTags: jest.fn() }));

const mockOverview = getBudgetOverview as jest.Mock;
const mockHistory = getBudgetEvaluationHistory as jest.Mock;
const mockRevision = getBudgetEffectiveRevision as jest.Mock;
const mockAccounts = getSelectableFinancialAccounts as jest.Mock;
const mockCategories = getSelectableCategories as jest.Mock;
const mockTags = getSelectableTags as jest.Mock;

const evaluation = (index: number, overrides = {}) => ({
  budgetId: 10,
  budgetName: 'Historic groceries',
  budgetType: 'EXPENSE' as const,
  currency: 'MXN',
  periodIndex: index,
  startInclusive: `2026-${String(index + 1).padStart(2, '0')}-01`,
  endExclusive: `2026-${String(index + 2).padStart(2, '0')}-01`,
  targetAmount: index === 0 ? 500 : 750,
  operationalStatus: 'ACTIVE' as const,
  periodTiming: index === 0 ? ('HISTORICAL' as const) : ('CURRENT' as const),
  evaluationApplicable: true,
  actualAmount: index === 0 ? 125 : 0,
  progressPercentage: index === 0 ? 25 : 0,
  remainingAmount: index === 0 ? 375 : 750,
  goalStatus: 'FINISHED_WITHIN_LIMIT',
  evaluationStatus: 'EVALUATED',
  semanticAvailability: 'COMPLETE',
  failureReason: null,
  message: null,
  ...overrides,
});

const itemFor = (value = evaluation(0)) => ({
  budgetId: 10,
  name: 'Historic groceries',
  description: 'Only food and household spending',
  type: 'EXPENSE' as const,
  currency: 'MXN',
  operationalStatus: value.operationalStatus,
  hasPeriodAtReferenceDate: true,
  referenceMessage: null,
  evaluation: value,
});

const initialRevision = {
  id: 21,
  number: 1,
  recordedAt: '2026-01-01T00:00:00Z',
  targetAmount: 500,
  intervalUnit: 'MONTH' as const,
  intervalCount: 1,
  alignment: 'ANCHORED' as const,
  anchorDate: '2026-01-01',
  customDayOfMonth: null,
  recurrenceMode: 'RECURRING' as const,
  termination: 'NONE' as const,
  terminationPeriodCount: null,
  recurrenceEndDate: null,
  accountScopeMode: 'SELECTED' as const,
  accountIds: [41],
  categoryScopeMode: 'SELECTED' as const,
  categoryIds: [51],
  tagScopeMode: 'SELECTED' as const,
  tagIds: [61],
  tagMatchMode: 'ALL' as const,
};

const renderDetail = (entry = '/budgets/10?referenceDate=2026-01-15') =>
  render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/budgets/:budgetId" element={<BudgetDetail />} />
      </Routes>
    </MemoryRouter>,
  );

const byCy = (value: string) => document.querySelector<HTMLElement>(`[data-cy="${value}"]`);

describe('BudgetDetail', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockOverview.mockImplementation(({ referenceDate }) => {
      const selected = referenceDate === '2026-02-01' ? evaluation(1) : referenceDate === '2026-11-01' ? evaluation(10) : evaluation(0);
      return Promise.resolve({ referenceDate, timeZone: 'America/Mexico_City', budgets: [itemFor(selected)] });
    });
    mockHistory.mockImplementation((_budgetId, { firstPeriodIndex, size }) => {
      const rows = Array.from({ length: firstPeriodIndex >= 10 ? 1 : size }, (_, offset) => evaluation(firstPeriodIndex + offset));
      return Promise.resolve({ firstPeriodIndex, size, evaluations: rows });
    });
    mockRevision.mockResolvedValue(initialRevision);
    mockAccounts.mockResolvedValue([{ id: 41, name: 'Cash card', accountType: 'DEBIT', currency: 'MXN', active: true }]);
    mockCategories.mockResolvedValue([{ id: 51, name: 'Food', categoryType: 'EXPENSE', active: true }]);
    mockTags.mockResolvedValue([{ id: 61, name: 'Essential', active: true }]);
  });

  it('loads the selected historical period, its effective revision, and readable scope names', async () => {
    renderDetail();

    expect(await screen.findByText('Historic groceries')).toBeTruthy();
    await waitFor(() => expect(byCy('budgetDetailActual')).toBeTruthy());
    expect(byCy('budgetDetailActual').textContent).toMatch(/125\.00/);
    expect(await screen.findByText('Revision 1')).toBeTruthy();
    expect(await screen.findByText('Cash card')).toBeTruthy();
    expect(byCy('budgetScopeSummary').textContent).toContain('Cash card');
    expect(byCy('budgetScopeSummary').textContent).toContain('Food');
    expect(byCy('budgetScopeSummary').textContent).toContain('Essential');
    expect(mockRevision).toHaveBeenCalledWith(10, '2026-01-01');
    expect(mockHistory).toHaveBeenCalledWith(10, expect.objectContaining({ firstPeriodIndex: 0, size: 11 }));
  });

  it('uses backend-produced periods for next navigation and history row navigation', async () => {
    renderDetail();

    await screen.findByText('Historic groceries');
    await waitFor(() => expect(byCy('budgetNextPeriod').hasAttribute('disabled')).toBe(false));
    fireEvent.click(byCy('budgetNextPeriod'));
    await waitFor(() => expect(mockOverview).toHaveBeenCalledWith(expect.objectContaining({ referenceDate: '2026-02-01' })));

    fireEvent.click(byCy('budgetHistoryNext'));
    await waitFor(() => expect(mockHistory).toHaveBeenCalledWith(10, expect.objectContaining({ firstPeriodIndex: 10, size: 11 })));
    await waitFor(() => expect(byCy('openBudgetPeriod-10')).toBeTruthy());
    fireEvent.click(byCy('openBudgetPeriod-10'));
    await waitFor(() => expect(mockOverview).toHaveBeenCalledWith(expect.objectContaining({ referenceDate: '2026-11-01' })));
  });

  it('never displays an unavailable financial result as zero', async () => {
    const unavailable = evaluation(0, {
      evaluationApplicable: false,
      actualAmount: null,
      progressPercentage: null,
      remainingAmount: null,
      evaluationStatus: 'SEMANTIC_UNAVAILABLE',
      semanticAvailability: 'UNAVAILABLE',
    });
    mockOverview.mockResolvedValue({ referenceDate: '2026-01-15', timeZone: 'America/Mexico_City', budgets: [itemFor(unavailable)] });
    mockHistory.mockResolvedValue({ firstPeriodIndex: 0, size: 11, evaluations: [unavailable] });

    renderDetail();

    expect(await screen.findByText('Financial information unavailable')).toBeTruthy();
    expect(byCy('budgetDetailActual').textContent).toBe('—');
    expect(byCy('budgetPeriodHistory').textContent).not.toContain('$0.00');
  });

  it('shows a safe no-period state when the selected date is outside the schedule', async () => {
    mockOverview.mockResolvedValue({
      referenceDate: '2027-01-15',
      timeZone: 'America/Mexico_City',
      budgets: [
        {
          ...itemFor(),
          operationalStatus: 'COMPLETED',
          hasPeriodAtReferenceDate: false,
          referenceMessage: 'This Budget has no scheduled period on the selected reference date.',
          evaluation: null,
        },
      ],
    });

    renderDetail('/budgets/10?referenceDate=2027-01-15');

    await waitFor(() => expect(byCy('budgetNoScheduledPeriod')).toBeTruthy());
    expect(byCy('budgetNoScheduledPeriod').textContent).toContain('no scheduled period');
    expect(mockRevision).not.toHaveBeenCalled();
  });
});
