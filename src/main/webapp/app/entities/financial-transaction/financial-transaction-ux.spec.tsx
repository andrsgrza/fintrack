import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';

import enAccountType from 'app/../i18n/en/accountType.json';
import enCategory from 'app/../i18n/en/category.json';
import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTag from 'app/../i18n/en/tag.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';
import { getSelectableCategories } from 'app/entities/category/category-selectable.service';
import { getSelectableTags } from 'app/entities/tag/tag-selectable.service';
import { FinancialTransaction } from './financial-transaction';
import { FinancialTransactionDeleteDialog } from './financial-transaction-delete-dialog';
import { FinancialTransactionDetail } from './financial-transaction-detail';
import { FinancialTransactionUpdate } from './financial-transaction-update';

const mockDispatch = jest.fn();
const mockGetEntities = jest.fn(params => ({ type: 'financialTransaction/getEntities', payload: params }));
const mockPartialUpdateEntity = jest.fn(entity => ({
  type: 'financialTransaction/partialUpdateEntity',
  payload: { data: entity },
}));
const mockGetEntity = jest.fn(id => ({ type: 'financialTransaction/getEntity', payload: id }));
const mockDeleteEntity = jest.fn(id => ({ type: 'financialTransaction/deleteEntity', payload: id }));
const mockGetSelectableCategories = getSelectableCategories as jest.Mock;
const mockGetSelectableTags = getSelectableTags as jest.Mock;
const mockGetSelectableFinancialAccounts = getSelectableFinancialAccounts as jest.Mock;
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./financial-transaction.reducer', () => ({
  getEntities: params => mockGetEntities(params),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
  getEntity: id => mockGetEntity(id),
  deleteEntity: id => mockDeleteEntity(id),
}));

jest.mock('app/entities/category/category-selectable.service', () => ({
  getSelectableCategories: jest.fn(),
}));

jest.mock('app/entities/financial-account/financial-account-selectable.service', () => ({
  getSelectableFinancialAccounts: jest.fn(),
}));

jest.mock('app/entities/tag/tag-selectable.service', () => ({
  getSelectableTags: jest.fn(),
}));

const accounts = [
  {
    id: 1,
    name: 'Checking',
    accountType: 'DEBIT',
    currency: 'MXN',
    lastFourDigits: '1234',
    color: '#2463A5',
    active: false,
  },
];
const categories = [
  {
    id: 10,
    name: 'Groceries',
    parentCategoryName: 'Home',
    parentCategoryId: 9,
    categoryType: 'EXPENSE',
    color: '#2463A5',
    active: false,
  },
];
const tags = [{ id: 20, name: 'Personal', color: '#E31B23', active: false }];

const existingTransaction = {
  id: 2501,
  transactionDate: '2026-07-13',
  postingDate: '2026-07-14',
  description: 'Bus fare',
  amount: 3,
  flow: 'OUT',
  origin: 'MANUAL',
  externalReference: 'Receipt-48',
  notes: 'Some note',
  createdAt: '2026-07-13T16:00:00Z',
  updatedAt: '2026-07-13T16:00:00Z',
  account: accounts[0],
  category: { ...categories[0], parentCategory: { id: 9, name: 'Home' } },
  transactionIngestion: { id: 99 },
  tags: [tags[0]],
};

const baseState = {
  financialTransaction: {
    entity: existingTransaction,
    entities: [existingTransaction],
    loading: false,
    totalItems: 60,
    updating: false,
    updateSuccess: false,
    errorMessage: null,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.registerTranslations('en', enFinancialAccount);
  TranslatorContext.registerTranslations('en', enAccountType);
  TranslatorContext.registerTranslations('en', enCategory);
  TranslatorContext.registerTranslations('en', enTag);
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

const LocationDisplay = () => {
  const location = useLocation();
  return <output data-testid="financialTransactionLocation">{`${location.pathname}${location.search}`}</output>;
};

const renderList = (initialEntry = '/financial-transaction') => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/financial-transaction" element={<FinancialTransaction />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('FinancialTransaction posted transaction UX', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
    mockGetEntities.mockClear();
    mockPartialUpdateEntity.mockClear();
    mockGetEntity.mockClear();
    mockDeleteEntity.mockClear();
    mockGetSelectableFinancialAccounts.mockClear();
    mockGetSelectableCategories.mockResolvedValue(categories);
    mockGetSelectableTags.mockResolvedValue(tags);
    mockGetSelectableFinancialAccounts.mockResolvedValue(accounts);
  });

  it('renders a product transaction list without generated technical columns', () => {
    renderList();

    expect(screen.getByRole('link', { name: /drafts/i }).getAttribute('href')).toBe('/financial-transaction/drafts');
    expect(screen.getByRole('link', { name: /new transaction/i }).getAttribute('href')).toBe('/financial-transaction/new');
    expect(screen.getByText('Bus fare')).toBeTruthy();
    expect(screen.getByText('Groceries')).toBeTruthy();
    expect(screen.getByText('Personal')).toBeTruthy();
    expect(screen.getByText('−3.00 MXN')).toBeTruthy();
    expect(document.querySelector('[data-cy="financialTransactionProductList"] [data-cy="transactionAmount"]')?.textContent).toContain(
      'Expense',
    );
    const category = document.querySelector('[data-cy="transactionCategory"]');
    expect(category.className).toContain('rounded-1');
    expect(category.getAttribute('data-color-treatment')).toBe('category');
    const tagChip = screen.getByText('Personal');
    expect(tagChip.className).toContain('rounded-pill');
    expect(tagChip.getAttribute('data-color-treatment')).toBe('tag');
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('MANUAL')).toBeNull();
    expect(screen.queryByText('2501')).toBeNull();
  });

  it('loads product filter controls from the URL and sends generated criteria to the paginated server query', async () => {
    renderList(
      '/financial-transaction?page=2&sort=transactionDate,asc&description.contains=Bus%20fare&transactionDate.greaterThanOrEqual=2026-07-01&transactionDate.lessThanOrEqual=2026-07-31&accountId.equals=1&flow.equals=OUT&categoryId.equals=10&tagsId.in=20',
    );

    await waitFor(() =>
      expect(mockGetEntities).toHaveBeenLastCalledWith(
        expect.objectContaining({
          page: 1,
          sort: 'transactionDate,asc',
          query: expect.stringContaining('description.contains=Bus+fare'),
        }),
      ),
    );
    const query = mockGetEntities.mock.calls.at(-1)?.[0].query;
    expect(query).toContain('transactionDate.greaterThanOrEqual=2026-07-01');
    expect(query).toContain('transactionDate.lessThanOrEqual=2026-07-31');
    expect(query).toContain('accountId.equals=1');
    expect(query).toContain('flow.equals=OUT');
    expect(query).toContain('categoryId.equals=10');
    expect(query).toContain('tagsId.in=20');
    expect(screen.getByLabelText('Search description').value).toBe('Bus fare');
    expect(screen.getByLabelText('Transaction date from').value).toBe('2026-07-01');
    expect(screen.getByLabelText('Account').value).toBe('1');
    expect(screen.getByLabelText('Type').value).toBe('OUT');
    expect(screen.getByLabelText('Category').value).toBe('10');
    await waitFor(() => expect(screen.getByTestId('financialTransactionFilterCategoryIdentity')).toBeTruthy());
    expect(screen.getByTestId('financialTransactionFilterTagIdentity').textContent).toContain('Personal');
    expect(screen.queryByText('Origin')).toBeNull();
    expect(screen.queryByText('Ingestion')).toBeNull();
  });

  it('resets to page one when applying or clearing filters and retains filters when sorting', async () => {
    mockState = baseState;
    render(
      <MemoryRouter initialEntries={['/financial-transaction?page=3&sort=id,desc']}>
        <Routes>
          <Route
            path="/financial-transaction"
            element={
              <>
                <FinancialTransaction />
                <LocationDisplay />
              </>
            }
          />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText('Search description'), { target: { value: 'Bus' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() =>
      expect(screen.getByTestId('financialTransactionLocation').textContent).toContain('?description.contains=Bus&page=1&sort=id%2Cdesc'),
    );

    fireEvent.click(screen.getByRole('button', { name: /sort by transaction date/i }));
    await waitFor(() =>
      expect(screen.getByTestId('financialTransactionLocation').textContent).toContain(
        '?description.contains=Bus&page=1&sort=transactionDate%2Casc',
      ),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() =>
      expect(screen.getByTestId('financialTransactionLocation').textContent).toBe(
        '/financial-transaction?page=1&sort=transactionDate%2Casc',
      ),
    );
  });

  it('keeps compact edit and overflow delete actions for a posted transaction', () => {
    renderList();

    expect(screen.getByRole('link', { name: /edit/i }).getAttribute('href')).toContain('/financial-transaction/2501/edit');
    fireEvent.click(screen.getByRole('button', { name: /more transaction actions/i }));
    expect(screen.getByRole('menuitem', { name: /delete/i }).getAttribute('href')).toContain('/financial-transaction/2501/delete');
  });

  it('uses a product empty state with a new transaction call to action', () => {
    mockState = {
      ...baseState,
      financialTransaction: {
        ...baseState.financialTransaction,
        entities: [],
        totalItems: 0,
      },
    };

    render(
      <MemoryRouter initialEntries={['/financial-transaction']}>
        <Routes>
          <Route path="/financial-transaction" element={<FinancialTransaction />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('No transactions yet')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: /new transaction/i })[1].getAttribute('href')).toBe('/financial-transaction/new');
  });

  it('renders a posted edit form with product sections and immutable account context', async () => {
    renderEditForm();

    await waitFor(() => expect(mockGetSelectableCategories).toHaveBeenCalledWith([10]));
    expect(screen.getByRole('heading', { name: 'Edit transaction' })).toBeTruthy();
    expect(screen.getAllByText('Checking · Debit account · MXN · ••••1234 · Inactive')).toHaveLength(2);
    expect(screen.queryByRole('combobox', { name: 'Account' })).toBeNull();
    expect(screen.getByTestId('financialTransactionEditTransactionSection')).toBeTruthy();
    expect(screen.getByTestId('financialTransactionEditClassificationSection')).toBeTruthy();
    expect(screen.getByTestId('financialTransactionEditOptionalDetails')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Expense' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.queryByLabelText('Use the same posting date')).toBeNull();
    await waitFor(() => expect(screen.getByLabelText('Category').value).toBe('10'));
    expect(screen.getByTestId('transactionCategoryPath').textContent).toContain('Home › Groceries');
    expect(screen.getByText('Personal')).toBeTruthy();
    expect(screen.getByLabelText('External Reference').value).toBe('Receipt-48');
    expect(screen.getByLabelText('Notes').value).toBe('Some note');
    expect(screen.queryByLabelText('ID')).toBeNull();
    expect(screen.queryByLabelText('Origin')).toBeNull();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByText('Step 1: Transaction details')).toBeNull();
  });

  it('edits a posted transaction with PATCH only and no immutable/server-owned fields', async () => {
    renderEditForm();

    await waitFor(() => expect(mockGetSelectableTags).toHaveBeenCalledWith([20]));
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Updated bus fare' } });
    fireEvent.click(screen.getByRole('button', { name: 'Income' }));
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '14.5' } });
    fireEvent.change(screen.getByLabelText('Transaction date'), { target: { value: '2026-07-15' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(mockPartialUpdateEntity).toHaveBeenCalled());
    const payload = mockPartialUpdateEntity.mock.calls[0][0];
    expect(payload).toEqual(
      expect.objectContaining({
        id: 2501,
        description: 'Updated bus fare',
        amount: 14.5,
        flow: 'IN',
      }),
    );
    expect(payload.transactionDate.format('YYYY-MM-DD')).toBe('2026-07-15');
    expect(payload).not.toHaveProperty('account');
    expect(payload).not.toHaveProperty('origin');
    expect(payload).not.toHaveProperty('transactionIngestion');
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
  });

  it('renders a posted detail hero, localized origin, product classification, and overflow delete', () => {
    renderDetail();

    expect(screen.getByRole('heading', { name: 'Bus fare' })).toBeTruthy();
    expect(screen.getByText('−3.00 MXN')).toBeTruthy();
    expect(document.querySelectorAll('[data-cy="transactionFlowBadge"]')).toHaveLength(1);
    expect(document.querySelector('[data-cy="financialTransactionDetailAccountContext"]')?.textContent).not.toContain('Expense');
    expect(screen.getAllByText('Checking · Debit account · MXN · ••••1234 · Inactive').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId('financialTransactionDetailClassification')).toBeTruthy();
    expect(screen.getByTestId('transactionCategoryPath').textContent).toContain('Home › Groceries');
    expect(screen.getByText('Personal')).toBeTruthy();
    expect(screen.getByText('Manual')).toBeTruthy();
    expect(screen.getByText('Receipt-48')).toBeTruthy();
    expect(screen.getByTestId('financialTransactionDetailNotes').textContent).toContain('Some note');
    expect(screen.getByRole('link', { name: /edit/i }).getAttribute('href')).toBe('/financial-transaction/2501/edit');
    fireEvent.click(screen.getByRole('button', { name: /more transaction actions/i }));
    expect(screen.getByRole('menuitem', { name: /delete/i }).getAttribute('href')).toBe('/financial-transaction/2501/delete');
    expect(document.querySelector('dl')).toBeNull();
    expect(screen.queryByText('MANUAL')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('Transaction Ingestion')).toBeNull();
    expect(screen.queryByText('2501')).toBeNull();
  });

  it('uses a product delete confirmation without exposing a transaction ID', () => {
    mockState = baseState;

    render(
      <MemoryRouter initialEntries={['/financial-transaction/2501/delete']}>
        <Routes>
          <Route path="/financial-transaction/:id/delete" element={<FinancialTransactionDeleteDialog />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole('heading', { name: 'Delete transaction' })).toBeTruthy();
    expect(screen.getByText('Are you sure you want to delete this transaction?')).toBeTruthy();
    expect(screen.queryByText('2501')).toBeNull();
  });

  it('omits empty optional detail values and the notes section', () => {
    mockState = {
      ...baseState,
      financialTransaction: {
        ...baseState.financialTransaction,
        entity: { ...existingTransaction, externalReference: null, notes: null },
      },
    };

    render(
      <MemoryRouter initialEntries={['/financial-transaction/2501']}>
        <Routes>
          <Route path="/financial-transaction/:id" element={<FinancialTransactionDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.queryByText('Receipt-48')).toBeNull();
    expect(screen.queryByTestId('financialTransactionDetailNotes')).toBeNull();
  });
});
