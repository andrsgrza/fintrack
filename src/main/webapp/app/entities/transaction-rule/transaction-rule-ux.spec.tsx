import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';
import dayjs from 'dayjs';

import enTransactionRule from 'app/../i18n/en/transactionRule.json';
import enTransactionRuleCondition from 'app/../i18n/en/transactionRuleCondition.json';
import enTransactionRuleField from 'app/../i18n/en/transactionRuleField.json';
import enRuleOperator from 'app/../i18n/en/ruleOperator.json';
import enRuleConditionLogic from 'app/../i18n/en/ruleConditionLogic.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import { TransactionRule } from './transaction-rule';
import { TransactionRuleDetail } from './transaction-rule-detail';
import { TransactionRuleUpdate } from './transaction-rule-update';

jest.mock('axios');

const mockAxiosGet = axios.get as jest.Mock;
const mockAxiosPost = axios.post as jest.Mock;
const mockAxiosPut = axios.put as jest.Mock;
const mockAxiosPatch = axios.patch as jest.Mock;
const mockAxiosDelete = axios.delete as jest.Mock;
const mockDispatch = jest.fn();
const mockGetEntity = jest.fn(id => ({ type: 'transactionRule/getEntity', payload: id }));
const mockGetEntities = jest.fn(() => ({ type: 'transactionRule/getEntities' }));
const mockReset = jest.fn(() => ({ type: 'transactionRule/reset' }));
const mockCreateEntity = jest.fn(entity => ({ type: 'transactionRule/createEntity', payload: entity }));
const mockPartialUpdateEntity = jest.fn(entity => ({ type: 'transactionRule/partialUpdateEntity', payload: entity }));
const mockGetCategories = jest.fn(() => ({ type: 'category/getEntities' }));
const mockGetTags = jest.fn(() => ({ type: 'tag/getEntities' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./transaction-rule.reducer', () => ({
  getEntity: id => mockGetEntity(id),
  getEntities: params => mockGetEntities(params),
  reset: () => mockReset(),
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
}));

jest.mock('app/entities/category/category.reducer', () => ({
  getEntities: () => mockGetCategories(),
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
  active: false,
  createdAt: dayjs('2026-07-12T10:00:00'),
  updatedAt: dayjs('2026-07-13T10:00:00'),
  resultingCategory: { id: 3, name: 'Food', categoryType: 'EXPENSE' },
  resultingTags: [{ id: 5, name: 'Morning' }],
};

const baseState = {
  category: {
    entities: [
      { id: 3, name: 'Food', categoryType: 'EXPENSE' },
      { id: 4, name: 'Salary', categoryType: 'INCOME' },
      { id: 6, name: 'General', categoryType: 'BOTH' },
    ],
  },
  tag: { entities: [{ id: 5, name: 'Morning' }] },
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
  TranslatorContext.registerTranslations('en', enRuleConditionLogic);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.setLocale('en');
};

const createListRule = (id: number, name: string, priority: number) => ({
  ...baseRule,
  id,
  name,
  priority,
  resultingCategory: priority === 0 ? baseRule.resultingCategory : null,
  resultingTags: priority === 0 ? baseRule.resultingTags : [],
});

const renderList = (entities: any[] = [createListRule(1, 'Coffee rule', 0), createListRule(2, 'Rent rule', 1)]) => {
  mockState = {
    ...baseState,
    transactionRule: {
      ...baseState.transactionRule,
      entities,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/transaction-rule']}>
      <Routes>
        <Route path="/transaction-rule" element={<TransactionRule />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderCreateForm = (stateOverride: any = {}) => {
  mockState = {
    ...baseState,
    ...stateOverride,
    transactionRule: {
      ...baseState.transactionRule,
      entity: {},
      ...(stateOverride.transactionRule ?? {}),
    },
  };

  return render(
    <MemoryRouter initialEntries={['/transaction-rule/new']}>
      <Routes>
        <Route path="/transaction-rule/new" element={<TransactionRuleUpdate />} />
        <Route path="/transaction-rule" element={<div>Transaction rule list route</div>} />
        <Route path="/transaction-rule/:id" element={<div>Created rule detail route</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const configuredRuleResponse = (conditions: any[] = []) => ({
  ...baseRule,
  conditions,
});

const renderEditForm = (configuredResult: any = configuredRuleResponse(), stateOverride: any = {}) => {
  mockAxiosGet.mockImplementation((url: string) => {
    if (url === 'api/transaction-rules/1/configured') {
      if (configuredResult === 'error') {
        return Promise.reject(new Error('configured failed'));
      }
      if (configuredResult === 'pending') {
        return new Promise(() => {});
      }
      return Promise.resolve({ data: configuredResult });
    }
    return new Promise(() => {});
  });
  mockState = {
    ...baseState,
    ...stateOverride,
    transactionRule: {
      ...baseState.transactionRule,
      ...(stateOverride.transactionRule ?? {}),
    },
  };

  return render(
    <MemoryRouter initialEntries={['/transaction-rule/1/edit']}>
      <Routes>
        <Route path="/transaction-rule/:id/edit" element={<TransactionRuleUpdate />} />
        <Route path="/transaction-rule" element={<div>Transaction rule list route</div>} />
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
    mockAxiosPut.mockReset();
    mockAxiosPatch.mockReset();
    mockAxiosDelete.mockReset();
    mockCreateEntity.mockClear();
    mockPartialUpdateEntity.mockClear();
    registerTranslations();
  });

  it('renders product-oriented list columns and keeps row actions', () => {
    renderList([createListRule(1, 'Coffee rule', 0), { ...createListRule(2, 'Rent rule', 1), conditionLogic: 'ANY', active: true }]);

    expect(mockGetEntities).toHaveBeenCalledWith({ sort: 'priority,asc&sort=id,asc' });
    expect(screen.queryByRole('columnheader', { name: /id/i })).toBeNull();
    expect(screen.queryByRole('columnheader', { name: /created at/i })).toBeNull();
    expect(screen.getByRole('columnheader', { name: /name/i })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: /status/i })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: /order/i })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: /conditions/i })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: /result/i })).toBeTruthy();
    expect(screen.getByRole('columnheader', { name: /updated/i })).toBeTruthy();
    expect(screen.getByRole('link', { name: /create a new transaction rule/i }).getAttribute('href')).toBe('/transaction-rule/new');
    expect(screen.getByRole('link', { name: 'Coffee rule' }).getAttribute('href')).toBe('/transaction-rule/1');
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.getByText('Active')).toBeTruthy();
    expect(screen.getByText('All conditions')).toBeTruthy();
    expect(screen.getByText('Any condition')).toBeTruthy();
    expect(screen.getByText('#1')).toBeTruthy();
    expect(screen.getByText('#2')).toBeTruthy();
    expect(screen.getByText('Category: Food')).toBeTruthy();
    expect(screen.getByText('Tags: Morning')).toBeTruthy();
    expect(screen.getByText('No result configured')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: /view/i })).toHaveLength(2);
    expect(screen.getAllByRole('link', { name: /edit/i })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: /delete/i })).toHaveLength(2);
    expect(screen.queryByLabelText('Priority')).toBeNull();
  });

  it('renders rules in priority ascending order even when raw entities are unsorted', () => {
    renderList([createListRule(3, 'Rule C', 2), createListRule(2, 'Rule B', 1), createListRule(1, 'Rule A', 0)]);

    const rows = screen.getAllByRole('row').slice(1);

    expect(within(rows[0]).getByRole('link', { name: 'Rule A' })).toBeTruthy();
    expect(within(rows[0]).getByText('#1')).toBeTruthy();
    expect(within(rows[1]).getByRole('link', { name: 'Rule B' })).toBeTruthy();
    expect(within(rows[1]).getByText('#2')).toBeTruthy();
    expect(within(rows[2]).getByRole('link', { name: 'Rule C' })).toBeTruthy();
    expect(within(rows[2]).getByText('#3')).toBeTruthy();
  });

  it('renders only possible move controls and keeps row actions', () => {
    renderList([createListRule(1, 'Rule A', 0), createListRule(2, 'Rule B', 1), createListRule(3, 'Rule C', 2)]);

    const moveUpButtons = screen.getAllByRole('button', { name: /move up/i });
    const moveDownButtons = screen.getAllByRole('button', { name: /move down/i });
    const rows = screen.getAllByRole('row').slice(1);

    expect(moveUpButtons).toHaveLength(2);
    expect(moveDownButtons).toHaveLength(2);
    expect(within(rows[0]).queryByRole('button', { name: /move up/i })).toBeNull();
    expect(within(rows[0]).getByRole('button', { name: /move down/i })).toBeTruthy();
    expect(within(rows[1]).getByRole('button', { name: /move up/i })).toBeTruthy();
    expect(within(rows[1]).getByRole('button', { name: /move down/i })).toBeTruthy();
    expect(within(rows[2]).getByRole('button', { name: /move up/i })).toBeTruthy();
    expect(within(rows[2]).queryByRole('button', { name: /move down/i })).toBeNull();
    expect(screen.getAllByRole('link', { name: /view/i })).toHaveLength(3);
    expect(screen.getAllByRole('link', { name: /edit/i })).toHaveLength(3);
    expect(screen.getAllByRole('button', { name: /delete/i })).toHaveLength(3);
  });

  it('renders no reorder buttons for a single-row list', () => {
    renderList([createListRule(1, 'Only rule', 0)]);

    expect(screen.queryByRole('button', { name: /move up/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /move down/i })).toBeNull();
    expect(screen.getAllByRole('link', { name: /view/i })).toHaveLength(1);
    expect(screen.getAllByRole('link', { name: /edit/i })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: /delete/i })).toHaveLength(1);
  });

  it('clicking move up on the last row sends the full swapped priority order and reloads', async () => {
    mockAxiosPut.mockResolvedValue({ data: [] });
    renderList([createListRule(1, 'Rule A', 0), createListRule(2, 'Rule B', 1), createListRule(3, 'Rule C', 2)]);
    mockGetEntities.mockClear();

    fireEvent.click(within(screen.getAllByRole('row')[3]).getByRole('button', { name: /move up/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith('api/transaction-rules/reorder', {
        orderedIds: [1, 3, 2],
      }),
    );
    await waitFor(() => expect(mockGetEntities).toHaveBeenCalledWith({ sort: 'priority,asc&sort=id,asc' }));
  });

  it('clicking move down on the first row sends the full swapped priority order and reloads', async () => {
    mockAxiosPut.mockResolvedValue({ data: [] });
    renderList([createListRule(1, 'Rule A', 0), createListRule(2, 'Rule B', 1), createListRule(3, 'Rule C', 2)]);
    mockGetEntities.mockClear();

    fireEvent.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: /move down/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith('api/transaction-rules/reorder', {
        orderedIds: [2, 1, 3],
      }),
    );
    await waitFor(() => expect(mockGetEntities).toHaveBeenCalledWith({ sort: 'priority,asc&sort=id,asc' }));
  });

  it('uses the priority ascending array for reorder even when raw entities are reversed', async () => {
    mockAxiosPut.mockResolvedValue({ data: [] });
    renderList([createListRule(3, 'Rule C', 2), createListRule(2, 'Rule B', 1), createListRule(1, 'Rule A', 0)]);

    fireEvent.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: /move down/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith('api/transaction-rules/reorder', {
        orderedIds: [2, 1, 3],
      }),
    );
  });

  it('shows a reorder error when move request fails', async () => {
    mockAxiosPut.mockRejectedValue(new Error('reorder failed'));
    renderList();

    fireEvent.click(screen.getAllByRole('button', { name: /move down/i })[0]);

    expect(await screen.findByText('Could not reorder rules.')).toBeTruthy();
  });

  it('renders configured create form with inline conditions editor', () => {
    renderCreateForm();

    expect(screen.getByRole('heading', { name: 'Create Transaction Rule' })).toBeTruthy();
    expect(screen.getByText('Configure metadata, outputs, conditions, and active state before saving.')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Priority')).toBeNull();
    expect(screen.queryByLabelText('Resulting Financial Subscription')).toBeNull();
    expect(screen.getByLabelText('Active')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Conditions' })).toBeTruthy();
    expect(screen.getByRole('button', { name: /^add condition$/i })).toBeTruthy();
    expect((screen.getByRole('button', { name: /^save$/i }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText('Rule order is managed from the rules list. New rules are added last.')).toBeTruthy();
  });

  it('blocks configured create save with zero conditions and zero outputs', () => {
    renderCreateForm();

    expect(screen.getAllByText('Transaction rule must have at least one condition.')).toHaveLength(2);
    expect(screen.getByText('Select at least one output: category or tags.')).toBeTruthy();
    expect((screen.getByRole('button', { name: /^save$/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('auto-adds and locks the required expense Flow OUT condition', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '3' } });

    expect(await screen.findByText('Flow equals Expense')).toBeTruthy();
    expect(screen.getByText('This Flow condition is required by the selected category.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^delete condition$/i })).toBeNull();
  });

  it('removes only the auto-required Flow condition when category no longer requires flow', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '3' } });
    expect(await screen.findByText('Flow equals Expense')).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '6' } });

    await waitFor(() => expect(screen.queryByText('Flow equals Expense')).toBeNull());
  });

  it('forces ALL and disables ANY when ANY is selected before choosing an EXPENSE category', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Condition Logic'), { target: { value: 'ANY' } });
    expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ANY');

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '3' } });

    await screen.findByText('Flow equals Expense');
    await waitFor(() => expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ALL'));
    expect((screen.getByRole('option', { name: 'Any condition' }) as HTMLOptionElement).disabled).toBe(true);
  });

  it('forces ALL and disables ANY when ANY is selected before choosing an INCOME category', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Condition Logic'), { target: { value: 'ANY' } });
    expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ANY');

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '4' } });

    await screen.findByText('Flow equals Income');
    await waitFor(() => expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ALL'));
    expect((screen.getByRole('option', { name: 'Any condition' }) as HTMLOptionElement).disabled).toBe(true);
  });

  it('allows ANY again when an EXPENSE category changes to BOTH or is cleared', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '3' } });

    await screen.findByText('Flow equals Expense');
    expect((screen.getByRole('option', { name: 'Any condition' }) as HTMLOptionElement).disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '6' } });

    await waitFor(() => expect(screen.queryByText('Flow equals Expense')).toBeNull());
    expect((screen.getByRole('option', { name: 'Any condition' }) as HTMLOptionElement).disabled).toBe(false);
    fireEvent.change(screen.getByLabelText('Condition Logic'), { target: { value: 'ANY' } });
    expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ANY');

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '3' } });
    await screen.findByText('Flow equals Expense');
    await waitFor(() => expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ALL'));

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '' } });

    await waitFor(() => expect(screen.queryByText('Flow equals Expense')).toBeNull());
    expect((screen.getByRole('option', { name: 'Any condition' }) as HTMLOptionElement).disabled).toBe(false);
  });

  it('allows ANY for tag-only rules', () => {
    renderCreateForm();

    const tagsSelect = screen.getByLabelText('Resulting Tags') as HTMLSelectElement;
    const morningTag = within(tagsSelect).getByRole('option', { name: 'Morning' }) as HTMLOptionElement;
    morningTag.selected = true;
    fireEvent.change(tagsSelect);
    fireEvent.change(screen.getByLabelText('Condition Logic'), { target: { value: 'ANY' } });

    expect((screen.getByRole('option', { name: 'Any condition' }) as HTMLOptionElement).disabled).toBe(false);
    expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ANY');
  });

  it('posts configured create payload instead of creating a draft rule', async () => {
    mockAxiosPost.mockResolvedValue({ data: { id: 42 } });
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '3' } });
    await screen.findByText('Flow equals Expense');
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New rule' } });
    fireEvent.blur(screen.getByLabelText('Name'));
    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('New rule'));
    await waitFor(() => expect((screen.getByRole('button', { name: /^save$/i }) as HTMLButtonElement).disabled).toBe(false));
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/transaction-rules/configured',
        expect.objectContaining({
          name: 'New rule',
          active: true,
          resultingCategory: { id: 3 },
          resultingTags: [],
          conditions: [
            expect.objectContaining({
              field: 'FLOW',
              operator: 'EQUALS',
              value: 'OUT',
              position: 0,
            }),
          ],
        }),
      ),
    );
    expect(mockCreateEntity).not.toHaveBeenCalled();
  });

  it('loads configured edit form and renders inline persisted conditions', async () => {
    renderEditForm(
      configuredRuleResponse([
        {
          id: 11,
          position: 0,
          field: 'DESCRIPTION',
          operator: 'CONTAINS',
          value: 'Coffee',
          caseSensitive: false,
        },
        {
          id: 12,
          position: 1,
          field: 'FLOW',
          operator: 'EQUALS',
          value: 'OUT',
          caseSensitive: false,
        },
      ]),
    );

    expect(await screen.findByRole('heading', { name: 'Edit Transaction Rule' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Identity' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Matching logic' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Result' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Status' })).toBeTruthy();
    expect(screen.getByLabelText('Active')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Priority')).toBeNull();
    expect(screen.queryByLabelText('Resulting Financial Subscription')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Conditions' })).toBeTruthy();
    expect(screen.getByText('Description contains "Coffee"')).toBeTruthy();
    expect(screen.getByText('Flow equals Expense')).toBeTruthy();
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules/1/configured'));
  });

  it('hydrates existing rule values on edit', async () => {
    renderEditForm(
      configuredRuleResponse([
        {
          id: 12,
          position: 0,
          field: 'FLOW',
          operator: 'EQUALS',
          value: 'OUT',
          caseSensitive: false,
        },
      ]),
    );

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Coffee rule'));
    expect((screen.getByLabelText('Description') as HTMLInputElement).value).toBe('Coffee shops');
    expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ALL');
    expect((screen.getByLabelText('Resulting Category') as HTMLSelectElement).value).toBe('3');
    expect(screen.queryByLabelText('Priority')).toBeNull();
  });

  it('puts configured edit payload with local conditions and without priority', async () => {
    mockAxiosPut.mockResolvedValue({ data: { id: 1 } });
    renderEditForm(
      configuredRuleResponse([
        {
          id: 11,
          position: 0,
          field: 'DESCRIPTION',
          operator: 'CONTAINS',
          value: 'Coffee',
          caseSensitive: false,
        },
        {
          id: 12,
          position: 1,
          field: 'FLOW',
          operator: 'EQUALS',
          value: 'OUT',
          caseSensitive: false,
        },
      ]),
    );

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Coffee rule'));
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Updated description' } });
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith(
        'api/transaction-rules/1/configured',
        expect.objectContaining({
          id: 1,
          description: 'Updated description',
          resultingCategory: { id: 3 },
          conditions: expect.arrayContaining([
            expect.objectContaining({ id: 11, field: 'DESCRIPTION', value: 'Coffee' }),
            expect.objectContaining({ id: 12, field: 'FLOW', value: 'OUT' }),
          ]),
        }),
      ),
    );
    expect(mockAxiosPut.mock.calls[0][1]).not.toHaveProperty('priority');
    expect(mockAxiosPut.mock.calls[0][1]).not.toHaveProperty('resulting' + 'Description');
    expect(mockAxiosPut.mock.calls[0][1]).not.toHaveProperty('resulting' + 'FinancialSubscription');
    expect(mockPartialUpdateEntity).not.toHaveBeenCalled();
  });

  it('blocks save when selected expense category conflicts with a user-authored Flow condition', async () => {
    renderEditForm(
      configuredRuleResponse([
        {
          id: 11,
          position: 0,
          field: 'FLOW',
          operator: 'EQUALS',
          value: 'IN',
          caseSensitive: false,
        },
      ]),
    );

    expect(await screen.findByText('Expense categories require Flow = OUT.')).toBeTruthy();
    expect(screen.getByText('The selected category is incompatible with an existing Flow condition.')).toBeTruthy();
    expect((screen.getByRole('button', { name: /^save$/i }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('hydrates resulting category, tags, and active on edit', async () => {
    renderEditForm({
      ...configuredRuleResponse([
        {
          id: 12,
          position: 0,
          field: 'FLOW',
          operator: 'EQUALS',
          value: 'OUT',
          caseSensitive: false,
        },
      ]),
      active: true,
    });

    await waitFor(() => expect((screen.getByLabelText('Resulting Category') as HTMLSelectElement).value).toBe('3'));
    expect(screen.getByRole('option', { name: 'Morning' })).toHaveProperty('selected', true);
    expect((screen.getByLabelText('Active') as HTMLInputElement).checked).toBe(true);
  });

  it('does not render an empty edit form before the requested entity is loaded', () => {
    renderEditForm('pending');

    expect(screen.getByText('Loading...')).toBeTruthy();
    expect(screen.queryByLabelText('Name')).toBeNull();
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
    expect(mockPartialUpdateEntity).not.toHaveBeenCalled();
  });

  it('renders compact detail sections aligned with edit layout', async () => {
    renderDetail([]);

    expect(screen.getByRole('heading', { name: 'Coffee rule' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Identity' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Matching logic' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Result' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Status / Metadata' })).toBeTruthy();
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.getByText('Evaluation order:')).toBeTruthy();
    expect(screen.getByText('#11')).toBeTruthy();
    expect(screen.getByText('All conditions')).toBeTruthy();
    expect(screen.getByText('Food')).toBeTruthy();
    expect(screen.getByText('Morning')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'When' })).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Then' })).toBeNull();
    expect(screen.getByText('Conditions technical editor')).toBeTruthy();
    expect(
      screen.getByText(
        'For product editing, use the configured edit flow. This section writes directly to condition endpoints and is kept temporarily for debugging.',
      ),
    ).toBeTruthy();
    expect(await screen.findByRole('heading', { name: 'Conditions' })).toBeTruthy();
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

    await screen.findByText('Description contains "Coffee"');
    fireEvent.click(screen.getByRole('button', { name: /delete condition/i }));

    expect(await screen.findByText('Condition could not be deleted.')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Coffee rule' })).toBeTruthy();
  });

  it('handles edit background condition load failure without breaking rule edit page', async () => {
    renderEditForm('error');

    expect(screen.getByRole('heading', { name: 'Edit Transaction Rule' })).toBeTruthy();
    expect(await screen.findByText('Transaction rule configuration could not be loaded.')).toBeTruthy();
    expect(screen.queryByLabelText('Active')).toBeNull();
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
    expect(screen.getByRole('heading', { name: 'Coffee rule' })).toBeTruthy();
  });
});
