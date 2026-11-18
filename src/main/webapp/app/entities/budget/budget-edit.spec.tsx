import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import BudgetEdit from './budget-edit';
import {
  cancelBudgetRevision,
  getBudgetEvaluationAtDate,
  getBudgetCurrentEvaluation,
  getBudgetEffectiveRevision,
  getBudgetHistory,
  getBudgetOperationalPeriods,
  getBudgetOverview,
  replaceBudgetRevision,
  scheduleBudgetRevision,
  updateBudgetMetadata,
} from './budget-api';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('./budget-api', () => ({
  getBudgetOverview: jest.fn(),
  getBudgetHistory: jest.fn(),
  getBudgetEvaluationAtDate: jest.fn(),
  getBudgetCurrentEvaluation: jest.fn(),
  getBudgetEffectiveRevision: jest.fn(),
  getBudgetOperationalPeriods: jest.fn(),
  updateBudgetMetadata: jest.fn(),
  scheduleBudgetRevision: jest.fn(),
  replaceBudgetRevision: jest.fn(),
  cancelBudgetRevision: jest.fn(),
}));
jest.mock('app/entities/financial-account/financial-account-selectable.service', () => ({
  getSelectableFinancialAccounts: jest.fn(),
}));
jest.mock('app/entities/category/category-selectable.service', () => ({ getSelectableCategories: jest.fn() }));
jest.mock('app/entities/tag/tag-selectable.service', () => ({ getSelectableTags: jest.fn() }));

const mockOverview = getBudgetOverview as jest.Mock;
const mockHistory = getBudgetHistory as jest.Mock;
const mockAtDate = getBudgetEvaluationAtDate as jest.Mock;
const mockCurrent = getBudgetCurrentEvaluation as jest.Mock;
const mockRevision = getBudgetEffectiveRevision as jest.Mock;
const mockPeriods = getBudgetOperationalPeriods as jest.Mock;
const mockUpdateMetadata = updateBudgetMetadata as jest.Mock;
const mockSchedule = scheduleBudgetRevision as jest.Mock;
const mockReplace = replaceBudgetRevision as jest.Mock;
const mockCancel = cancelBudgetRevision as jest.Mock;
const mockAccounts = getSelectableFinancialAccounts as jest.Mock;
const mockCategories = getSelectableCategories as jest.Mock;
const mockTags = getSelectableTags as jest.Mock;

const currentEvaluation = {
  budgetId: 10,
  budgetName: 'Food limit',
  budgetType: 'EXPENSE' as const,
  currency: 'MXN',
  periodIndex: 4,
  startInclusive: '2026-05-01',
  endExclusive: '2026-06-01',
  targetAmount: 500,
  operationalStatus: 'ACTIVE' as const,
  periodTiming: 'CURRENT' as const,
  evaluationApplicable: true,
  actualAmount: 125,
  progressPercentage: 25,
  remainingAmount: 375,
  goalStatus: 'WITHIN_LIMIT',
  evaluationStatus: 'EVALUATED',
  semanticAvailability: 'COMPLETE',
  failureReason: null,
  message: null,
};

const revision = {
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
  accountScopeMode: 'ALL' as const,
  accountIds: [],
  categoryScopeMode: 'SELECTED' as const,
  categoryIds: [51],
  tagScopeMode: 'NONE' as const,
  tagIds: [],
  tagMatchMode: 'ANY' as const,
};

const scheduledRevision = { ...revision, id: 22, number: 2, targetAmount: 900 };

const period = (index: number, startInclusive: string, endExclusive: string) => ({
  index,
  startInclusive,
  endExclusive,
  effectiveRevisionId: 21,
  status: 'ACTIVE' as const,
  evaluationEligible: true,
});

const pendingSchedule = {
  id: 71,
  operation: 'SCHEDULED' as const,
  revisionId: 22,
  targetScheduleId: null,
  effectivePeriodStart: '2026-07-01',
  sequenceNumber: 2,
  recordedAt: '2026-05-05T00:00:00Z',
  active: true,
};

const renderEdit = () =>
  render(
    <MemoryRouter initialEntries={['/budgets/10/edit']}>
      <Routes>
        <Route path="/budgets/:budgetId/edit" element={<BudgetEdit />} />
      </Routes>
    </MemoryRouter>,
  );

const byCy = (value: string) => document.querySelector<HTMLElement>(`[data-cy="${value}"]`);

describe('BudgetEdit', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockOverview.mockResolvedValue({
      referenceDate: '2026-05-10',
      timeZone: 'America/Mexico_City',
      budgets: [
        {
          budgetId: 10,
          name: 'Food limit',
          description: 'Groceries only',
          type: 'EXPENSE',
          currency: 'MXN',
          operationalStatus: 'ACTIVE',
          hasPeriodAtReferenceDate: true,
          referenceMessage: null,
          evaluation: currentEvaluation,
        },
      ],
    });
    mockHistory.mockResolvedValue({
      budgetId: 10,
      version: 4,
      revisions: [revision, scheduledRevision],
      schedules: [],
      lifecycleEvents: [],
    });
    mockCurrent.mockResolvedValue(currentEvaluation);
    mockRevision.mockResolvedValue(revision);
    mockPeriods.mockResolvedValue([
      period(4, '2026-05-01', '2026-06-01'),
      period(5, '2026-06-01', '2026-07-01'),
      period(6, '2026-07-01', '2026-08-01'),
    ]);
    mockAccounts.mockResolvedValue([{ id: 41, name: 'Cash card', currency: 'MXN', accountType: 'DEBIT', active: true }]);
    mockCategories.mockResolvedValue([{ id: 51, name: 'Food', categoryType: 'EXPENSE', active: true }]);
    mockTags.mockResolvedValue([{ id: 61, name: 'Essential', active: true }]);
    mockUpdateMetadata.mockResolvedValue({ budgetId: 10, version: 5 });
    mockSchedule.mockResolvedValue({ budgetId: 10, version: 5, effectivePeriodStart: '2026-05-01' });
    mockReplace.mockResolvedValue({ budgetId: 10, version: 5, effectivePeriodStart: '2026-07-01' });
    mockCancel.mockResolvedValue({ budgetId: 10, version: 5, effectivePeriodStart: '2026-07-01' });
  });

  it('initializes mutable fields from the effective immutable revision and keeps type and currency read-only', async () => {
    renderEdit();

    expect(await screen.findByText('What does not change')).toBeTruthy();
    expect(screen.getByText('Expense limit')).toBeTruthy();
    expect(screen.getByText('MXN')).toBeTruthy();
    expect((byCy('budgetEditName') as HTMLInputElement).value).toBe('Food limit');
    expect((byCy('budgetEditAmount') as HTMLInputElement).value).toBe('500');
    expect(screen.getByText('New revision scope')).toBeTruthy();
    expect(mockRevision).toHaveBeenCalledWith(10, '2026-05-01');
    expect(mockPeriods).toHaveBeenCalledWith(10, expect.objectContaining({ firstIndex: 4, lastIndex: 64 }));
  });

  it('uses the immutable revision endpoint after explicit current-period review', async () => {
    renderEdit();
    await screen.findByText('New revision');

    fireEvent.change(byCy('budgetEditAmount'), { target: { value: '650' } });
    fireEvent.click(screen.getByLabelText('Current period'));
    fireEvent.click(byCy('reviewBudgetRevision'));

    await waitFor(() => expect(byCy('budgetRevisionReview')).toBeTruthy());
    fireEvent.click(byCy('confirmBudgetRevision'));

    await waitFor(() =>
      expect(mockSchedule).toHaveBeenCalledWith(
        10,
        expect.objectContaining({
          expectedVersion: 4,
          applyToCurrentPeriod: true,
          effectivePeriodStart: '2026-05-01',
          revision: expect.objectContaining({
            targetAmount: 650,
            categoryScopeMode: 'SELECTED',
            categoryIds: [51],
          }),
        }),
      ),
    );
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('shows a pending revision and requires confirmation to replace or cancel it', async () => {
    mockHistory.mockResolvedValue({
      budgetId: 10,
      version: 4,
      revisions: [revision, scheduledRevision],
      schedules: [pendingSchedule],
      lifecycleEvents: [],
    });
    renderEdit();
    await screen.findByText('Scheduled future revisions');

    expect(byCy('pendingRevision-71')).toBeTruthy();
    fireEvent.click(byCy('cancelPendingRevision-71'));
    expect(
      await screen.findByText('Cancel this pending revision? History remains and the future period uses the prior configuration.'),
    ).toBeTruthy();
    fireEvent.click(byCy('confirmCancelPendingRevision'));
    await waitFor(() => expect(mockCancel).toHaveBeenCalledWith(10, 71, expect.objectContaining({ expectedVersion: 4 })));
    await screen.findByText('The pending revision was cancelled. History is preserved.');

    mockHistory.mockResolvedValue({
      budgetId: 10,
      version: 4,
      revisions: [revision, scheduledRevision],
      schedules: [pendingSchedule],
      lifecycleEvents: [],
    });
    fireEvent.click(screen.getByLabelText('Future period'));
    await waitFor(() => expect(byCy('budgetEditFuturePeriod')).toBeTruthy());
    fireEvent.click(byCy('reviewBudgetRevision'));
    await waitFor(() => expect(byCy('confirmBudgetRevision')).toBeTruthy());
    fireEvent.click(byCy('confirmBudgetRevision'));
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith(10, 71, expect.objectContaining({ effectivePeriodStart: '2026-07-01' })));
  });

  it('reports an optimistic conflict without losing the edit form', async () => {
    mockSchedule.mockRejectedValue({ response: { status: 409 } });
    renderEdit();
    await screen.findByText('New revision');

    fireEvent.click(screen.getByLabelText('Current period'));
    fireEvent.click(byCy('reviewBudgetRevision'));
    await waitFor(() => expect(byCy('confirmBudgetRevision')).toBeTruthy());
    fireEvent.click(byCy('confirmBudgetRevision'));

    expect(await screen.findByText('This budget changed elsewhere. Reload and review before confirming.')).toBeTruthy();
    expect(byCy('budgetEditAmount')).toBeTruthy();
  });

  it('uses the server-resolved first period for a scheduled budget and does not invent a revision action before it starts', async () => {
    const scheduledEvaluation = {
      ...currentEvaluation,
      periodIndex: 0,
      startInclusive: '2027-01-01',
      endExclusive: '2027-02-01',
      operationalStatus: 'SCHEDULED' as const,
      periodTiming: 'FUTURE' as const,
      evaluationApplicable: false,
      actualAmount: null,
      progressPercentage: null,
      remainingAmount: null,
      evaluationStatus: 'NOT_YET_EVALUATED',
    };
    mockOverview.mockResolvedValue({
      referenceDate: '2026-05-10',
      timeZone: 'America/Mexico_City',
      budgets: [
        {
          budgetId: 10,
          name: 'Food limit',
          description: 'Groceries only',
          type: 'EXPENSE',
          currency: 'MXN',
          operationalStatus: 'SCHEDULED',
          hasPeriodAtReferenceDate: false,
          referenceMessage: 'The Budget is not scheduled on the requested local date.',
          evaluation: null,
        },
      ],
    });
    mockHistory.mockResolvedValue({
      budgetId: 10,
      version: 4,
      revisions: [revision, scheduledRevision],
      schedules: [
        {
          id: 72,
          operation: 'SCHEDULED',
          revisionId: 21,
          targetScheduleId: null,
          effectivePeriodStart: '2027-01-01',
          sequenceNumber: 1,
          recordedAt: '2026-05-05T00:00:00Z',
          active: true,
        },
      ],
      lifecycleEvents: [],
    });
    mockCurrent.mockRejectedValue({ response: { status: 400 } });
    mockAtDate.mockResolvedValue(scheduledEvaluation);
    mockPeriods.mockResolvedValue([
      {
        ...period(0, '2027-01-01', '2027-02-01'),
        status: 'SCHEDULED',
        evaluationEligible: false,
      },
    ]);

    renderEdit();

    expect(await screen.findByText('Scheduled budget')).toBeTruthy();
    expect(byCy('budgetScheduledRevisionState')).toBeTruthy();
    expect(screen.queryByText('New revision')).toBeNull();
    expect(byCy('budgetEditName')).toBeTruthy();
    expect(mockAtDate).toHaveBeenCalledWith(10, '2026-01-01');
  });
});
