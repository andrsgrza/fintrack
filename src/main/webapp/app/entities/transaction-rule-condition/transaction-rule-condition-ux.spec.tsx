import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enTransactionRuleCondition from 'app/../i18n/en/transactionRuleCondition.json';
import enTransactionRuleField from 'app/../i18n/en/transactionRuleField.json';
import enRuleOperator from 'app/../i18n/en/ruleOperator.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import enTransactionOrigin from 'app/../i18n/en/transactionOrigin.json';
import { TransactionRuleConditionUpdate } from './transaction-rule-condition-update';

const mockDispatch = jest.fn();
const mockGetEntity = jest.fn(id => ({ type: 'transactionRuleCondition/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'transactionRuleCondition/reset' }));
const mockCreateEntity = jest.fn(entity => ({ type: 'transactionRuleCondition/createEntity', payload: entity }));
const mockPartialUpdateEntity = jest.fn(entity => ({ type: 'transactionRuleCondition/partialUpdateEntity', payload: entity }));
const mockGetTransactionRules = jest.fn(() => ({ type: 'transactionRule/getEntities' }));
const mockGetFinancialAccounts = jest.fn(() => ({ type: 'financialAccount/getEntities' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./transaction-rule-condition.reducer', () => ({
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
}));

jest.mock('app/entities/transaction-rule/transaction-rule.reducer', () => ({
  getEntities: () => mockGetTransactionRules(),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: () => mockGetFinancialAccounts(),
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
  financialAccount: {
    entities: [
      {
        id: 2,
        name: 'Checking account',
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
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.registerTranslations('en', enTransactionOrigin);
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

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'AMOUNT' } });
    fireEvent.change(screen.getByLabelText('Operator'), { target: { value: 'BETWEEN' } });

    expect(screen.getByLabelText('Second Value')).toBeTruthy();
  });

  it('shows case sensitive only for textual fields', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Case Sensitive')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'AMOUNT' } });

    expect(screen.queryByLabelText('Case Sensitive')).toBeNull();
  });

  it('filters operators for DESCRIPTION', () => {
    renderCreateForm();

    const operatorSelect = screen.getByLabelText('Operator');

    expect(within(operatorSelect).getByRole('option', { name: 'Contains' })).toBeTruthy();
    expect(within(operatorSelect).queryByRole('option', { name: 'Greater than' })).toBeNull();
    expect(within(operatorSelect).queryByRole('option', { name: 'Before' })).toBeNull();
  });

  it('filters operators for AMOUNT', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'AMOUNT' } });

    const operatorSelect = screen.getByLabelText('Operator');
    expect(within(operatorSelect).getByRole('option', { name: 'Greater than' })).toBeTruthy();
    expect(within(operatorSelect).getByRole('option', { name: 'Between' })).toBeTruthy();
    expect(within(operatorSelect).queryByRole('option', { name: 'Contains' })).toBeNull();
  });

  it('filters operators for FLOW and renders enum select for equals', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'FLOW' } });

    const operatorSelect = screen.getByLabelText('Operator');
    expect(within(operatorSelect).getByRole('option', { name: 'Equals' })).toBeTruthy();
    expect(within(operatorSelect).queryByRole('option', { name: 'Contains' })).toBeNull();
    expect(within(screen.getByLabelText('Value')).getByRole('option', { name: 'Income' })).toBeTruthy();
    expect(within(screen.getByLabelText('Value')).getByRole('option', { name: 'Expense' })).toBeTruthy();
  });

  it('filters operators for TRANSACTION_DATE and renders date input', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'TRANSACTION_DATE' } });

    const operatorSelect = screen.getByLabelText('Operator');
    expect(within(operatorSelect).getByRole('option', { name: 'Before' })).toBeTruthy();
    expect(within(operatorSelect).getByRole('option', { name: 'Between' })).toBeTruthy();
    expect((screen.getByLabelText('Value') as HTMLInputElement).type).toBe('date');
  });

  it('filters operators for ACCOUNT and renders account selector for equals', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'ACCOUNT' } });

    const operatorSelect = screen.getByLabelText('Operator');
    expect(within(operatorSelect).getByRole('option', { name: 'Equals' })).toBeTruthy();
    expect(within(operatorSelect).queryByRole('option', { name: 'Contains' })).toBeNull();
    expect(within(screen.getByLabelText('Value')).getByRole('option', { name: 'Checking account' })).toBeTruthy();
    expect(mockGetFinancialAccounts).toHaveBeenCalled();
  });

  it('renders ORIGIN enum select for equals', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'ORIGIN' } });

    expect(within(screen.getByLabelText('Value')).getByRole('option', { name: 'MANUAL' })).toBeTruthy();
    expect(within(screen.getByLabelText('Value')).getByRole('option', { name: 'FILE_IMPORT' })).toBeTruthy();
    expect(within(screen.getByLabelText('Value')).getByRole('option', { name: 'API' })).toBeTruthy();
  });

  it('renders IN and NOT_IN as text input with helper text', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'AMOUNT' } });
    fireEvent.change(screen.getByLabelText('Operator'), { target: { value: 'IN' } });

    expect((screen.getByLabelText('Value') as HTMLInputElement).type).toBe('text');
    expect(screen.getByText('Use comma-separated decimal values, e.g. 100.00,250.50')).toBeTruthy();
  });

  it('changing field resets incompatible operator and clears values', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Value'), { target: { value: 'Coffee' } });
    fireEvent.change(screen.getByLabelText('Operator'), { target: { value: 'CONTAINS' } });
    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'FLOW' } });

    expect((screen.getByLabelText('Operator') as HTMLSelectElement).value).toBe('EQUALS');
    expect((screen.getByLabelText('Value') as HTMLSelectElement).value).toBe('');
  });

  it('changing away from BETWEEN hides and clears second value', () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'AMOUNT' } });
    fireEvent.change(screen.getByLabelText('Operator'), { target: { value: 'BETWEEN' } });
    fireEvent.change(screen.getByLabelText('Second Value'), { target: { value: '200' } });
    fireEvent.change(screen.getByLabelText('Operator'), { target: { value: 'EQUALS' } });

    expect(screen.queryByLabelText('Second Value')).toBeNull();
  });

  it('submits account selector value as string and caseSensitive false when hidden', async () => {
    renderCreateForm('/transaction-rule-condition/new?transactionRuleId=1');

    fireEvent.change(screen.getByLabelText('Field'), { target: { value: 'ACCOUNT' } });
    fireEvent.change(screen.getByLabelText('Value'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Position'), { target: { value: '0' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(mockCreateEntity).toHaveBeenCalledWith(
        expect.objectContaining({
          field: 'ACCOUNT',
          operator: 'EQUALS',
          value: '2',
          caseSensitive: false,
        }),
      ),
    );
  });
});
