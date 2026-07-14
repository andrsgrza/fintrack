import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enTransactionRuleCondition from 'app/../i18n/en/transactionRuleCondition.json';
import enTransactionRuleField from 'app/../i18n/en/transactionRuleField.json';
import enRuleOperator from 'app/../i18n/en/ruleOperator.json';
import { TransactionRuleConditionUpdate } from './transaction-rule-condition-update';

const mockDispatch = jest.fn();
const mockGetEntity = jest.fn(id => ({ type: 'transactionRuleCondition/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'transactionRuleCondition/reset' }));
const mockGetTransactionRules = jest.fn(() => ({ type: 'transactionRule/getEntities' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./transaction-rule-condition.reducer', () => ({
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
  createEntity: entity => ({ type: 'transactionRuleCondition/createEntity', payload: entity }),
  partialUpdateEntity: entity => ({ type: 'transactionRuleCondition/partialUpdateEntity', payload: entity }),
}));

jest.mock('app/entities/transaction-rule/transaction-rule.reducer', () => ({
  getEntities: () => mockGetTransactionRules(),
}));

const baseState = {
  transactionRule: {
    entities: [
      {
        id: 1,
        name: 'Coffee rule',
      },
    ],
  },
  transactionRuleCondition: {
    entity: {
      id: 5,
      field: 'DESCRIPTION',
      operator: 'CONTAINS',
      value: 'Coffee',
      caseSensitive: false,
      position: 0,
      transactionRule: {
        id: 1,
        name: 'Coffee rule',
      },
    },
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enTransactionRuleCondition);
  TranslatorContext.registerTranslations('en', enTransactionRuleField);
  TranslatorContext.registerTranslations('en', enRuleOperator);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = (initialEntry = '/transaction-rule-condition/new') => {
  mockState = {
    ...baseState,
    transactionRuleCondition: {
      ...baseState.transactionRuleCondition,
      entity: {},
    },
  };

  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/transaction-rule-condition/new" element={<TransactionRuleConditionUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/transaction-rule-condition/5/edit']}>
      <Routes>
        <Route path="/transaction-rule-condition/:id/edit" element={<TransactionRuleConditionUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('TransactionRuleCondition UX', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it('renders dynamic create title and preselects parent from query param', () => {
    renderCreateForm('/transaction-rule-condition/new?transactionRuleId=1');

    expect(screen.getByRole('heading', { name: 'Create Transaction Rule Condition' })).toBeTruthy();
    expect((screen.getByLabelText('Transaction Rule') as HTMLSelectElement).value).toBe('1');
  });

  it('renders dynamic edit title and disables parent in edit mode', () => {
    renderEditForm();

    expect(screen.getByRole('heading', { name: 'Edit Transaction Rule Condition' })).toBeTruthy();
    expect((screen.getByLabelText('Transaction Rule') as HTMLSelectElement).disabled).toBe(true);
  });

  it('shows second value only for BETWEEN operator', () => {
    renderCreateForm();

    expect(screen.queryByLabelText('Second Value')).toBeNull();

    fireEvent.change(screen.getByLabelText('Operator'), { target: { value: 'BETWEEN' } });

    expect(screen.getByLabelText('Second Value')).toBeTruthy();
  });

  it('shows case sensitive only for textual fields', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Case Sensitive')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'AMOUNT' } });

    expect(screen.queryByLabelText('Case Sensitive')).toBeNull();
  });
});
