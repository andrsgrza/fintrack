import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enCreditAccountDetails from 'app/../i18n/en/creditAccountDetails.json';
import { CreditAccountDetailsUpdate } from './credit-account-details-update';

const mockDispatch = jest.fn();
const mockCreateEntity = jest.fn(entity => ({ type: 'creditAccountDetails/createEntity', payload: entity }));
const mockUpdateEntity = jest.fn(entity => ({ type: 'creditAccountDetails/updateEntity', payload: entity }));
const mockGetEntity = jest.fn(id => ({ type: 'creditAccountDetails/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'creditAccountDetails/reset' }));
const mockGetFinancialAccounts = jest.fn(params => ({ type: 'financialAccount/getEntities', payload: params }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./credit-account-details.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  updateEntity: entity => mockUpdateEntity(entity),
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: params => mockGetFinancialAccounts(params),
}));

const baseState = {
  financialAccount: {
    entities: [
      { id: 1, name: 'Everyday checking', accountType: 'DEBIT' },
      { id: 2, name: 'Main credit card', accountType: 'CREDIT_CARD' },
      { id: 3, name: 'Wallet cash', accountType: 'CASH' },
      { id: 4, name: 'Travel credit card', accountType: 'CREDIT_CARD' },
    ],
  },
  creditAccountDetails: {
    entity: {},
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enCreditAccountDetails);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = {
    ...baseState,
    creditAccountDetails: {
      ...baseState.creditAccountDetails,
      entity: {},
    },
  };

  return render(
    <MemoryRouter initialEntries={['/credit-account-details/new']}>
      <Routes>
        <Route path="/credit-account-details/new" element={<CreditAccountDetailsUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = () => {
  mockState = {
    ...baseState,
    creditAccountDetails: {
      ...baseState.creditAccountDetails,
      entity: {
        id: 10,
        creditLimit: 50000,
        statementDay: 15,
        paymentDueDay: 5,
        annualInterestRate: 65,
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-02T00:00:00Z',
        account: { id: 2, name: 'Main credit card', accountType: 'CREDIT_CARD' },
      },
    },
  };

  return render(
    <MemoryRouter initialEntries={['/credit-account-details/10/edit']}>
      <Routes>
        <Route path="/credit-account-details/:id/edit" element={<CreditAccountDetailsUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('CreditAccountDetails update form', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
    mockCreateEntity.mockClear();
    mockUpdateEntity.mockClear();
    mockGetEntity.mockClear();
    mockReset.mockClear();
    mockGetFinancialAccounts.mockClear();
  });

  it('renders create mode with a focused title and without timestamp fields', () => {
    renderCreateForm();

    expect(screen.getByText('Create Credit Account Details')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
  });

  it('renders edit mode with a focused title and without timestamp fields', () => {
    renderEditForm();

    expect(screen.getByText('Edit Credit Account Details')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
  });

  it('shows only credit card accounts in the create account selector', () => {
    renderCreateForm();

    const accountSelect = screen.getByLabelText('Account') as HTMLSelectElement;
    const optionLabels = Array.from(accountSelect.options).map(option => option.textContent);

    expect(accountSelect.disabled).toBe(false);
    expect(optionLabels).toContain('Main credit card');
    expect(optionLabels).toContain('Travel credit card');
    expect(optionLabels).not.toContain('Everyday checking');
    expect(optionLabels).not.toContain('Wallet cash');
  });

  it('does not allow changing the account in edit mode', () => {
    renderEditForm();

    expect(screen.queryByLabelText('Account')).toBeNull();
  });

  it('renders financial field labels and help text', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Credit limit')).toBeTruthy();
    expect(screen.getByText('Maximum approved credit line for this card.')).toBeTruthy();
    expect(screen.getByLabelText('Statement day')).toBeTruthy();
    expect(screen.getByText('Day of the month when the statement period closes.')).toBeTruthy();
    expect(screen.getByLabelText('Payment due day')).toBeTruthy();
    expect(screen.getByText('Day of the month when payment is due.')).toBeTruthy();
    expect(screen.getByLabelText('Annual interest rate')).toBeTruthy();
    expect(screen.getByText('Annual percentage interest rate. Used later for interest calculations.')).toBeTruthy();
  });

  it('submits create payload without fake timestamps', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '50000' } });
    fireEvent.change(screen.getByLabelText('Statement day'), { target: { value: '15' } });
    fireEvent.change(screen.getByLabelText('Payment due day'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Annual interest rate'), { target: { value: '65' } });
    fireEvent.change(screen.getByLabelText('Account'), { target: { value: '2' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(
      expect.not.objectContaining({
        createdAt: expect.anything(),
        updatedAt: expect.anything(),
      }),
    );
    expect(mockCreateEntity.mock.calls[0][0].account).toEqual(expect.objectContaining({ id: 2, accountType: 'CREDIT_CARD' }));
  });
});
