import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import axios from 'axios';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import { FinancialTransactionDetail } from './financial-transaction-detail';
import { FinancialTransactionUpdate } from './financial-transaction-update';

const mockDispatch = jest.fn();
const mockPartialUpdateEntity = jest.fn(entity => ({
  type: 'financialTransaction/partialUpdateEntity',
  payload: { data: entity },
}));
const mockGetEntity = jest.fn(id => ({ type: 'financialTransaction/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'financialTransaction/reset' }));
const mockGetFinancialAccounts = jest.fn(() => ({ type: 'financialAccount/getEntities' }));
const mockGetCategories = jest.fn(() => ({ type: 'category/getEntities' }));
const mockGetTags = jest.fn(() => ({ type: 'tag/getEntities' }));
const mockAxiosPost = axios.post as jest.Mock;
let mockState;

jest.mock('axios');

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./financial-transaction.reducer', () => ({
  createEntity: entity => ({ type: 'financialTransaction/createEntity', payload: { data: entity } }),
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

const accounts = [{ id: 1, name: 'Checking', currency: 'MXN' }];
const categories = [{ id: 10, name: 'Groceries', categoryType: 'EXPENSE' }];
const tags = [{ id: 20, name: 'Personal' }];

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
    entity: existingTransaction,
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.setLocale('en');
};

const renderEditForm = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/financial-transaction/2501/edit']}>
      <Routes>
        <Route path="/financial-transaction/:id/edit" element={<FinancialTransactionUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/financial-transaction/2501']}>
      <Routes>
        <Route path="/financial-transaction/:id" element={<FinancialTransactionDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('FinancialTransaction posted transaction UX', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
    mockPartialUpdateEntity.mockClear();
    mockGetEntity.mockClear();
    mockReset.mockClear();
    mockGetFinancialAccounts.mockClear();
    mockGetCategories.mockClear();
    mockGetTags.mockClear();
    mockAxiosPost.mockReset();
  });

  it('edit form hides technical fields, keeps account immutable, and does not call rule-preview', () => {
    renderEditForm();

    expect(screen.getByDisplayValue('Checking')).toBeTruthy();
    expect(screen.getByLabelText('Account').disabled).toBe(true);
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Origin')).toBeNull();
    expect(screen.queryByLabelText('Transaction Ingestion')).toBeNull();
    expect(screen.queryByLabelText('External Reference')).toBeNull();
    expect(screen.queryByLabelText('Financial Subscription')).toBeNull();
    expect(screen.queryByText('Step 1: Transaction details')).toBeNull();
    expect(mockAxiosPost).not.toHaveBeenCalled();
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
        flow: 'OUT',
      }),
    );
    expect(payload.transactionDate.format('YYYY-MM-DD')).toBe('2026-07-14');
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
