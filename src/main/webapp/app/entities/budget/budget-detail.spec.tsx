import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import BudgetDetail from './budget-detail';
import {
  getBudgetCurrentEvaluation,
  getBudgetEffectiveRevision,
  getBudgetEvaluationHistory,
  getBudgetHistory,
  getBudgetOperationalPeriods,
  getBudgetOverview,
  cancelBudgetPause,
  getBudgetNotificationPreferences,
  previewBudgetScope,
} from './budget-api';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('./budget-api', () => ({
  getBudgetOverview: jest.fn(),
  getBudgetEvaluationHistory: jest.fn(),
  getBudgetEffectiveRevision: jest.fn(),
  getBudgetHistory: jest.fn(),
  getBudgetCurrentEvaluation: jest.fn(),
  getBudgetOperationalPeriods: jest.fn(),
  requestBudgetPause: jest.fn(),
  cancelBudgetPause: jest.fn(),
  requestBudgetResume: jest.fn(),
  requestBudgetFinish: jest.fn(),
  deleteBudget: jest.fn(),
  getBudgetNotificationPreferences: jest.fn(),
  previewBudgetScope: jest.fn(),
}));
jest.mock('app/entities/financial-account/financial-account-selectable.service', () => ({
  getSelectableFinancialAccounts: jest.fn(),
}));
jest.mock('app/entities/category/category-selectable.service', () => ({ getSelectableCategories: jest.fn() }));
jest.mock('app/entities/tag/tag-selectable.service', () => ({ getSelectableTags: jest.fn() }));

const mockOverview = getBudgetOverview as jest.Mock;
const mockHistory = getBudgetEvaluationHistory as jest.Mock;
const mockRevision = getBudgetEffectiveRevision as jest.Mock;
const mockLifecycleHistory = getBudgetHistory as jest.Mock;
const mockCurrentEvaluation = getBudgetCurrentEvaluation as jest.Mock;
const mockOperationalPeriods = getBudgetOperationalPeriods as jest.Mock;
const mockCancelPause = cancelBudgetPause as jest.Mock;
const mockNotificationPreferences = getBudgetNotificationPreferences as jest.Mock;
const mockPreviewBudgetScope = previewBudgetScope as jest.Mock;
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
  targetAmount: index === 0 ? '500.00' : '750.00',
  operationalStatus: 'ACTIVE' as const,
  periodTiming: index === 0 ? ('HISTORICAL' as const) : ('CURRENT' as const),
  evaluationApplicable: true,
  actualAmount: index === 0 ? '125.00' : '0.00',
  progressPercentage: index === 0 ? '25.00' : '0.00',
  remainingAmount: index === 0 ? '375.00' : '750.00',
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
  targetAmount: '500.00',
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
    mockLifecycleHistory.mockResolvedValue({ budgetId: 10, version: 4, revisions: [initialRevision], schedules: [], lifecycleEvents: [] });
    mockCurrentEvaluation.mockResolvedValue(evaluation(2, { periodTiming: 'CURRENT' }));
    mockOperationalPeriods.mockResolvedValue([
      {
        index: 3,
        startInclusive: '2026-04-01',
        endExclusive: '2026-05-01',
        effectiveRevisionId: 21,
        status: 'ACTIVE',
        evaluationEligible: true,
      },
    ]);
    mockCancelPause.mockResolvedValue(undefined);
    mockNotificationPreferences.mockResolvedValue({
      budgetId: 10,
      configured: true,
      version: 3,
      percentageAlertsEnabled: false,
      thresholdPercentages: [],
      periodCloseEnabled: false,
      periodCloseEnabledSince: null,
      updatedAt: '2026-01-01T00:00:00Z',
    });
    mockAccounts.mockResolvedValue([{ id: 41, name: 'Cash card', accountType: 'DEBIT', currency: 'MXN', active: true }]);
    mockCategories.mockResolvedValue([{ id: 51, name: 'Food', categoryType: 'EXPENSE', active: true }]);
    mockTags.mockResolvedValue([{ id: 61, name: 'Essential', active: true }]);
    mockPreviewBudgetScope.mockResolvedValue({
      unrestricted: false,
      selectedCategories: [{ id: 51, name: 'Food', color: null, active: true, path: ['Food'], categoryType: 'EXPENSE' }],
      effectiveCategories: [{ id: 51, name: 'Food', color: null, active: true, path: ['Food'], categoryType: 'EXPENSE' }],
      missingCategoryIds: [],
      incompatibleCategoryIds: [],
    });
  });

  it('loads the selected historical period, its effective revision, and readable scope names', async () => {
    renderDetail();

    expect(await screen.findByText('Historic groceries')).toBeTruthy();
    await waitFor(() => expect(byCy('budgetDetailActual')).toBeTruthy());
    expect(byCy('budgetDetailActual').textContent).toMatch(/125\.00/);
    expect(await screen.findByText('Revision 1')).toBeTruthy();
    expect(await screen.findByText(/Cash card/)).toBeTruthy();
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

  it('loads lifecycle state from the actual current period rather than the historical period being viewed', async () => {
    mockCurrentEvaluation.mockResolvedValue(evaluation(2, { operationalStatus: 'PAUSED', periodTiming: 'CURRENT' }));
    renderDetail();

    await screen.findByText('Historic groceries');
    await waitFor(() => expect(mockCurrentEvaluation).toHaveBeenCalledWith(10));
    expect(byCy('budgetDetailIdentity').textContent).toContain('Paused');
    expect(byCy('budgetLifecycleActions')).toBeTruthy();
  });

  it('keeps lifecycle actions mounted while refreshing after cancelling a pending pause', async () => {
    mockLifecycleHistory.mockResolvedValue({
      budgetId: 10,
      version: 4,
      revisions: [initialRevision],
      schedules: [],
      lifecycleEvents: [
        {
          id: 77,
          type: 'PAUSE_REQUESTED',
          effectivePeriodStart: '2026-04-01',
          targetEventId: null,
        },
      ],
    });
    renderDetail();

    await screen.findByText('Historic groceries');
    await waitFor(() => expect(screen.getByText('Cancel pending pause')).toBeTruthy());
    fireEvent.click(byCy('budgetLifecycleActions'));
    fireEvent.click(screen.getByText('Cancel pending pause'));
    fireEvent.click(byCy('confirmBudgetLifecycle'));
    await waitFor(() => expect(mockCancelPause).toHaveBeenCalledWith(10, 77, expect.any(Object)));
    await waitFor(() => expect(byCy('budgetLifecycleNotice')).toBeTruthy());

    fireEvent.click(byCy('budgetLifecycleActions'));
    fireEvent.click(screen.getByText('Delete budget'));
    await waitFor(() => expect(byCy('confirmBudgetLifecycle')).toBeTruthy());
  });

  it('renders current notification settings as a read-only summary and links to the central editor', async () => {
    mockNotificationPreferences.mockResolvedValue({
      budgetId: 10,
      configured: true,
      version: 3,
      percentageAlertsEnabled: true,
      thresholdPercentages: ['50', '80.1250'],
      periodCloseEnabled: true,
      periodCloseEnabledSince: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    });
    renderDetail();

    await waitFor(() => expect(byCy('budgetNotificationPreferencesSummary')).toBeTruthy());
    expect(byCy('budgetNotificationThresholdSummary').textContent).toContain('50%, 80.125%');
    expect(byCy('budgetNotificationPeriodCloseSummary').textContent).toContain('Enabled');
    expect(byCy('configureBudgetNotifications').getAttribute('href')).toBe('/budgets/10/edit#notifications');
    expect(byCy('budgetDetailNotificationThresholdEnabled')).toBeNull();
    expect(byCy('saveBudgetNotificationPreferences')).toBeNull();
    expect(byCy('cancelBudgetNotificationPreferences')).toBeNull();
  });

  it('uses income wording for disabled alerts and does not offer an edit action after completion', async () => {
    mockOverview.mockResolvedValue({
      referenceDate: '2026-01-15',
      timeZone: 'America/Mexico_City',
      budgets: [{ ...itemFor(evaluation(0, { operationalStatus: 'COMPLETED' })), type: 'INCOME' }],
    });
    mockCurrentEvaluation.mockResolvedValue(evaluation(2, { operationalStatus: 'COMPLETED', periodTiming: 'CURRENT' }));
    renderDetail();

    await waitFor(() => expect(byCy('budgetNotificationPreferencesSummary')).toBeTruthy());
    expect(byCy('budgetNotificationPreferencesSummary').textContent).toContain('Income alerts');
    expect(byCy('budgetNotificationThresholdSummary').textContent).toContain('Disabled');
    expect(byCy('configureBudgetNotifications')).toBeNull();
    expect(byCy('editBudget')).toBeNull();
  });
});
