import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import BudgetOverview from './budget-overview';
import { getBudgetOverview } from './budget-api';
import { browserToday } from './budget-utils';

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('./budget-api', () => ({ getBudgetOverview: jest.fn() }));

const mockGetBudgetOverview = getBudgetOverview as jest.Mock;

const activeZeroBudget = {
  budgetId: 10,
  name: 'Food limit',
  description: 'Economic expenses only',
  type: 'EXPENSE' as const,
  currency: 'MXN',
  operationalStatus: 'ACTIVE' as const,
  hasPeriodAtReferenceDate: true,
  referenceMessage: null,
  evaluation: {
    budgetId: 10,
    budgetName: 'Food limit',
    budgetType: 'EXPENSE' as const,
    currency: 'MXN',
    periodIndex: 0,
    startInclusive: '2026-11-01',
    endExclusive: '2026-12-01',
    targetAmount: 1000,
    operationalStatus: 'ACTIVE' as const,
    periodTiming: 'CURRENT' as const,
    evaluationApplicable: true,
    actualAmount: 0,
    progressPercentage: 0,
    remainingAmount: 1000,
    goalStatus: 'WITHIN_LIMIT',
    evaluationStatus: 'EVALUATED',
    semanticAvailability: 'AVAILABLE',
    failureReason: null,
    message: null,
  },
};

const renderOverview = () =>
  render(
    <MemoryRouter initialEntries={['/budgets?referenceDate=2026-11-15']}>
      <Routes>
        <Route path="/budgets" element={<BudgetOverview />} />
        <Route path="/budgets/:budgetId" element={<BudgetOverview />} />
      </Routes>
    </MemoryRouter>,
  );

describe('BudgetOverview', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetBudgetOverview.mockResolvedValue({ referenceDate: '2026-11-15', timeZone: 'America/Mexico_City', budgets: [activeZeroBudget] });
  });

  it('renders a valid financial zero as zero, independently from the active card state', async () => {
    renderOverview();

    expect(await screen.findByText('Food limit')).toBeTruthy();
    expect(document.querySelector('[data-cy="budgetCard-10"]')?.textContent).toContain('Active');
    expect(screen.getByText('Within limit')).toBeTruthy();
    expect(document.querySelector('[data-cy="budgetActual"]')?.textContent).toMatch(/0\.00/);
  });

  it('sends type and operational-state filters to the owner-scoped overview endpoint', async () => {
    renderOverview();
    await screen.findByText('Food limit');
    fireEvent.change(screen.getByLabelText('Objective'), { target: { value: 'INCOME' } });
    fireEvent.change(screen.getByLabelText('Operational state'), { target: { value: 'PAUSED' } });

    await waitFor(() =>
      expect(mockGetBudgetOverview).toHaveBeenLastCalledWith(
        expect.objectContaining({ referenceDate: '2026-11-15', type: 'INCOME', status: 'PAUSED' }),
      ),
    );
  });

  it('explains the common reference date and restores it to today without changing a budget', async () => {
    renderOverview();
    await screen.findByText('Food limit');
    expect(screen.getByText(/only chooses which period/i)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('View periods corresponding to this day'), { target: { value: '2026-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Today' }));

    await waitFor(() => expect(mockGetBudgetOverview).toHaveBeenLastCalledWith(expect.objectContaining({ referenceDate: browserToday() })));
  });

  it('keeps financial zero, unavailable, exceeded, scheduled and paused periods distinct in the overview', async () => {
    const unavailable = {
      ...activeZeroBudget,
      budgetId: 11,
      name: 'Unavailable history',
      evaluation: {
        ...activeZeroBudget.evaluation,
        budgetId: 11,
        budgetName: 'Unavailable history',
        actualAmount: null,
        progressPercentage: null,
        remainingAmount: null,
        evaluationApplicable: false,
        evaluationStatus: 'SEMANTIC_UNAVAILABLE',
        semanticAvailability: 'UNAVAILABLE',
      },
    };
    const exceeded = {
      ...activeZeroBudget,
      budgetId: 12,
      name: 'Exceeded food',
      evaluation: {
        ...activeZeroBudget.evaluation,
        budgetId: 12,
        budgetName: 'Exceeded food',
        actualAmount: 3600,
        progressPercentage: 120,
        remainingAmount: -600,
        goalStatus: 'EXCEEDED',
      },
    };
    const scheduled = {
      ...activeZeroBudget,
      budgetId: 13,
      name: 'Next month',
      operationalStatus: 'SCHEDULED' as const,
      hasPeriodAtReferenceDate: false,
      evaluation: null,
    };
    const paused = {
      ...activeZeroBudget,
      budgetId: 14,
      name: 'Paused plan',
      operationalStatus: 'PAUSED' as const,
      evaluation: {
        ...activeZeroBudget.evaluation,
        budgetId: 14,
        budgetName: 'Paused plan',
        operationalStatus: 'PAUSED' as const,
        actualAmount: null,
        progressPercentage: null,
        remainingAmount: null,
        evaluationApplicable: false,
        evaluationStatus: 'NOT_EVALUATED',
        semanticAvailability: null,
        failureReason: 'PAUSED_PERIOD',
      },
    };
    mockGetBudgetOverview.mockResolvedValue({
      referenceDate: '2026-11-15',
      timeZone: 'America/Mexico_City',
      budgets: [activeZeroBudget, unavailable, exceeded, scheduled, paused],
    });

    renderOverview();

    expect(await screen.findByText('Unavailable history')).toBeTruthy();
    expect(screen.getByText('Information unavailable')).toBeTruthy();
    expect(document.querySelector('[data-cy="budgetCard-12"]')?.textContent).toContain('120.00%');
    expect(screen.getByText('Limit exceeded')).toBeTruthy();
    expect(screen.getByText('This budget has not started on the reference date.')).toBeTruthy();
    expect(screen.getByText('No financial evaluation')).toBeTruthy();
    expect(document.querySelector('[data-cy="budgetCard-10"] [data-cy="budgetActual"]')?.textContent).toMatch(/0\.00/);
  });
});
