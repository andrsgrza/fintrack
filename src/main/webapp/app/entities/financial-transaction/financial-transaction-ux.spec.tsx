import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import { FinancialTransactionDetail } from './financial-transaction-detail';
import { FinancialTransactionUpdate } from './financial-transaction-update';

const mockDispatch = jest.fn();
const mockCreateEntity = jest.fn(entity => ({ type: 'financialTransaction/createEntity', payload: { data: { id: 99, ...entity } } }));
const mockPartialUpdateEntity = jest.fn(entity => ({
  type: 'financialTransaction/partialUpdateEntity',
  payload: { data: entity },
}));
const mockGetEntity = jest.fn(id => ({ type: 'financialTransaction/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'financialTransaction/reset' }));
const mockGetFinancialAccounts = jest.fn(() => ({ type: 'financialAccount/getEntities' }));
const mockGetCategories = jest.fn(() => ({ type: 'category/getEntities' }));
const mockGetTags = jest.fn(() => ({ type: 'tag/getEntities' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./financial-transaction.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: () => mockGetFinancialAccounts(),
}));

jest.mock('app/entities/category/category.reducer', () => ({
  getEntities: () => mockGetCategories(),
}));

jest.mock('app/entities/tag/tag.reducer', () => ({
  getEntities: () => mockGetTags(),
}));

const accounts = [
  { id: 1, name: 'Checking', currency: 'MXN' },
  { id: 2, name: 'Cash', currency: 'USD' },
];

const categories = [
  { id: 10, name: 'Groceries', categoryType: 'EXPENSE' },
  { id: 11, name: 'Salary', categoryType: 'INCOME' },
  { id: 12, name: 'Adjustment', categoryType: 'BOTH' },
];

const tags = [
  { id: 20, name: 'Personal' },
  { id: 21, name: 'Important' },
];

const baseState = {
  financialAccount: {
    entities: accounts,
  },
  category: {
    entities: categories,
  },
  tag: {
    entities: tags,
  },
  financialTransaction: {
    entity: {},
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const existingTransaction = {
  id: 2501,
  transactionDate: '2026-07-13',
  postingDate: '2026-07-13',
  description: 'Bus fare',
  amount: 3,
  flow: 'OUT',
  origin: 'MANUAL',
  externalReference: 'None',
  notes: 'Some note',
  createdAt: '2026-07-13T16:00:00Z',
  updatedAt: '2026-07-13T16:00:00Z',
  account: accounts[0],
  category: categories[0],
  transactionIngestion: { id: 99 },
  tags: [tags[0]],
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = {
    ...baseState,
    financialTransaction: {
      ...baseState.financialTransaction,
      entity: {},
    },
  };

  return render(
    <MemoryRouter initialEntries={['/financial-transaction/new']}>
      <Routes>
        <Route path="/financial-transaction/new" element={<FinancialTransactionUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = () => {
  mockState = {
    ...baseState,
    financialTransaction: {
      ...baseState.financialTransaction,
      entity: existingTransaction,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/financial-transaction/2501/edit']}>
      <Routes>
        <Route path="/financial-transaction/:id/edit" element={<FinancialTransactionUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = {
    ...baseState,
    financialTransaction: {
      ...baseState.financialTransaction,
      entity: existingTransaction,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/financial-transaction/2501']}>
      <Routes>
        <Route path="/financial-transaction/:id" element={<FinancialTransactionDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

const selectOptions = (label: string) =>
  Array.from((screen.getByLabelText(label) as HTMLSelectElement).options).map(option => option.textContent);

describe('FinancialTransaction manual UX cleanup', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
    mockDispatch.mockImplementation(action => action);
    mockCreateEntity.mockClear();
    mockPartialUpdateEntity.mockClear();
    mockGetEntity.mockClear();
    mockReset.mockClear();
    mockGetFinancialAccounts.mockClear();
    mockGetCategories.mockClear();
    mockGetTags.mockClear();
  });

  it('create form hides technical fields and keeps account selectable', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Account')).toBeTruthy();
    expect((screen.getByLabelText('Account') as HTMLSelectElement).disabled).toBe(false);
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Origin')).toBeNull();
    expect(screen.queryByLabelText('Transaction Ingestion')).toBeNull();
    expect(screen.queryByLabelText('External Reference')).toBeNull();
    expect(screen.queryByLabelText('Financial Subscription')).toBeNull();
  });

  it('create submit sends MANUAL origin and no ingestion or timestamps', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Account'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('Transaction date'), { target: { value: '2026-07-13' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Bus fare' } });
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    const payload = mockCreateEntity.mock.calls[0][0];
    expect(payload).toEqual(
      expect.objectContaining({
        account: expect.objectContaining({ id: 1 }),
        origin: 'MANUAL',
        amount: 3,
      }),
    );
    expect(payload).not.toHaveProperty('transactionIngestion');
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
  });

  it('filters category options by flow on create', () => {
    renderCreateForm();

    expect(selectOptions('Category')).toEqual(['', 'Salary', 'Adjustment']);

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'OUT' } });

    expect(selectOptions('Category')).toEqual(['', 'Groceries', 'Adjustment']);
  });

  it('clears incompatible category when flow changes', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: '11' } });
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'OUT' } });
    fireEvent.change(screen.getByLabelText('Account'), { target: { value: '1' } });
    fireEvent.change(screen.getByLabelText('Transaction date'), { target: { value: '2026-07-13' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Bus fare' } });
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(expect.objectContaining({ category: null, flow: 'OUT' }));
  });

  it('edit form hides technical fields and keeps account immutable', () => {
    renderEditForm();

    expect(screen.getByDisplayValue('Checking')).toBeTruthy();
    expect((screen.getByLabelText('Account') as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Origin')).toBeNull();
    expect(screen.queryByLabelText('Transaction Ingestion')).toBeNull();
    expect(screen.queryByLabelText('External Reference')).toBeNull();
    expect(screen.queryByLabelText('Financial Subscription')).toBeNull();
  });

  it('edit submit uses partial update without immutable/server-owned fields', async () => {
    renderEditForm();

    fireEvent.change(screen.getByLabelText('Transaction date'), { target: { value: '2026-07-14' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockPartialUpdateEntity).toHaveBeenCalled());
    const payload = mockPartialUpdateEntity.mock.calls[0][0];
    expect(payload).toEqual(
      expect.objectContaining({
        id: 2501,
        transactionDate: '2026-07-14',
        flow: 'OUT',
      }),
    );
    expect(payload).not.toHaveProperty('account');
    expect(payload).not.toHaveProperty('origin');
    expect(payload).not.toHaveProperty('transactionIngestion');
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
  });

  it('detail shows clean fields with amount currency and hides technical metadata', () => {
    renderDetail();

    expect(screen.getByText('Checking')).toBeTruthy();
    expect(screen.getAllByText('13/07/2026')).toHaveLength(2);
    expect(screen.getByText('Expense')).toBeTruthy();
    expect(screen.getByText('3 MXN')).toBeTruthy();
    expect(screen.getByText('Bus fare')).toBeTruthy();
    expect(screen.getByText('Groceries')).toBeTruthy();
    expect(screen.getByText('Personal')).toBeTruthy();
    expect(screen.getByText('Some note')).toBeTruthy();
    expect(screen.getByText('MANUAL')).toBeTruthy();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('Transaction Ingestion')).toBeNull();
    expect(screen.queryByText('External Reference')).toBeNull();
    expect(screen.queryByText('Financial Subscription')).toBeNull();
  });
});
