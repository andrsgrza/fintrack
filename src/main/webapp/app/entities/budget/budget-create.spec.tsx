import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import BudgetCreate from './budget-create';
import { createBudget, previewBudgetCalendar } from './budget-api';
import { calendarValidationError } from './budget-utils';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';

jest.mock('app/config/store', () => ({ useAppSelector: () => 'en' }));
jest.mock('app/entities/financial-account/financial-account-selectable.service', () => ({
  getSelectableFinancialAccounts: jest.fn(),
}));
jest.mock('app/entities/category/category-selectable.service', () => ({ getSelectableCategories: jest.fn() }));
jest.mock('app/entities/tag/tag-selectable.service', () => ({ getSelectableTags: jest.fn() }));
jest.mock('./budget-api', () => ({
  createBudget: jest.fn(),
  previewBudgetCalendar: jest.fn(),
}));

const mockCreateBudget = createBudget as jest.Mock;
const mockPreviewBudgetCalendar = previewBudgetCalendar as jest.Mock;
const mockGetAccounts = getSelectableFinancialAccounts as jest.Mock;
const mockGetCategories = getSelectableCategories as jest.Mock;
const mockGetTags = getSelectableTags as jest.Mock;

const renderCreate = () =>
  render(
    <MemoryRouter initialEntries={['/budgets/new']}>
      <Routes>
        <Route path="/budgets/new" element={<BudgetCreate />} />
        <Route path="/budgets/:budgetId" element={<div data-cy="createdBudget">Created budget</div>} />
      </Routes>
    </MemoryRouter>,
  );

const fillRequiredFields = async () => {
  fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Monthly essentials' } });
  fireEvent.change(screen.getByLabelText('Period limit'), { target: { value: '1250' } });
  fireEvent.change(screen.getByLabelText('Currency'), { target: { value: 'MXN' } });
  fireEvent.change(screen.getByLabelText('Every'), { target: { value: '1' } });
  fireEvent.change(screen.getByLabelText('Unit'), { target: { value: 'MONTH' } });
  await waitFor(() => expect(mockPreviewBudgetCalendar).toHaveBeenCalled());
};

describe('BudgetCreate', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetAccounts.mockResolvedValue([{ id: 11, name: 'Daily', accountType: 'DEBIT', currency: 'MXN', active: true }]);
    mockGetCategories.mockResolvedValue([{ id: 21, name: 'Essentials', categoryType: 'EXPENSE', active: true }]);
    mockGetTags.mockResolvedValue([{ id: 31, name: 'Home', active: true }]);
    mockPreviewBudgetCalendar.mockResolvedValue({
      localToday: '2026-11-15',
      periods: [{ index: 0, startInclusive: '2026-11-15', endExclusive: '2026-12-15' }],
    });
  });

  it('starts incomplete and creates an economic expense budget only after the Java calendar preview is valid', async () => {
    mockCreateBudget.mockResolvedValue({ id: 44, name: 'Monthly essentials', type: 'EXPENSE', currency: 'MXN' });
    renderCreate();

    expect(screen.getByLabelText('I want to track').value).toBe('EXPENSE');
    expect(screen.getByLabelText('Currency').value).toBe('');
    expect(screen.getByLabelText('Every').value).toBe('');
    expect(screen.getByLabelText('Unit').value).toBe('');

    await fillRequiredFields();
    fireEvent.click(screen.getByRole('button', { name: 'Create budget' }));

    await waitFor(() => expect(mockCreateBudget).toHaveBeenCalledTimes(1));
    expect(mockCreateBudget).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Monthly essentials',
        type: 'EXPENSE',
        currency: 'MXN',
        initialRevision: expect.objectContaining({
          targetAmount: 1250,
          intervalCount: 1,
          intervalUnit: 'MONTH',
          accountScopeMode: 'ALL',
          categoryScopeMode: 'ALL',
          tagScopeMode: 'NONE',
        }),
      }),
    );
    expect(await screen.findByText('Created budget')).toBeTruthy();
  });

  it('keeps the draft and uses one idempotency request while a failed create is retried', async () => {
    let rejectCreate: (reason: Error) => void = () => undefined;
    mockCreateBudget.mockImplementation(
      () =>
        new Promise((_, reject) => {
          rejectCreate = reject;
        }),
    );
    renderCreate();
    await fillRequiredFields();

    const submit = screen.getByRole('button', { name: 'Create budget' });
    fireEvent.click(submit);
    fireEvent.click(submit);
    expect(mockCreateBudget).toHaveBeenCalledTimes(1);
    const requestId = mockCreateBudget.mock.calls[0][0].clientRequestId;
    rejectCreate(new Error('Temporary service issue'));

    expect(await screen.findByText('Temporary service issue')).toBeTruthy();
    expect(screen.getByLabelText('Name').value).toBe('Monthly essentials');
    expect(screen.getByLabelText('Period limit').value).toBe('1250');

    mockCreateBudget.mockResolvedValueOnce({ id: 45, name: 'Monthly essentials', type: 'EXPENSE', currency: 'MXN' });
    fireEvent.click(screen.getByRole('button', { name: 'Create budget' }));
    await waitFor(() => expect(mockCreateBudget).toHaveBeenCalledTimes(2));
    expect(mockCreateBudget.mock.calls[1][0].clientRequestId).toBe(requestId);
  });

  it('offers calendar controls appropriate to each mode and sends the persisted custom recurrence rule', async () => {
    renderCreate();
    await fillRequiredFields();

    fireEvent.change(screen.getByLabelText('Calendar mode'), { target: { value: 'NATURAL' } });
    expect(await screen.findByLabelText('Month')).toBeTruthy();
    await waitFor(() =>
      expect(mockPreviewBudgetCalendar).toHaveBeenLastCalledWith(
        expect.objectContaining({ alignment: 'NATURAL', anchorDate: expect.stringMatching(/-01$/), customDayOfMonth: null }),
        expect.anything(),
      ),
    );

    fireEvent.change(screen.getByLabelText('Calendar mode'), { target: { value: 'CUSTOM' } });
    fireEvent.change(screen.getByLabelText('First cycle month'), { target: { value: '2027-02' } });
    fireEvent.change(screen.getByLabelText('Recurring day'), { target: { value: '31' } });
    expect(await screen.findByText(/last available day/i)).toBeTruthy();
    await waitFor(() =>
      expect(mockPreviewBudgetCalendar).toHaveBeenLastCalledWith(
        expect.objectContaining({ alignment: 'CUSTOM', anchorDate: '2027-02-28', customDayOfMonth: 31 }),
        expect.anything(),
      ),
    );

    fireEvent.change(screen.getByLabelText('Unit'), { target: { value: 'DAY' } });
    expect(screen.queryByLabelText('Calendar mode')).toBeNull();
    expect(screen.getByLabelText('Start date')).toBeTruthy();
  });

  it('creates an income goal with multiple selected accounts, a descendant category scope and an all-tags scope', async () => {
    mockGetAccounts.mockResolvedValue([
      { id: 11, name: 'Daily', accountType: 'DEBIT', currency: 'MXN', active: true },
      { id: 12, name: 'Savings', accountType: 'CASH', currency: 'MXN', active: true },
      { id: 13, name: 'Dollar', accountType: 'DEBIT', currency: 'USD', active: true },
    ]);
    mockGetCategories.mockResolvedValue([{ id: 21, name: 'Salary', categoryType: 'INCOME', active: true }]);
    mockGetTags.mockResolvedValue([
      { id: 31, name: 'Recurring', active: true },
      { id: 32, name: 'Verified', active: true },
    ]);
    mockCreateBudget.mockResolvedValue({ id: 46, name: 'Income goal', type: 'INCOME', currency: 'MXN' });
    renderCreate();

    await waitFor(() => expect(mockGetAccounts).toHaveBeenCalledTimes(1));
    await fillRequiredFields();
    fireEvent.change(screen.getByLabelText('I want to track'), { target: { value: 'INCOME' } });
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Income goal' } });

    fireEvent.click(screen.getByLabelText('Selected accounts'));
    await screen.findByLabelText(/Daily/);
    fireEvent.click(screen.getByLabelText(/Daily/));
    fireEvent.click(screen.getByLabelText(/Savings/));

    fireEvent.click(screen.getByLabelText('Selected categories and their subcategories'));
    fireEvent.click(screen.getByLabelText(/Salary/));

    fireEvent.click(screen.getByLabelText('Selected tags'));
    fireEvent.click(screen.getByLabelText('All selected tags'));
    fireEvent.click(screen.getByTestId('budgetTagSelectorAdd'));
    fireEvent.click(await screen.findByRole('button', { name: 'Recurring' }));
    fireEvent.click(screen.getByTestId('budgetTagSelectorAdd'));
    fireEvent.click(await screen.findByRole('button', { name: 'Verified' }));

    fireEvent.click(screen.getByRole('button', { name: 'Create budget' }));
    await waitFor(() => expect(mockCreateBudget).toHaveBeenCalledTimes(1));
    expect(mockCreateBudget).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'INCOME',
        currency: 'MXN',
        initialRevision: expect.objectContaining({
          accountScopeMode: 'SELECTED',
          accountIds: [11, 12],
          categoryScopeMode: 'SELECTED',
          categoryIds: [21],
          tagScopeMode: 'SELECTED',
          tagIds: [31, 32],
          tagMatchMode: 'ALL',
        }),
      }),
    );
    expect(await screen.findByText('Created budget')).toBeTruthy();
  });

  it('requires a concrete selection when a selected account scope is submitted', async () => {
    renderCreate();
    await fillRequiredFields();
    fireEvent.click(screen.getByLabelText('Selected accounts'));
    fireEvent.click(screen.getByRole('button', { name: 'Create budget' }));

    expect(await screen.findByText('Select at least one account.')).toBeTruthy();
    expect(mockCreateBudget).not.toHaveBeenCalled();
  });

  it('requires a currency before exposing accounts and removes incompatible selections after a currency change', async () => {
    mockGetAccounts.mockResolvedValue([
      { id: 11, name: 'Daily', accountType: 'DEBIT', currency: 'MXN', active: true },
      { id: 12, name: 'Dollar', accountType: 'DEBIT', currency: 'USD', active: true },
    ]);
    renderCreate();
    await waitFor(() => expect(mockGetAccounts).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByLabelText('Selected accounts'));

    expect(await screen.findByText('Select a currency first.')).toBeTruthy();
    expect(screen.queryByLabelText(/Daily/)).toBeNull();

    fireEvent.change(screen.getByLabelText('Currency'), { target: { value: 'MXN' } });
    const daily = await screen.findByLabelText(/Daily/);
    expect(screen.queryByText('Select a currency first.')).toBeNull();
    expect(screen.queryByLabelText(/Dollar/)).toBeNull();
    fireEvent.click(daily);
    expect((daily as HTMLInputElement).checked).toBe(true);

    fireEvent.change(screen.getByLabelText('Currency'), { target: { value: 'USD' } });
    expect(screen.queryByLabelText(/Daily/)).toBeNull();
    expect(await screen.findByLabelText(/Dollar/)).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Currency'), { target: { value: 'MXN' } });
    expect(await screen.findByLabelText(/Daily/)).toHaveProperty('checked', false);
  });

  it('localizes known calendar validation errors instead of surfacing backend text in Spanish', () => {
    expect(calendarValidationError(new Error('Anchor date is not a natural boundary for the selected interval.'), 'es', 'fallback')).toBe(
      'El inicio debe ser un límite válido del calendario natural seleccionado.',
    );
    expect(
      calendarValidationError(
        new Error('The first custom calendar cycle must begin on the selected recurring boundary.'),
        'es',
        'fallback',
      ),
    ).toBe('El primer ciclo debe comenzar en el límite configurado.');
  });
});
