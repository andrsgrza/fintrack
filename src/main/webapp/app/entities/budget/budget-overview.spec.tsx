import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import BudgetOverview from './budget-overview';
import { getBudgetOverview } from './budget-api';

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
    targetAmount: '1000.00',
    operationalStatus: 'ACTIVE' as const,
    periodTiming: 'CURRENT' as const,
    evaluationApplicable: true,
    actualAmount: '0.00',
    progressPercentage: '0.00',
    remainingAmount: '1000.00',
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

  it('returns to server-authoritative current periods without using the browser date', async () => {
    renderOverview();
    await screen.findByText('Food limit');
    expect(screen.getByText(/each Budget shows its current period/i)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('View periods corresponding to this day'), { target: { value: '2026-01-01' } });
    fireEvent.click(screen.getByRole('button', { name: 'Today' }));

    await waitFor(() => {
      expect(mockGetBudgetOverview).toHaveBeenLastCalledWith(expect.not.objectContaining({ referenceDate: expect.any(String) }));
    });
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
        actualAmount: '3600.00',
        progressPercentage: '120.00',
        remainingAmount: '-600.00',
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

  it.each([
    ['0.00', '0.00%', '0', 'EXPENSE'],
    ['50.00', '50.00%', '50', 'EXPENSE'],
    ['100.00', '100.00%', '100', 'EXPENSE'],
    ['120.00', '120.00%', '100', 'EXPENSE'],
    ['250.00', '250.00%', '100', 'INCOME'],
  ])(
    'keeps %s actual progress visible while exposing a valid ARIA range for %s budgets',
    async (progress, visibleProgress, boundedProgress, type) => {
      const budgetId = 20;
      mockGetBudgetOverview.mockResolvedValue({
        referenceDate: '2026-11-15',
        timeZone: 'America/Mexico_City',
        budgets: [
          {
            ...activeZeroBudget,
            budgetId,
            name: `${type} ${progress}`,
            type,
            evaluation: {
              ...activeZeroBudget.evaluation,
              budgetId,
              budgetName: `${type} ${progress}`,
              budgetType: type,
              progressPercentage: progress,
              actualAmount: type === 'EXPENSE' ? '1250.00' : '2500.00',
              remainingAmount: progress === '0.00' ? '1000.00' : progress === '250.00' ? '1500.00' : '-250.00',
            },
          },
        ],
      });

      renderOverview();
      await screen.findByText(`${type} ${progress}`);
      const progressbar = document.querySelector('[data-cy="budgetProgress"]');
      expect(progressbar.getAttribute('aria-valuemin')).toBe('0');
      expect(progressbar.getAttribute('aria-valuemax')).toBe('100');
      expect(progressbar.getAttribute('aria-valuenow')).toBe(boundedProgress);
      expect(progressbar.getAttribute('aria-valuetext')).toBe(visibleProgress);
      expect(progressbar.textContent).not.toContain(visibleProgress);
      expect(document.querySelector(`[data-cy="budgetCard-${budgetId}"]`)?.textContent).toContain(visibleProgress);
      expect(document.querySelector(`[data-cy="budgetCard-${budgetId}"]`)?.textContent).toContain(
        type === 'EXPENSE' ? 'Spent' : 'Received',
      );
    },
  );
});
