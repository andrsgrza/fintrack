import React from 'react';
import axios from 'axios';
import { render, screen, waitFor } from '@testing-library/react';
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

const renderEditForm = () => {
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
    registerTranslations();
  });

  it('renders create title, hides timestamps, and hides active in create mode', () => {
    renderCreateForm();

    expect(screen.getByRole('heading', { name: 'Create Transaction Rule' })).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Active')).toBeNull();
  });

  it('renders edit title, active field, and related conditions section in edit mode', () => {
    mockAxiosGet.mockImplementation(() => new Promise(() => {}));

    renderEditForm();

    expect(screen.getByRole('heading', { name: 'Edit Transaction Rule' })).toBeTruthy();
    expect(screen.getByLabelText('Active')).toBeTruthy();
    expect(screen.getByText('Active rules require at least one condition.')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Conditions' })).toBeTruthy();
  });

  it('loads and displays related conditions on detail', async () => {
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

    expect(await screen.findByText('Coffee')).toBeTruthy();
    expect(screen.getAllByText('Description').length).toBeGreaterThan(0);
    expect(screen.getByText('Contains')).toBeTruthy();
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
