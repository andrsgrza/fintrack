import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import enAccountType from 'app/../i18n/en/accountType.json';
import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import enCreditAccountDetails from 'app/../i18n/en/creditAccountDetails.json';
import enGlobal from 'app/../i18n/en/global.json';
import { FinancialAccountDetail } from './financial-account-detail';
import { FinancialAccountUpdate } from './financial-account-update';

jest.mock('axios');

const mockAxiosGet = axios.get as jest.Mock;
const mockAxiosPost = axios.post as jest.Mock;
const mockAxiosPut = axios.put as jest.Mock;
const mockDispatch = jest.fn();
const mockCreateEntity = jest.fn(entity => ({ type: 'financialAccount/createEntity', payload: { data: { id: 99, ...entity } } }));
const mockUpdateEntity = jest.fn(entity => ({ type: 'financialAccount/updateEntity', payload: { data: entity } }));
const mockGetEntity = jest.fn(id => ({ type: 'financialAccount/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'financialAccount/reset' }));
const mockCreateCreditAccountDetails = jest.fn(entity => ({
  type: 'creditAccountDetails/createEntity',
  payload: { data: { id: 25, ...entity } },
}));
const mockUpdateCreditAccountDetails = jest.fn(entity => ({
  type: 'creditAccountDetails/updateEntity',
  payload: { data: entity },
}));
const mockGetCreditAccountDetailsByAccountId = jest.fn(accountId => ({
  type: 'creditAccountDetails/getEntityByAccountId',
  payload: accountId,
}));
const mockResetCreditAccountDetails = jest.fn(() => ({ type: 'creditAccountDetails/reset' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./financial-account.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  updateEntity: entity => mockUpdateEntity(entity),
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
}));

jest.mock('app/entities/credit-account-details/credit-account-details.reducer', () => ({
  createEntity: entity => mockCreateCreditAccountDetails(entity),
  updateEntity: entity => mockUpdateCreditAccountDetails(entity),
  getEntityByAccountId: accountId => mockGetCreditAccountDetailsByAccountId(accountId),
  reset: () => mockResetCreditAccountDetails(),
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
  creditAccountDetails: {
    entity: {},
    entities: [],
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialAccount);
  TranslatorContext.registerTranslations('en', enAccountType);
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.registerTranslations('en', enCreditAccountDetails);
  TranslatorContext.registerTranslations('en', enGlobal);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = (creditAccountDetailsEntity = {}) => {
  mockState = {
    ...baseState,
    financialAccount: {
      ...baseState.financialAccount,
      entity: {},
    },
    creditAccountDetails: {
      ...baseState.creditAccountDetails,
      entity: creditAccountDetailsEntity,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/financial-account/new']}>
      <Routes>
        <Route path="/financial-account/new" element={<FinancialAccountUpdate />} />
        <Route path="/financial-account/:id" element={<div>Financial account detail</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = (accountType = 'CREDIT_CARD', creditAccountDetailsEntity = {}) => {
  mockState = {
    ...baseState,
    financialAccount: {
      ...baseState.financialAccount,
      entity: {
        id: 1,
        name: 'Existing account',
        accountType,
        currency: 'MXN',
        initialBalance: 500,
        initialBalanceDate: '2026-01-10',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-02T00:00:00Z',
        budgets: [],
        transactionIngestions: [],
      },
    },
    creditAccountDetails: {
      ...baseState.creditAccountDetails,
      entity: creditAccountDetailsEntity,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/financial-account/1/edit']}>
      <Routes>
        <Route path="/financial-account/:id/edit" element={<FinancialAccountUpdate />} />
        <Route path="/financial-account/:id" element={<div>Financial account detail</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const buildBalance = (accountType, overrides = {}) => ({
  accountId: 1,
  accountName: 'Test account',
  accountType,
  currency: 'MXN',
  initialBalance: 123,
  initialBalanceDate: '2026-01-10',
  asOfDate: '2026-02-10',
  inflowTotal: 200,
  outflowTotal: 50,
  currentBalance: accountType === 'CREDIT_CARD' ? undefined : 273,
  currentDebt: accountType === 'CREDIT_CARD' ? 73 : undefined,
  creditLimit: accountType === 'CREDIT_CARD' ? 1000 : undefined,
  availableCredit: accountType === 'CREDIT_CARD' ? 927 : undefined,
  missingCreditDetails: false,
  ...overrides,
});

const buildRecentTransactions = (overrides = []) => overrides;

const pendingRequest = () => new Promise(() => {});

const setupDetailAxiosMocks = (accountType, options: any = {}) => {
  const balanceOption = Object.prototype.hasOwnProperty.call(options, 'balance') ? options.balance : 'pending';
  const transactionsOption = Object.prototype.hasOwnProperty.call(options, 'transactions') ? options.transactions : 'pending';

  mockAxiosGet.mockImplementation((url: string) => {
    if (url === 'api/financial-accounts/1/balance') {
      if (balanceOption === 'pending') {
        return pendingRequest();
      }
      if (balanceOption === 'error') {
        return Promise.reject(new Error('balance failed'));
      }
      return Promise.resolve({ data: buildBalance(accountType, balanceOption) });
    }
    if (url === 'api/financial-transactions?accountId.equals=1&sort=transactionDate,desc&sort=id,desc&size=5') {
      if (transactionsOption === 'pending') {
        return pendingRequest();
      }
      if (transactionsOption === 'error') {
        return Promise.reject(new Error('transactions failed'));
      }
      return Promise.resolve({ data: buildRecentTransactions(transactionsOption) });
    }
    return pendingRequest();
  });
};

const renderDetail = (accountType, creditAccountDetailsEntity = {}, detailOptions?) => {
  if (!mockAxiosGet.getMockImplementation()) {
    const options =
      detailOptions === undefined ||
      Object.prototype.hasOwnProperty.call(detailOptions, 'balance') ||
      Object.prototype.hasOwnProperty.call(detailOptions, 'transactions') ||
      Object.prototype.hasOwnProperty.call(detailOptions, 'account')
        ? detailOptions
        : { balance: detailOptions };
    setupDetailAxiosMocks(accountType, options);
  }
  const accountOverrides = detailOptions?.account ?? {};
  mockState = {
    ...baseState,
    financialAccount: {
      ...baseState.financialAccount,
      entity: {
        id: 1,
        name: 'Test account',
        accountType,
        currency: 'MXN',
        active: true,
        institutionName: 'Test bank',
        lastFourDigits: '1234',
        initialBalance: 123,
        initialBalanceDate: '2026-01-10',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-02T00:00:00Z',
        budgets: [{ id: 21 }],
        transactionIngestions: [{ id: 22 }],
        ...accountOverrides,
      },
    },
    creditAccountDetails: {
      ...baseState.creditAccountDetails,
      entity: creditAccountDetailsEntity,
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

const expectNoMissingTranslations = () => {
  expect(screen.queryByText(/translation-not-found\[fintrackApp\.financialAccount\.creditCardDetails/)).toBeNull();
  expect(screen.queryByText(/translation-not-found\[fintrackApp\.creditAccountDetails\.composition/)).toBeNull();
};

const expectFieldBefore = (firstLabel: string, secondLabel: string) => {
  const firstField = screen.getByLabelText(firstLabel);
  const secondField = screen.getByLabelText(secondLabel);

  expect(firstField.compareDocumentPosition(secondField) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
};

describe('FinancialAccount opening-position labels', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
    mockDispatch.mockImplementation(action => action);
    mockCreateEntity.mockClear();
    mockUpdateEntity.mockClear();
    mockGetEntity.mockClear();
    mockReset.mockClear();
    mockCreateCreditAccountDetails.mockClear();
    mockUpdateCreditAccountDetails.mockClear();
    mockGetCreditAccountDetailsByAccountId.mockClear();
    mockResetCreditAccountDetails.mockClear();
    mockAxiosGet.mockReset();
    mockAxiosPost.mockReset();
    mockAxiosPut.mockReset();
  });

  it('shows DEBIT opening-position copy on initial create render', () => {
    renderCreateForm();

    expect(screen.getByText('Create Financial Account')).toBeTruthy();
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
    expect(screen.queryByLabelText('Active')).toBeNull();
    expect((screen.getByLabelText('Account Type') as HTMLSelectElement).disabled).toBe(false);
    expect((screen.getByLabelText('Currency') as HTMLSelectElement).disabled).toBe(false);
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Budgets')).toBeNull();
    expect(screen.queryByLabelText('Transaction Ingestions')).toBeNull();
    expect(screen.queryByText('Credit details')).toBeNull();
  });

  it('renders a translated label for every supported account type', () => {
    renderCreateForm();

    const optionLabels = Array.from((screen.getByLabelText('Account Type') as HTMLSelectElement).options).map(option => option.textContent);

    expect(optionLabels).toEqual(['Debit account', 'Cash', 'Credit card', 'Investment account']);
    expect(optionLabels).not.toContain('DEBIT');
    expect(optionLabels).not.toContain('CREDIT_CARD');
    expect(optionLabels).not.toContain('INVESTMENT');
  });

  it('renders secondary account fields before account type in create mode', () => {
    renderCreateForm();

    expectFieldBefore('Currency', 'Account Type');
    expectFieldBefore('Last Four Digits', 'Account Type');
    expectFieldBefore('Description', 'Account Type');
    expectFieldBefore('Color', 'Account Type');
    expectFieldBefore('Icon', 'Account Type');
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
    expect(screen.getByText('Credit details')).toBeTruthy();
    expect(screen.getByLabelText('Credit limit')).toBeTruthy();
    expect(screen.getByLabelText('Statement closing day')).toBeTruthy();
    expect(screen.getByLabelText('Payment due day')).toBeTruthy();
    expect(screen.getByLabelText('Annual interest rate')).toBeTruthy();
    expect(screen.queryByLabelText('Account')).toBeNull();
    expectNoMissingTranslations();
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

  it('does not expose server-owned timestamps in edit mode and locks immutable selects', () => {
    renderEditForm();

    expect(screen.getByText('Edit Financial Account')).toBeTruthy();
    expect(screen.getByLabelText('Opening card balance')).toBeTruthy();
    expect(screen.getByLabelText('Active')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect((screen.getByLabelText('Account Type') as HTMLSelectElement).disabled).toBe(true);
    expect((screen.getByLabelText('Currency') as HTMLSelectElement).disabled).toBe(true);
    expect(screen.queryByLabelText('Budgets')).toBeNull();
    expect(screen.queryByLabelText('Transaction Ingestions')).toBeNull();
  });

  it('creates a debit account with one configured request while the active checkbox is hidden', async () => {
    renderCreateForm();
    mockAxiosPost.mockResolvedValue({ data: { financialAccount: { id: 99 } } });

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New account' } });
    fireEvent.change(screen.getByLabelText('Initial balance'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('Tracking start date'), { target: { value: '2026-01-10' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/financial-accounts/configured', expect.any(Object)));
    expect(mockAxiosPost.mock.calls[0][1]).toEqual(
      expect.objectContaining({ financialAccount: expect.objectContaining({ active: true, accountType: 'DEBIT' }) }),
    );
  });

  it('creates a CREDIT_CARD and its details with one configured request', async () => {
    renderCreateForm();
    mockAxiosPost.mockResolvedValue({ data: { financialAccount: { id: 99 } } });

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New card' } });
    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'CREDIT_CARD' } });
    fireEvent.change(screen.getByLabelText('Opening card balance'), { target: { value: '5000' } });
    fireEvent.change(screen.getByLabelText('Tracking start date'), { target: { value: '2026-01-10' } });
    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '50000' } });
    fireEvent.change(screen.getByLabelText('Statement closing day'), { target: { value: '15' } });
    fireEvent.change(screen.getByLabelText('Payment due day'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Annual interest rate'), { target: { value: '65' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledWith('api/financial-accounts/configured', expect.any(Object)));
    expect(mockAxiosPost.mock.calls[0][1]).toEqual(
      expect.objectContaining({
        creditAccountDetails: expect.objectContaining({
          creditLimit: 50000,
          statementDay: 15,
          paymentDueDay: 5,
          annualInterestRate: 65,
        }),
      }),
    );
    expect(mockCreateCreditAccountDetails).not.toHaveBeenCalled();
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
    expect(screen.queryByText('Credit details')).toBeNull();
    expectNoMissingTranslations();
  });

  it('does not show credit card details in DEBIT edit mode', () => {
    renderEditForm('DEBIT');

    expect(screen.queryByText('Credit details')).toBeNull();
  });

  it('hydrates existing credit card details and updates parent and child with one configured request', async () => {
    renderEditForm('CREDIT_CARD', {
      id: 25,
      creditLimit: 50000,
      statementDay: 15,
      paymentDueDay: 5,
      annualInterestRate: 65,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
      account: { id: 1, name: 'Existing account' },
    });

    expect(screen.getByText('Credit details')).toBeTruthy();
    expect((screen.getByLabelText('Credit limit') as HTMLInputElement).value).toBe('50000');
    expect(screen.queryByLabelText('Account')).toBeNull();
    expectNoMissingTranslations();

    mockAxiosPut.mockResolvedValue({ data: { financialAccount: { id: 1 } } });
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Existing account edited' } });
    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '60000' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockAxiosPut).toHaveBeenCalledWith('api/financial-accounts/1/configured', expect.any(Object)));
    expect(mockAxiosPut.mock.calls[0][1]).toEqual(
      expect.objectContaining({
        financialAccount: expect.objectContaining({ name: 'Existing account edited', accountType: 'CREDIT_CARD' }),
        creditAccountDetails: expect.objectContaining({ creditLimit: 60000 }),
      }),
    );
    expect(mockAxiosPut.mock.calls[0][1].creditAccountDetails).toEqual(
      expect.objectContaining({ statementDay: 15, paymentDueDay: 5, annualInterestRate: 65 }),
    );
    expect(mockUpdateCreditAccountDetails).not.toHaveBeenCalled();
  });

  it('keeps an invalid credit-card form open and renders child validation errors without sending a request', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New card' } });
    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'CREDIT_CARD' } });
    fireEvent.change(screen.getByLabelText('Opening card balance'), { target: { value: '5000' } });
    fireEvent.change(screen.getByLabelText('Tracking start date'), { target: { value: '2026-01-10' } });
    expect((screen.getByLabelText('Account Type') as HTMLSelectElement).value).toBe('CREDIT_CARD');
    expect(screen.getByText('Credit details')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(screen.getAllByText('This field is required.')).toHaveLength(3));
    expect(mockAxiosPost).not.toHaveBeenCalled();
    expect(screen.getByLabelText('Credit limit')).toBeTruthy();
  });

  it('keeps the form open and shows one contextual error when the configured command fails', async () => {
    renderCreateForm();
    mockAxiosPost.mockRejectedValue(new Error('configured save failed'));

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New card' } });
    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'CREDIT_CARD' } });
    fireEvent.change(screen.getByLabelText('Opening card balance'), { target: { value: '5000' } });
    fireEvent.change(screen.getByLabelText('Tracking start date'), { target: { value: '2026-01-10' } });
    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '50000' } });
    fireEvent.change(screen.getByLabelText('Statement closing day'), { target: { value: '15' } });
    fireEvent.change(screen.getByLabelText('Payment due day'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toContain(
        'The account could not be saved. Review the account and credit-card details and try again.',
      ),
    );
    expect(screen.getByLabelText('Name')).toBeTruthy();
  });

  it('uses configured update to create missing credit account details for an existing CREDIT_CARD account', async () => {
    renderEditForm('CREDIT_CARD');
    mockAxiosPut.mockResolvedValue({ data: { financialAccount: { id: 1 } } });

    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '40000' } });
    fireEvent.change(screen.getByLabelText('Statement closing day'), { target: { value: '12' } });
    fireEvent.change(screen.getByLabelText('Payment due day'), { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockAxiosPut).toHaveBeenCalledWith('api/financial-accounts/1/configured', expect.any(Object)));
    expect(mockAxiosPut.mock.calls[0][1].creditAccountDetails).toEqual(
      expect.objectContaining({ creditLimit: 40000, statementDay: 12, paymentDueDay: 4 }),
    );
    expect(mockCreateCreditAccountDetails).not.toHaveBeenCalled();
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
    expect(screen.queryByText('Credit details')).toBeNull();
  });

  it('renders a product account detail without technical metadata or relationship dumps', () => {
    renderDetail('DEBIT');

    expect(screen.getByText('Account details')).toBeTruthy();
    expect(screen.getByTestId('financialAccountDetailAccountSection')).toBeTruthy();
    expect(screen.getByText('Test bank')).toBeTruthy();
    expect(screen.getByText('Debit account')).toBeTruthy();
    expect(screen.getByText('••••1234')).toBeTruthy();
    expect(screen.getByTestId('financialAccountDetailStatusSection').textContent).toContain('Active');
    expect(screen.queryByText('ID')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
    expect(screen.queryByText('Transaction Ingestions')).toBeNull();
    expect(screen.queryByText('true')).toBeNull();
    expect(screen.getByRole('link', { name: /edit/i }).getAttribute('href')).toBe('/financial-account/1/edit');
  });

  it('explains the historical-only meaning of an inactive account', () => {
    renderDetail('DEBIT', {}, { account: { active: false } });

    expect(screen.getByTestId('financialAccountStatus').textContent).toContain('Inactive');
    expect(screen.getByTestId('financialAccountInactiveExplanation').textContent).toContain(
      'cannot be selected for new transactions, imports, or rules until it is reactivated',
    );
  });

  it('shows DEBIT balance snapshot with current balance and hides credit-card-only fields', async () => {
    renderDetail('DEBIT', {}, { currentBalance: 273, inflowTotal: 200, outflowTotal: 50 });

    const section = await screen.findByTestId('financialAccountBalanceSection');

    expect(mockAxiosGet).toHaveBeenCalledWith('api/financial-accounts/1/balance');
    expect(within(section).getByText('Balance snapshot')).toBeTruthy();
    expect(within(section).getByText('Current balance')).toBeTruthy();
    expect(within(section).getByText('273 MXN')).toBeTruthy();
    expect(within(section).getByText('Inflow total')).toBeTruthy();
    expect(within(section).getByText('200 MXN')).toBeTruthy();
    expect(within(section).getByText('Outflow total')).toBeTruthy();
    expect(within(section).getByText('50 MXN')).toBeTruthy();
    expect(within(section).queryByText('Current debt')).toBeNull();
    expect(within(section).queryByText('Available credit')).toBeNull();
  });

  it('uses the CREDIT_CARD opening-position label in detail view', () => {
    renderDetail('CREDIT_CARD', {
      id: 25,
      creditLimit: 50000,
      statementDay: 15,
      paymentDueDay: 5,
      annualInterestRate: 65,
    });

    expect(screen.getByText('Opening card balance')).toBeTruthy();
    expect(screen.getByText('Tracking start date')).toBeTruthy();
    expect(screen.getByText('123')).toBeTruthy();
    expect(screen.getByText('Credit details')).toBeTruthy();
    expect(screen.getByText('Credit limit')).toBeTruthy();
    expect(screen.getByText('50000 MXN')).toBeTruthy();
    expect(screen.queryByRole('combobox', { name: 'Account' })).toBeNull();
    expectNoMissingTranslations();
  });

  it('shows CREDIT_CARD balance snapshot with debt, credit limit, and available credit', async () => {
    renderDetail(
      'CREDIT_CARD',
      {
        id: 25,
        creditLimit: 1000,
        statementDay: 15,
        paymentDueDay: 5,
        annualInterestRate: 65,
      },
      { currentDebt: 73, creditLimit: 1000, availableCredit: 927 },
    );

    const section = await screen.findByTestId('financialAccountBalanceSection');

    expect(within(section).getByText('Current debt')).toBeTruthy();
    expect(within(section).getByText('73 MXN')).toBeTruthy();
    expect(within(section).getByText('Credit limit')).toBeTruthy();
    expect(within(section).getByText('1000 MXN')).toBeTruthy();
    expect(within(section).getByText('Available credit')).toBeTruthy();
    expect(within(section).getByText('927 MXN')).toBeTruthy();
    expect(within(section).queryByText('Current balance')).toBeNull();
  });

  it('shows missing credit details warning in CREDIT_CARD balance snapshot', async () => {
    renderDetail('CREDIT_CARD', {}, { creditLimit: undefined, availableCredit: undefined, missingCreditDetails: true });

    const section = await screen.findByTestId('financialAccountBalanceSection');

    expect(within(section).getByText('Credit card details have not been configured yet.')).toBeTruthy();
    expect(within(section).getByText('Current debt')).toBeTruthy();
    expect(within(section).queryByText('Available credit')).toBeNull();
  });

  it('shows balance unavailable when balance request fails but keeps account detail rendered', async () => {
    mockAxiosGet.mockRejectedValue(new Error('balance failed'));
    renderDetail('DEBIT');

    expect(screen.getByText('Test account')).toBeTruthy();
    expect(await screen.findByText('Balance is not available.')).toBeTruthy();
    expect(screen.getByText('Initial balance')).toBeTruthy();
  });

  it('shows loading state while balance request is pending', async () => {
    let resolveBalance;
    mockAxiosGet.mockReturnValue(
      new Promise(resolve => {
        resolveBalance = resolve;
      }),
    );
    renderDetail('DEBIT');

    expect(screen.getByText('Loading balance...')).toBeTruthy();

    resolveBalance({ data: buildBalance('DEBIT') });
    await waitFor(() => expect(screen.queryByText('Loading balance...')).toBeNull());
  });

  it('renders recent transactions section and loading state', () => {
    renderDetail('DEBIT');

    expect(screen.getByText('Recent transactions')).toBeTruthy();
    expect(screen.getByText('Loading transactions...')).toBeTruthy();
  });

  it('shows empty recent transactions state', async () => {
    renderDetail('DEBIT', {}, { transactions: [] });

    const section = await screen.findByTestId('accountRecentTransactionsSection');

    expect(mockAxiosGet).toHaveBeenCalledWith(
      'api/financial-transactions?accountId.equals=1&sort=transactionDate,desc&sort=id,desc&size=5',
    );
    expect(within(section).getByText('No transactions yet.')).toBeTruthy();
    expect(within(section).getByText('View all transactions')).toBeTruthy();
  });

  it('shows recent transactions returned by API without edit or delete controls', async () => {
    renderDetail(
      'DEBIT',
      {},
      {
        transactions: [
          {
            id: 2501,
            transactionDate: '2026-07-13',
            description: 'Bus fare',
            flow: 'OUT',
            amount: 3,
            category: { name: 'Transport' },
          },
          {
            id: 2502,
            transactionDate: '2026-07-06',
            description: 'Refund',
            flow: 'IN',
            amount: 5,
          },
        ],
      },
    );

    const section = await screen.findByTestId('accountRecentTransactionsSection');

    expect(within(section).getByText('13/07/2026')).toBeTruthy();
    expect(within(section).getByText('Bus fare')).toBeTruthy();
    expect(within(section).getByText('Expense')).toBeTruthy();
    expect(within(section).getByText('3 MXN')).toBeTruthy();
    expect(within(section).getByText('Transport')).toBeTruthy();
    expect(within(section).getByText('06/07/2026')).toBeTruthy();
    expect(within(section).getByText('Refund')).toBeTruthy();
    expect(within(section).getByText('Income')).toBeTruthy();
    expect(within(section).getByText('5 MXN')).toBeTruthy();
    expect(within(section).queryByRole('link', { name: /edit/i })).toBeNull();
    expect(within(section).queryByRole('button', { name: /delete/i })).toBeNull();
  });

  it('shows recent transactions unavailable when request fails but keeps account detail rendered', async () => {
    renderDetail('DEBIT', {}, { transactions: 'error' });

    expect(screen.getByText('Test account')).toBeTruthy();
    expect(await screen.findByText('Transactions are not available.')).toBeTruthy();
    expect(screen.getByText('Initial balance')).toBeTruthy();
  });

  it('shows a clear detail message when CREDIT_CARD details are missing', () => {
    renderDetail('CREDIT_CARD');

    expect(screen.getByText('Credit card details have not been configured yet.')).toBeTruthy();
    expectNoMissingTranslations();
  });
});
