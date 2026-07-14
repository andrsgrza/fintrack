import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enTransactionRule from 'app/../i18n/en/transactionRule.json';
import enTransactionRuleCondition from 'app/../i18n/en/transactionRuleCondition.json';
import enTransactionRuleField from 'app/../i18n/en/transactionRuleField.json';
import enRuleOperator from 'app/../i18n/en/ruleOperator.json';
import { TransactionRuleDetail } from './transaction-rule-detail';
import { TransactionRuleUpdate } from './transaction-rule-update';

jest.mock('axios');

const mockAxiosGet = axios.get as jest.Mock;
const mockAxiosPost = axios.post as jest.Mock;
const mockAxiosPatch = axios.patch as jest.Mock;
const mockAxiosDelete = axios.delete as jest.Mock;
const mockDispatch = jest.fn();
const mockGetEntity = jest.fn(id => ({ type: 'transactionRule/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'transactionRule/reset' }));
const mockGetCategories = jest.fn(() => ({ type: 'category/getEntities' }));
const mockGetFinancialSubscriptions = jest.fn(() => ({ type: 'financialSubscription/getEntities' }));
const mockGetTags = jest.fn(() => ({ type: 'tag/getEntities' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./transaction-rule.reducer', () => ({
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
  createEntity: entity => ({ type: 'transactionRule/createEntity', payload: entity }),
  partialUpdateEntity: entity => ({ type: 'transactionRule/partialUpdateEntity', payload: entity }),
}));

jest.mock('app/entities/category/category.reducer', () => ({
  getEntities: () => mockGetCategories(),
}));

jest.mock('app/entities/financial-subscription/financial-subscription.reducer', () => ({
  getEntities: () => mockGetFinancialSubscriptions(),
}));

jest.mock('app/entities/tag/tag.reducer', () => ({
  getEntities: () => mockGetTags(),
}));

const baseRule = {
  id: 1,
  name: 'Coffee rule',
  description: 'Coffee shops',
  priority: 10,
  conditionLogic: 'ALL',
  resultingDescription: 'Coffee',
  active: false,
  resultingTags: [],
};

const baseState = {
  category: { entities: [] },
  financialSubscription: { entities: [] },
  tag: { entities: [] },
  financialAccount: { entities: [{ id: 2, name: 'Checking account' }] },
  transactionRule: {
    entity: baseRule,
    entities: [],
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enTransactionRule);
  TranslatorContext.registerTranslations('en', enTransactionRuleCondition);
  TranslatorContext.registerTranslations('en', enTransactionRuleField);
  TranslatorContext.registerTranslations('en', enRuleOperator);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = {
    ...baseState,
    transactionRule: { ...baseState.transactionRule, entity: {} },
  };

  return render(
    <MemoryRouter initialEntries={['/transaction-rule/new']}>
      <Routes>
        <Route path="/transaction-rule/new" element={<TransactionRuleUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = (conditionsResult: any = []) => {
  mockAxiosGet.mockImplementation((url: string) => {
    if (url === 'api/transaction-rules/1/conditions') {
      if (conditionsResult === 'error') {
        return Promise.reject(new Error('conditions failed'));
      }
      return Promise.resolve({ data: conditionsResult });
    }
    return new Promise(() => {});
  });
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/transaction-rule/1/edit']}>
      <Routes>
        <Route path="/transaction-rule/:id/edit" element={<TransactionRuleUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = (conditionsResult: any) => {
  mockAxiosGet.mockImplementation((url: string) => {
    if (url === 'api/transaction-rules/1/conditions') {
      if (conditionsResult === 'error') {
        return Promise.reject(new Error('conditions failed'));
      }
      return Promise.resolve({ data: conditionsResult });
    }
    return new Promise(() => {});
  });
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/transaction-rule/1']}>
      <Routes>
        <Route path="/transaction-rule/:id" element={<TransactionRuleDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('TransactionRule UX', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAxiosGet.mockReset();
    mockAxiosPost.mockReset();
    mockAxiosPatch.mockReset();
    mockAxiosDelete.mockReset();
    registerTranslations();
  });

  it('renders create title, hides timestamps, and hides active in create mode', () => {
    renderCreateForm();

    expect(screen.getByRole('heading', { name: 'Create Transaction Rule' })).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Active')).toBeNull();
  });

  it('renders edit title, active field, and manage conditions link without embedded editor', async () => {
    renderEditForm();

    expect(screen.getByRole('heading', { name: 'Edit Transaction Rule' })).toBeTruthy();
    expect(screen.getByLabelText('Active')).toBeTruthy();
    expect(screen.getByText('Active rules require at least one condition.')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Conditions' })).toBeNull();
    expect(screen.queryByRole('button', { name: /add condition/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /delete condition/i })).toBeNull();
    expect(screen.getByRole('link', { name: /manage conditions/i }).getAttribute('href')).toBe('/transaction-rule/1');
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules/1/conditions'));
  });

  it('loads background condition count in edit and disables active when empty', async () => {
    renderEditForm([]);

    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules/1/conditions'));
    expect((screen.getByLabelText('Active') as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByText('Add at least one condition before activating this rule.')).toBeTruthy();
  });

  it('enables active in edit when background condition count has at least one condition', async () => {
    renderEditForm([
      {
        id: 11,
        position: 1,
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'Coffee',
        caseSensitive: false,
      },
    ]);

    await waitFor(() => expect((screen.getByLabelText('Active') as HTMLInputElement).disabled).toBe(false));
  });

  it('opens embedded add form on detail without parent selector and posts condition with fixed rule id', async () => {
    mockAxiosPost.mockResolvedValue({ data: { id: 12 } });
    renderDetail([]);

    await screen.findByText('No conditions yet.');
    fireEvent.click(screen.getByRole('button', { name: /add condition/i }));

    expect(screen.getByRole('heading', { name: 'Add condition' })).toBeTruthy();
    expect(screen.queryByLabelText('Transaction Rule')).toBeNull();
    expect(screen.queryByLabelText('Position')).toBeNull();

    fireEvent.change(screen.getByLabelText('Value'), { target: { value: 'Coffee' } });
    fireEvent.click(screen.getByRole('button', { name: /save condition/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/transaction-rule-conditions',
        expect.objectContaining({
          field: 'DESCRIPTION',
          operator: 'EQUALS',
          value: 'Coffee',
          secondValue: null,
          caseSensitive: false,
          transactionRule: { id: 1 },
        }),
      ),
    );
    expect(mockAxiosPost.mock.calls[0][1]).not.toHaveProperty('position');
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledTimes(2));
  });

  it('opens embedded edit form on detail without parent selector and patches editable condition fields only', async () => {
    mockAxiosPatch.mockResolvedValue({ data: { id: 11 } });
    renderDetail([
      {
        id: 11,
        position: 1,
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'Coffee',
        caseSensitive: false,
        transactionRule: { id: 1 },
      },
    ]);

    await screen.findByText('Description contains "Coffee"');
    fireEvent.click(screen.getByRole('button', { name: /edit/i }));

    expect(screen.getByRole('heading', { name: 'Edit condition' })).toBeTruthy();
    expect(screen.queryByLabelText('Transaction Rule')).toBeNull();
    expect(screen.queryByLabelText('Position')).toBeNull();
    fireEvent.change(screen.getByLabelText('Value'), { target: { value: 'Tea' } });
    fireEvent.click(screen.getByRole('button', { name: /update condition/i }));

    await waitFor(() =>
      expect(mockAxiosPatch).toHaveBeenCalledWith(
        'api/transaction-rule-conditions/11',
        expect.not.objectContaining({
          transactionRule: expect.anything(),
        }),
      ),
    );
    expect(mockAxiosPatch.mock.calls[0][1]).not.toHaveProperty('position');
    expect(mockAxiosPatch.mock.calls[0][1]).toEqual(
      expect.objectContaining({
        id: 11,
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'Tea',
      }),
    );
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledTimes(2));
  });

  it('deletes a condition from detail after confirmation and refreshes the list', async () => {
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    mockAxiosDelete.mockResolvedValue({ data: {} });
    renderDetail([
      {
        id: 11,
        position: 1,
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'Coffee',
        caseSensitive: false,
      },
    ]);

    await screen.findByText('Description contains "Coffee"');
    fireEvent.click(screen.getByRole('button', { name: /delete condition/i }));

    await waitFor(() => expect(mockAxiosDelete).toHaveBeenCalledWith('api/transaction-rule-conditions/11'));
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledTimes(2));
  });

  it('shows condition delete failure without breaking detail page', async () => {
    jest.spyOn(window, 'confirm').mockReturnValue(true);
    mockAxiosDelete.mockRejectedValue(new Error('delete failed'));
    renderDetail([
      {
        id: 11,
        position: 1,
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'Coffee',
        caseSensitive: false,
      },
    ]);

    await screen.findByText('Coffee');
    fireEvent.click(screen.getByRole('button', { name: /delete condition/i }));

    expect(await screen.findByText('Condition could not be deleted.')).toBeTruthy();
    expect(screen.getByText('Coffee rule')).toBeTruthy();
  });

  it('handles edit background condition load failure without breaking rule edit page', async () => {
    renderEditForm('error');

    expect(screen.getByRole('heading', { name: 'Edit Transaction Rule' })).toBeTruthy();
    await waitFor(() => expect((screen.getByLabelText('Active') as HTMLInputElement).disabled).toBe(true));
  });

  it('loads and displays normalized related condition summaries on detail', async () => {
    renderDetail([
      {
        id: 11,
        position: 1,
        field: 'DESCRIPTION',
        operator: 'CONTAINS',
        value: 'Coffee',
        caseSensitive: true,
      },
      {
        id: 12,
        position: 2,
        field: 'AMOUNT',
        operator: 'BETWEEN',
        value: '20',
        secondValue: '500',
        caseSensitive: false,
      },
      {
        id: 13,
        position: 3,
        field: 'ACCOUNT',
        operator: 'EQUALS',
        value: '2',
        caseSensitive: false,
      },
    ]);

    expect(await screen.findByText('Description contains "Coffee" (case-sensitive)')).toBeTruthy();
    expect(screen.getByText('Amount between 20 and 500')).toBeTruthy();
    expect(screen.getByText('Account equals Checking account')).toBeTruthy();
    expect(screen.queryByText('Value')).toBeNull();
    expect(screen.queryByText('Second Value')).toBeNull();
    expect(screen.queryByText('Case Sensitive')).toBeNull();
    expect(screen.getByText('Condition')).toBeTruthy();
    expect(screen.queryByText('Position')).toBeNull();
    expect(screen.queryByRole('button', { name: /view/i })).toBeNull();
    expect(screen.getAllByRole('button', { name: /edit/i })).toHaveLength(3);
    expect(screen.getAllByRole('button', { name: /delete condition/i })).toHaveLength(3);
  });

  it('shows empty related conditions state on detail', async () => {
    renderDetail([]);

    expect(await screen.findByText('No conditions yet.')).toBeTruthy();
  });

  it('handles related condition load failure without breaking detail', async () => {
    renderDetail('error');

    await waitFor(() => expect(screen.getByText('Conditions are not available.')).toBeTruthy());
    expect(screen.getByText('Coffee rule')).toBeTruthy();
  });
});
