import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

import { BudgetLifecycleActions, BudgetLifecycleStatus, lifecycleProjection } from './budget-lifecycle-actions';
import { cancelBudgetPause, deleteBudget, requestBudgetFinish, requestBudgetPause, requestBudgetResume } from './budget-api';

jest.mock('./budget-api', () => ({
  cancelBudgetPause: jest.fn(),
  deleteBudget: jest.fn(),
  requestBudgetFinish: jest.fn(),
  requestBudgetPause: jest.fn(),
  requestBudgetResume: jest.fn(),
}));

const mockPause = requestBudgetPause as jest.Mock;
const mockCancelPause = cancelBudgetPause as jest.Mock;
const mockResume = requestBudgetResume as jest.Mock;
const mockFinish = requestBudgetFinish as jest.Mock;
const mockDelete = deleteBudget as jest.Mock;

const budget = {
  budgetId: 10,
  name: 'Groceries',
  description: 'Household food',
  type: 'EXPENSE' as const,
  currency: 'MXN',
  operationalStatus: 'ACTIVE' as const,
  hasPeriodAtReferenceDate: true,
  referenceMessage: null,
  evaluation: null,
};

const history = (lifecycleEvents = []) => ({
  budgetId: 10,
  version: 4,
  revisions: [],
  schedules: [],
  lifecycleEvents,
});

const renderActions = (overrides: Partial<React.ComponentProps<typeof BudgetLifecycleActions>> = {}) => {
  const onChanged = jest.fn();
  const onSuccess = jest.fn();
  const onDeleted = jest.fn();
  const view = render(
    <BudgetLifecycleActions
      locale="en"
      budget={budget}
      history={history()}
      currentPeriodStart="2026-03-01"
      nextPeriodStart="2026-04-01"
      nextPeriodAvailable
      timeZone="America/Mexico_City"
      onChanged={onChanged}
      onSuccess={onSuccess}
      onDeleted={onDeleted}
      {...overrides}
    />,
  );
  return { ...view, onChanged, onSuccess, onDeleted };
};

const openActions = () => fireEvent.click(document.querySelector<HTMLElement>('[data-cy="budgetLifecycleActions"]'));
const confirm = () => fireEvent.click(document.querySelector<HTMLElement>('[data-cy="confirmBudgetLifecycle"]'));

describe('BudgetLifecycleActions', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockPause.mockResolvedValue({ budgetId: 10, version: 5, effectivePeriodStart: '2026-04-01' });
    mockCancelPause.mockResolvedValue({ budgetId: 10, version: 5, effectivePeriodStart: '2026-04-01' });
    mockResume.mockResolvedValue({ budgetId: 10, version: 5, effectivePeriodStart: '2026-04-01' });
    mockFinish.mockResolvedValue({ budgetId: 10, version: 5, effectivePeriodStart: '2026-04-01' });
    mockDelete.mockResolvedValue(undefined);
  });

  it('sends the server-authoritative pause command and shows confirmation feedback', async () => {
    const { onChanged } = renderActions();

    openActions();
    fireEvent.click(await screen.findByText('Pause at next period'));
    expect(screen.getByText(/The current period remains active/)).toBeTruthy();
    confirm();

    await waitFor(() =>
      expect(mockPause).toHaveBeenCalledWith(
        10,
        expect.objectContaining({ expectedVersion: 4, timeZone: 'America/Mexico_City', clientRequestId: expect.any(String) }),
      ),
    );
    expect(await screen.findByText('The action was scheduled for the displayed date.')).toBeTruthy();
    expect(onChanged).toHaveBeenCalledTimes(1);
  });

  it('shows a pending pause with its effective date and cancels only that pending event', async () => {
    const pendingPause = {
      id: 31,
      type: 'PAUSE_REQUESTED',
      targetEventId: null,
      effectivePeriodStart: '2026-04-01',
      sequenceNumber: 1,
      recordedAt: '2026-03-15T18:00:00Z',
    };
    render(
      <>
        <BudgetLifecycleStatus locale="en" history={history([pendingPause])} currentPeriodStart="2026-03-01" />
        <BudgetLifecycleActions
          locale="en"
          budget={budget}
          history={history([pendingPause])}
          currentPeriodStart="2026-03-01"
          nextPeriodStart="2026-04-01"
          nextPeriodAvailable
          timeZone="America/Mexico_City"
          onChanged={jest.fn()}
          onSuccess={jest.fn()}
          onDeleted={jest.fn()}
        />
      </>,
    );

    expect(screen.getByText(/Pause pending:/)).toBeTruthy();
    openActions();
    fireEvent.click(await screen.findByText('Cancel pending pause'));
    expect(screen.getByText(/pending pause will be cancelled/)).toBeTruthy();
    confirm();

    await waitFor(() => expect(mockCancelPause).toHaveBeenCalledWith(10, 31, expect.objectContaining({ expectedVersion: 4 })));
  });

  it('only offers resume for an actually paused budget and submits the existing command', async () => {
    renderActions({ budget: { ...budget, operationalStatus: 'PAUSED' } });

    openActions();
    fireEvent.click(await screen.findByText('Resume at next period'));
    expect(screen.getByText(/current paused period does not change/)).toBeTruthy();
    confirm();

    await waitFor(() => expect(mockResume).toHaveBeenCalledWith(10, expect.objectContaining({ expectedVersion: 4 })));
    expect(mockPause).not.toHaveBeenCalled();
  });

  it('requires an explicit irreversible finalization confirmation', async () => {
    renderActions();

    openActions();
    fireEvent.click(await screen.findByText('Finish budget'));
    expect(screen.getByText(/Finalization is permanent/)).toBeTruthy();
    confirm();

    await waitFor(() => expect(mockFinish).toHaveBeenCalledWith(10, expect.objectContaining({ expectedVersion: 4 })));
  });

  it('requires deletion confirmation, handles a concurrent already-deleted result, and redirects only on success', async () => {
    const first = renderActions();

    openActions();
    fireEvent.click(await screen.findByText('Delete budget'));
    expect(screen.getByText(/cannot be recovered/)).toBeTruthy();
    confirm();
    await waitFor(() => expect(mockDelete).toHaveBeenCalledWith(10, 4));
    expect(first.onDeleted).toHaveBeenCalledTimes(1);

    mockDelete.mockRejectedValueOnce({ response: { status: 409 } });
    first.unmount();
    const retry = renderActions();
    openActions();
    fireEvent.click(await screen.findByText('Delete budget'));
    confirm();
    expect(await screen.findByText(/changed elsewhere/)).toBeTruthy();
    expect(retry.onDeleted).not.toHaveBeenCalled();
  });

  it('treats the owner-scoped already-deleted response as a completed deletion', async () => {
    mockDelete.mockRejectedValueOnce({ response: { status: 400, data: { message: 'Budget not found.' } } });
    const { onDeleted } = renderActions();

    openActions();
    fireEvent.click(await screen.findByText('Delete budget'));
    confirm();

    await waitFor(() => expect(onDeleted).toHaveBeenCalledTimes(1));
  });

  it('does not report a cancelled pause as pending', () => {
    const projection = lifecycleProjection(
      history([
        {
          id: 31,
          type: 'PAUSE_REQUESTED',
          targetEventId: null,
          effectivePeriodStart: '2026-04-01',
          sequenceNumber: 1,
          recordedAt: '2026-03-15T18:00:00Z',
        },
        {
          id: 32,
          type: 'PAUSE_CANCELLED',
          targetEventId: 31,
          effectivePeriodStart: '2026-04-01',
          sequenceNumber: 2,
          recordedAt: '2026-03-16T18:00:00Z',
        },
      ]),
      '2026-03-01',
    );

    expect(projection.pendingPause).toBeNull();
  });
});
