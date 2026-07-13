import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import { FinancialAccountDetail } from './financial-account-detail';
import { FinancialAccountUpdate } from './financial-account-update';

const mockDispatch = jest.fn();
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

const baseState = {
  budget: {
    entities: [],
  },
  transactionIngestion: {
    entities: [],
  },
  financialAccount: {
    entity: {},
    entities: [],
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialAccount);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = {
    ...baseState,
    financialAccount: {
      ...baseState.financialAccount,
      entity: {},
    },
  };

  return render(
    <MemoryRouter initialEntries={['/financial-account/new']}>
      <Routes>
        <Route path="/financial-account/new" element={<FinancialAccountUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = accountType => {
  mockState = {
    ...baseState,
    financialAccount: {
      ...baseState.financialAccount,
      entity: {
        id: 1,
        name: 'Test account',
        accountType,
        initialBalance: 123,
        initialBalanceDate: '2026-01-10',
      },
    },
  };

  return render(
    <MemoryRouter initialEntries={['/financial-account/1']}>
      <Routes>
        <Route path="/financial-account/:id" element={<FinancialAccountDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('FinancialAccount opening-position labels', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
  });

  it('shows DEBIT opening-position copy on initial create render', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Initial balance')).toBeTruthy();
    expect(
      screen.getByText(
        'Amount available when you started tracking this account. Negative values represent overdraft or a negative balance.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('Opening card balance')).toBeNull();
    expect(screen.queryByText('Initial cash')).toBeNull();
    expect(screen.queryByText('Initial account value')).toBeNull();
    expect(screen.getByLabelText('Tracking start date')).toBeTruthy();
  });

  it('changes DEBIT to CREDIT_CARD labels and resets initial balance fields', () => {
    renderCreateForm();

    const accountTypeSelect = screen.getByLabelText('Account Type');
    const initialBalanceInput = screen.getByLabelText('Initial balance') as HTMLInputElement;
    const initialBalanceDateInput = screen.getByLabelText('Tracking start date') as HTMLInputElement;

    fireEvent.change(initialBalanceInput, { target: { value: '100' } });
    fireEvent.change(initialBalanceDateInput, { target: { value: '2026-01-10' } });

    fireEvent.change(accountTypeSelect, { target: { value: 'CREDIT_CARD' } });

    expect(screen.getByLabelText('Opening card balance')).toBeTruthy();
    expect(
      screen.getByText(
        'Use a positive number if you owed money, or a negative number if you had credit in your favor. This is not your credit limit.',
      ),
    ).toBeTruthy();
    expect(
      screen.queryByText(
        'Amount available when you started tracking this account. Negative values represent overdraft or a negative balance.',
      ),
    ).toBeNull();
    expect((screen.getByLabelText('Opening card balance') as HTMLInputElement).value).toBe('');
    expect((screen.getByLabelText('Tracking start date') as HTMLInputElement).value).toBe('');
  });

  it('preserves unrelated form fields when account type changes', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Everyday account' } });
    fireEvent.change(screen.getByLabelText('Institution Name'), { target: { value: 'My bank' } });
    fireEvent.change(screen.getByLabelText('Currency'), { target: { value: 'USD' } });
    fireEvent.change(screen.getByLabelText('Last Four Digits'), { target: { value: '1234' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Personal checking account' } });

    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'CREDIT_CARD' } });

    expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Everyday account');
    expect((screen.getByLabelText('Institution Name') as HTMLInputElement).value).toBe('My bank');
    expect((screen.getByLabelText('Currency') as HTMLInputElement).value).toBe('USD');
    expect((screen.getByLabelText('Last Four Digits') as HTMLInputElement).value).toBe('1234');
    expect((screen.getByLabelText('Description') as HTMLInputElement).value).toBe('Personal checking account');
  });

  it('changes CREDIT_CARD back to DEBIT labels and resets initial balance', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'CREDIT_CARD' } });
    const cardBalanceInput = screen.getByLabelText('Opening card balance') as HTMLInputElement;
    fireEvent.change(cardBalanceInput, { target: { value: '5000' } });

    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'DEBIT' } });

    expect(screen.getByLabelText('Initial balance')).toBeTruthy();
    expect(
      screen.getByText(
        'Amount available when you started tracking this account. Negative values represent overdraft or a negative balance.',
      ),
    ).toBeTruthy();
    expect(
      screen.queryByText(
        'Use a positive number if you owed money, or a negative number if you had credit in your favor. This is not your credit limit.',
      ),
    ).toBeNull();
    expect((screen.getByLabelText('Initial balance') as HTMLInputElement).value).toBe('');
  });

  it('shows CASH opening-position copy', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'CASH' } });

    expect(screen.getByLabelText('Initial cash')).toBeTruthy();
    expect(
      screen.getByText('Cash on hand when you started tracking. Negative values can represent an adjustment or negative cash position.'),
    ).toBeTruthy();
    expect(screen.queryByText('Initial balance')).toBeNull();
    expect(screen.queryByText('Opening card balance')).toBeNull();
    expect(screen.queryByText('Initial account value')).toBeNull();
  });

  it('shows INVESTMENT opening-position copy', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'INVESTMENT' } });

    expect(screen.getByLabelText('Initial account value')).toBeTruthy();
    expect(screen.getByText('Account value when you started tracking. Detailed investment valuation is deferred.')).toBeTruthy();
    expect(screen.queryByText('Initial balance')).toBeNull();
    expect(screen.queryByText('Initial cash')).toBeNull();
    expect(screen.queryByText('Opening card balance')).toBeNull();
  });

  it('uses the DEBIT opening-position label in detail view', () => {
    renderDetail('DEBIT');

    expect(screen.getByText('Initial balance')).toBeTruthy();
    expect(screen.getByText('Tracking start date')).toBeTruthy();
    expect(screen.getByText('123')).toBeTruthy();
    expect(screen.queryByText('Opening card balance')).toBeNull();
  });

  it('uses the CREDIT_CARD opening-position label in detail view', () => {
    renderDetail('CREDIT_CARD');

    expect(screen.getByText('Opening card balance')).toBeTruthy();
    expect(screen.getByText('Tracking start date')).toBeTruthy();
    expect(screen.getByText('123')).toBeTruthy();
  });
});
