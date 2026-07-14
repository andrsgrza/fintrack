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
  resultingCategory: { id: 3, name: 'Food' },
  resultingTags: [{ id: 5, name: 'Morning' }],
};

const baseState = {
  category: { entities: [{ id: 3, name: 'Food' }] },
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
        <Route path="/transaction-rule/:id" element={<div>Created rule detail route</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = (conditionsResult: any = [], stateOverride: any = {}) => {
  mockAxiosGet.mockImplementation((url: string) => {
    if (url === 'api/transaction-rules/1/conditions') {
      if (conditionsResult === 'error') {
        return Promise.reject(new Error('conditions failed'));
      }
      return Promise.resolve({ data: conditionsResult });
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

  it('renders create title, hides timestamps, and hides active in create mode', () => {
    renderCreateForm();

    expect(screen.getByRole('heading', { name: 'Create Transaction Rule' })).toBeTruthy();
    expect(
      screen.getByText('Rules are saved inactive first. Add conditions from the rule detail page, then activate the rule when ready.'),
    ).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Active')).toBeNull();
    expect(screen.queryByLabelText('Priority')).toBeNull();
    expect(screen.queryByLabelText('Resulting Financial Subscription')).toBeNull();
    expect(screen.getByRole('button', { name: /save and add conditions/i })).toBeTruthy();
    expect(screen.getByText('Rule order is managed from the rules list. New rules are added last.')).toBeTruthy();
  });

  it('does not render the embedded conditions editor in create mode', () => {
    renderCreateForm();

    expect(screen.queryByRole('heading', { name: 'Conditions' })).toBeNull();
    expect(screen.queryByRole('button', { name: /^add condition$/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /^delete condition$/i })).toBeNull();
  });

  it('submits new rules as inactive drafts', async () => {
    renderCreateForm();
    mockDispatch.mockClear();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New rule' } });
    fireEvent.change(screen.getByLabelText('Resulting Category'), { target: { value: '3' } });
    fireEvent.click(screen.getByRole('button', { name: /save and add conditions/i }));

    await waitFor(() =>
      expect(mockCreateEntity).toHaveBeenCalledWith(
        expect.objectContaining({
          name: 'New rule',
          active: false,
          resultingCategory: { id: 3, name: 'Food' },
        }),
      ),
    );
    expect(mockCreateEntity.mock.calls[0][0]).not.toHaveProperty('priority');
    expect(mockCreateEntity.mock.calls[0][0]).not.toHaveProperty('resulting' + 'Description');
    expect(mockCreateEntity.mock.calls[0][0]).not.toHaveProperty('resulting' + 'FinancialSubscription');
    expect(mockDispatch).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'transactionRule/createEntity',
        payload: expect.objectContaining({ active: false }),
      }),
    );
  });

  it('redirects successful create to the new rule detail page', async () => {
    renderCreateForm({
      transactionRule: {
        entity: { id: 42 },
        updateSuccess: true,
      },
    });

    expect(await screen.findByText('Created rule detail route')).toBeTruthy();
  });

  it('renders edit title, active field, and manage conditions link without embedded editor', async () => {
    renderEditForm();

    expect(screen.getByRole('heading', { name: 'Edit Transaction Rule' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Identity' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Matching logic' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Result' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Status' })).toBeTruthy();
    expect(screen.getByLabelText('Active')).toBeTruthy();
    expect(screen.getByText('Active rules require at least one condition.')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Priority')).toBeNull();
    expect(screen.queryByLabelText('Resulting Financial Subscription')).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Conditions' })).toBeNull();
    expect(screen.queryByRole('button', { name: /add condition/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /delete condition/i })).toBeNull();
    expect(screen.getByRole('link', { name: /manage conditions/i }).getAttribute('href')).toBe('/transaction-rule/1');
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules/1/conditions'));
  });

  it('hydrates existing rule values on edit', async () => {
    renderEditForm();

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Coffee rule'));
    expect((screen.getByLabelText('Description') as HTMLInputElement).value).toBe('Coffee shops');
    expect((screen.getByLabelText('Condition Logic') as HTMLSelectElement).value).toBe('ALL');
    expect((screen.getByLabelText('Resulting Category') as HTMLSelectElement).value).toBe('3');
    expect(screen.queryByLabelText('Priority')).toBeNull();
  });

  it('submits edited rules without priority', async () => {
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
    mockPartialUpdateEntity.mockClear();

    await waitFor(() => expect((screen.getByLabelText('Name') as HTMLInputElement).value).toBe('Coffee rule'));
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Updated description' } });
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    await waitFor(() =>
      expect(mockPartialUpdateEntity).toHaveBeenCalledWith(
        expect.objectContaining({
          id: 1,
          description: 'Updated description',
        }),
      ),
    );
    expect(mockPartialUpdateEntity.mock.calls[0][0]).not.toHaveProperty('priority');
    expect(mockPartialUpdateEntity.mock.calls[0][0]).not.toHaveProperty('resulting' + 'Description');
    expect(mockPartialUpdateEntity.mock.calls[0][0]).not.toHaveProperty('resulting' + 'FinancialSubscription');
  });

  it('hydrates resulting category, tags, and active on edit', async () => {
    renderEditForm([], {
      transactionRule: {
        entity: {
          ...baseRule,
          active: true,
        },
      },
    });

    await waitFor(() => expect((screen.getByLabelText('Resulting Category') as HTMLSelectElement).value).toBe('3'));
    expect(screen.getByRole('option', { name: 'Morning' })).toHaveProperty('selected', true);
    expect((screen.getByLabelText('Active') as HTMLInputElement).checked).toBe(true);
  });

  it('does not render an empty edit form before the requested entity is loaded', () => {
    renderEditForm([], {
      transactionRule: {
        entity: {},
        loading: false,
      },
    });

    expect(screen.getByText('Loading...')).toBeTruthy();
    expect(screen.queryByLabelText('Name')).toBeNull();
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
    expect(screen.getByRole('heading', { name: 'Coffee rule' })).toBeTruthy();
  });
});
