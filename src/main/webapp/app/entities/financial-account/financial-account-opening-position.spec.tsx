import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import enCreditAccountDetails from 'app/../i18n/en/creditAccountDetails.json';
import { FinancialAccountDetail } from './financial-account-detail';
import { FinancialAccountUpdate } from './financial-account-update';

jest.mock('axios');

const mockAxiosGet = axios.get as jest.Mock;
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
  TranslatorContext.registerTranslations('en', enCreditAccountDetails);
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

const renderDetail = (accountType, creditAccountDetailsEntity = {}, balanceOverride?) => {
  if (!mockAxiosGet.getMockImplementation()) {
    if (balanceOverride === undefined) {
      mockAxiosGet.mockReturnValue(new Promise(() => {}));
    } else {
      mockAxiosGet.mockResolvedValue({ data: buildBalance(accountType, balanceOverride) });
    }
  }
  mockState = {
    ...baseState,
    financialAccount: {
      ...baseState.financialAccount,
      entity: {
        id: 1,
        name: 'Test account',
        accountType,
        currency: 'MXN',
        initialBalance: 123,
        initialBalanceDate: '2026-01-10',
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
    expect(screen.queryByText('Credit card details')).toBeNull();
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
    expect(screen.getByText('Credit card details')).toBeTruthy();
    expect(screen.getByLabelText('Credit limit')).toBeTruthy();
    expect(screen.getByLabelText('Statement day')).toBeTruthy();
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

  it('submits active=true in create mode while the active checkbox is hidden', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New account' } });
    fireEvent.change(screen.getByLabelText('Initial balance'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('Tracking start date'), { target: { value: '2026-01-10' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(expect.objectContaining({ active: true }));
  });

  it('creates credit account details after creating a CREDIT_CARD financial account', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New card' } });
    fireEvent.change(screen.getByLabelText('Account Type'), { target: { value: 'CREDIT_CARD' } });
    fireEvent.change(screen.getByLabelText('Opening card balance'), { target: { value: '5000' } });
    fireEvent.change(screen.getByLabelText('Tracking start date'), { target: { value: '2026-01-10' } });
    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '50000' } });
    fireEvent.change(screen.getByLabelText('Statement day'), { target: { value: '15' } });
    fireEvent.change(screen.getByLabelText('Payment due day'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Annual interest rate'), { target: { value: '65' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateCreditAccountDetails).toHaveBeenCalled());
    expect(mockCreateCreditAccountDetails.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        creditLimit: 50000,
        statementDay: 15,
        paymentDueDay: 5,
        annualInterestRate: 65,
        account: expect.objectContaining({ id: 99, name: 'New card' }),
      }),
    );
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
    expect(screen.queryByText('Credit card details')).toBeNull();
    expectNoMissingTranslations();
  });

  it('does not show credit card details in DEBIT edit mode', () => {
    renderEditForm('DEBIT');

    expect(screen.queryByText('Credit card details')).toBeNull();
  });

  it('shows existing credit card details in CREDIT_CARD edit mode and updates them on save', async () => {
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

    expect(screen.getByText('Credit card details')).toBeTruthy();
    expect((screen.getByLabelText('Credit limit') as HTMLInputElement).value).toBe('50000');
    expect(screen.queryByLabelText('Account')).toBeNull();
    expectNoMissingTranslations();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Existing account edited' } });
    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '60000' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockUpdateCreditAccountDetails).toHaveBeenCalled());
    expect(mockUpdateCreditAccountDetails.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        id: 25,
        creditLimit: 60000,
        account: expect.objectContaining({ id: 1 }),
      }),
    );
  });

  it('creates missing credit account details for existing CREDIT_CARD account on save', async () => {
    renderEditForm('CREDIT_CARD');

    fireEvent.change(screen.getByLabelText('Credit limit'), { target: { value: '40000' } });
    fireEvent.change(screen.getByLabelText('Statement day'), { target: { value: '12' } });
    fireEvent.change(screen.getByLabelText('Payment due day'), { target: { value: '4' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateCreditAccountDetails).toHaveBeenCalled());
    expect(mockCreateCreditAccountDetails.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        creditLimit: 40000,
        statementDay: 12,
        paymentDueDay: 4,
        account: expect.objectContaining({ id: 1 }),
      }),
    );
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
    expect(screen.queryByText('Credit card details')).toBeNull();
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
    expect(screen.getByText('Credit card details')).toBeTruthy();
    expect(screen.getByText('Credit limit')).toBeTruthy();
    expect(screen.getByText('50000')).toBeTruthy();
    expect(screen.queryByText('Account')).toBeNull();
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

  it('shows a clear detail message when CREDIT_CARD details are missing', () => {
    renderDetail('CREDIT_CARD');

    expect(screen.getByText('Credit card details have not been configured yet.')).toBeTruthy();
    expectNoMissingTranslations();
  });
});
