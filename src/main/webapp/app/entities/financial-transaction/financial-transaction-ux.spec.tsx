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
const mockAxiosPost = axios.post as jest.Mock;
let mockState;

jest.mock('axios');

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

const selectMultipleValues = (label: string, values: string[]) => {
  const select = screen.getByLabelText(label) as HTMLSelectElement;
  Array.from(select.options).forEach(option => {
    option.selected = values.includes(option.value);
  });
  fireEvent.change(select);
};

const emptyPreview = {
  suggestedCategory: null,
  suggestedTags: [],
  conflicts: [],
  skippedOutputs: [],
  matchedRules: [],
  hasSuggestions: false,
  hasConflicts: false,
};

const fillStepOne = () => {
  fireEvent.change(screen.getByLabelText('Account'), { target: { value: '1' } });
  fireEvent.change(screen.getByLabelText('Transaction date'), { target: { value: '2026-07-13' } });
  fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Bus fare' } });
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '3' } });
  fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'OUT' } });
};

const goToStepTwo = async (preview = emptyPreview) => {
  mockAxiosPost.mockResolvedValueOnce({ data: preview });
  fillStepOne();
  fireEvent.click(screen.getByRole('button', { name: /next: categorization/i }));
  await waitFor(() => expect(mockAxiosPost).toHaveBeenCalled());
  await screen.findByText('Step 2: Categorization');
};

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
    mockAxiosPost.mockReset();
  });

  it('create mode starts on Step 1 with transaction details only', () => {
    renderCreateForm();

    expect(screen.getByText('Step 1: Transaction details')).toBeTruthy();
    expect(screen.getByLabelText('Account')).toBeTruthy();
    expect((screen.getByLabelText('Account') as HTMLSelectElement).disabled).toBe(false);
    expect(screen.getByLabelText('External Reference')).toBeTruthy();
    expect(screen.queryByLabelText('Category')).toBeNull();
    expect(screen.queryByLabelText('Tags')).toBeNull();
    expect(screen.getByRole('button', { name: /next: categorization/i })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^save$/i })).toBeNull();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Origin')).toBeNull();
    expect(screen.queryByLabelText('Transaction Ingestion')).toBeNull();
    expect(screen.queryByLabelText('Financial Subscription')).toBeNull();
  });

  it('Next validates required Step 1 fields and does not call preview', async () => {
    renderCreateForm();

    fireEvent.click(screen.getByRole('button', { name: /next: categorization/i }));

    expect(await screen.findByText('Account is required before previewing rules.')).toBeTruthy();
    expect(mockAxiosPost).not.toHaveBeenCalled();
  });

  it('Next calls rule-preview with draft and MANUAL origin', async () => {
    renderCreateForm();

    await goToStepTwo();

    expect(mockAxiosPost).toHaveBeenCalledWith(
      'api/financial-transactions/rule-preview',
      expect.objectContaining({
        accountId: 1,
        description: 'Bus fare',
        amount: 3,
        flow: 'OUT',
        origin: 'MANUAL',
        transactionDate: '2026-07-13',
      }),
    );
  });

  it('preview suggestions prepopulate Step 2 category and tags', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedCategory: { categoryId: 10, categoryName: 'Groceries', conflictsWithCurrentValue: false },
      suggestedTags: [{ tagId: 20, tagName: 'Personal', alreadyPresent: false, duplicateOfEarlierSuggestion: false }],
      matchedRules: [{ ruleId: 1, ruleName: 'Groceries rule' }],
      hasSuggestions: true,
    });

    expect((screen.getByLabelText('Category') as HTMLSelectElement).value).toBe('10');
    expect(Array.from((screen.getByLabelText('Tags') as HTMLSelectElement).selectedOptions).map(option => option.value)).toEqual(['20']);
    expect(screen.getByText('Suggestions from rules were prefilled. You can change them before saving.')).toBeTruthy();
    expect(screen.getByText(/Groceries rule/)).toBeTruthy();
  });

  it('user can change suggested category before save', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedCategory: { categoryId: 10, categoryName: 'Groceries', conflictsWithCurrentValue: false },
      hasSuggestions: true,
    });

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(expect.objectContaining({ category: expect.objectContaining({ id: 12 }) }));
  });

  it('user can clear suggested category before save', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedCategory: { categoryId: 10, categoryName: 'Groceries', conflictsWithCurrentValue: false },
      hasSuggestions: true,
    });

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(expect.objectContaining({ category: null }));
  });

  it('manual category selection is not overwritten when preview is run again', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedCategory: { categoryId: 10, categoryName: 'Groceries', conflictsWithCurrentValue: false },
      hasSuggestions: true,
    });

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: /back to details/i }));
    mockAxiosPost.mockResolvedValueOnce({
      data: {
        ...emptyPreview,
        suggestedCategory: { categoryId: 10, categoryName: 'Groceries', conflictsWithCurrentValue: true },
        hasSuggestions: true,
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /next: categorization/i }));
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledTimes(2));

    expect((screen.getByLabelText('Category') as HTMLSelectElement).value).toBe('12');
  });

  it('user can remove suggested tag before save', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedTags: [{ tagId: 20, tagName: 'Personal', alreadyPresent: false, duplicateOfEarlierSuggestion: false }],
      hasSuggestions: true,
    });

    selectMultipleValues('Tags', []);
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(expect.objectContaining({ tags: [] }));
  });

  it('user can add a manual tag before save', async () => {
    renderCreateForm();

    await goToStepTwo(emptyPreview);

    selectMultipleValues('Tags', ['21']);
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(expect.objectContaining({ tags: [expect.objectContaining({ id: 21 })] }));
  });

  it('explicit selected tag is preserved and suggested new tag is added without duplicates', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedTags: [{ tagId: 20, tagName: 'Personal', alreadyPresent: false, duplicateOfEarlierSuggestion: false }],
      hasSuggestions: true,
    });

    selectMultipleValues('Tags', ['21']);
    fireEvent.click(screen.getByRole('button', { name: /back to details/i }));
    mockAxiosPost.mockResolvedValueOnce({
      data: {
        ...emptyPreview,
        suggestedTags: [
          { tagId: 21, tagName: 'Important', alreadyPresent: true, duplicateOfEarlierSuggestion: false },
          { tagId: 20, tagName: 'Personal', alreadyPresent: false, duplicateOfEarlierSuggestion: false },
        ],
        hasSuggestions: true,
      },
    });
    fireEvent.click(screen.getByRole('button', { name: /next: categorization/i }));
    await waitFor(() => expect(mockAxiosPost).toHaveBeenCalledTimes(2));

    expect(
      Array.from((screen.getByLabelText('Tags') as HTMLSelectElement).selectedOptions)
        .map(option => option.value)
        .sort(),
    ).toEqual(['20', '21']);
  });

  it('no suggestions shows Step 2 empty manual categorization', async () => {
    renderCreateForm();

    await goToStepTwo(emptyPreview);

    expect((screen.getByLabelText('Category') as HTMLSelectElement).value).toBe('');
    expect(Array.from((screen.getByLabelText('Tags') as HTMLSelectElement).selectedOptions)).toHaveLength(0);
    expect(screen.getByText('No rule suggestions found. Choose category/tags manually.')).toBeTruthy();
  });

  it('preview conflict displays non-blocking message and user can still save', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedCategory: {
        categoryId: 10,
        categoryName: 'Groceries',
        conflictsWithCurrentValue: true,
        currentCategoryId: 12,
        currentCategoryName: 'Adjustment',
      },
      conflicts: [{ field: 'CATEGORY', suggestedValueLabel: 'Groceries', currentValueLabel: 'Adjustment' }],
      hasConflicts: true,
    });

    expect(screen.getByText('A rule suggested Groceries, but the current category is Adjustment.')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
  });

  it('preview failure stays on Step 1 and does not create', async () => {
    mockAxiosPost.mockRejectedValueOnce(new Error('preview failed'));
    renderCreateForm();

    fillStepOne();
    fireEvent.click(screen.getByRole('button', { name: /next: categorization/i }));

    expect(await screen.findByText('Rule preview failed. Review the transaction details and try again.')).toBeTruthy();
    expect(screen.getByText('Step 1: Transaction details')).toBeTruthy();
    expect(mockCreateEntity).not.toHaveBeenCalled();
  });

  it('Back from Step 2 preserves Step 1 values', async () => {
    renderCreateForm();

    await goToStepTwo(emptyPreview);
    fireEvent.click(screen.getByRole('button', { name: /back to details/i }));

    expect((screen.getByLabelText('Account') as HTMLSelectElement).value).toBe('1');
    expect((screen.getByLabelText('Transaction date') as HTMLInputElement).value).toBe('2026-07-13');
    expect((screen.getByLabelText('Description') as HTMLInputElement).value).toBe('Bus fare');
    expect((screen.getByLabelText('Amount') as HTMLInputElement).value).toBe('3');
  });

  it('Save from Step 2 calls normal create with final fields and MANUAL origin', async () => {
    renderCreateForm();

    await goToStepTwo({
      ...emptyPreview,
      suggestedCategory: { categoryId: 10, categoryName: 'Groceries', conflictsWithCurrentValue: false },
      suggestedTags: [{ tagId: 20, tagName: 'Personal', alreadyPresent: false, duplicateOfEarlierSuggestion: false }],
      hasSuggestions: true,
    });
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    expect(mockCreateEntity.mock.calls[0][0]).toEqual(
      expect.objectContaining({
        account: expect.objectContaining({ id: 1 }),
        origin: 'MANUAL',
        amount: 3,
        category: expect.objectContaining({ id: 10 }),
        tags: [expect.objectContaining({ id: 20 })],
      }),
    );
    expect(mockCreateEntity.mock.calls[0][0]).not.toHaveProperty('transactionIngestion');
    expect(mockCreateEntity.mock.calls[0][0]).not.toHaveProperty('createdAt');
    expect(mockCreateEntity.mock.calls[0][0]).not.toHaveProperty('updatedAt');
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
