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
import enTransactionOrigin from 'app/../i18n/en/transactionOrigin.json';
import enCategory from 'app/../i18n/en/category.json';
import enTag from 'app/../i18n/en/tag.json';
import esTransactionRule from 'app/../i18n/es/transactionRule.json';
import esTransactionRuleCondition from 'app/../i18n/es/transactionRuleCondition.json';
import esTransactionRuleField from 'app/../i18n/es/transactionRuleField.json';
import esRuleOperator from 'app/../i18n/es/ruleOperator.json';
import esRuleConditionLogic from 'app/../i18n/es/ruleConditionLogic.json';
import esTransactionFlow from 'app/../i18n/es/transactionFlow.json';
import esTransactionOrigin from 'app/../i18n/es/transactionOrigin.json';
import esCategory from 'app/../i18n/es/category.json';
import esTag from 'app/../i18n/es/tag.json';
import { TransactionRule } from './transaction-rule';
import { TransactionRuleDetail } from './transaction-rule-detail';
import { TransactionRuleDeleteDialog } from './transaction-rule-delete-dialog';
import { TransactionRuleUpdate } from './transaction-rule-update';

jest.mock('axios');
jest.mock('@dnd-kit/core', () => ({
  DndContext: ({ children, onDragEnd }) => (
    <div>
      <button
        type="button"
        data-testid="transaction-rule-dnd-drop-first-on-second"
        onClick={() => onDragEnd({ active: { id: 1 }, over: { id: 2 } })}
      >
        Trigger drag reorder
      </button>
      {children}
    </div>
  ),
  KeyboardSensor: class KeyboardSensor {},
  MouseSensor: class MouseSensor {},
  TouchSensor: class TouchSensor {},
  closestCenter: jest.fn(),
  useSensor: jest.fn(() => ({})),
  useSensors: jest.fn(() => []),
}));
jest.mock('@dnd-kit/sortable', () => ({
  SortableContext: ({ children }) => <>{children}</>,
  arrayMove(items, from, to) {
    const copy = [...items];
    const [item] = copy.splice(from, 1);
    copy.splice(to, 0, item);
    return copy;
  },
  sortableKeyboardCoordinates: jest.fn(),
  useSortable: () => ({ attributes: {}, listeners: {}, setNodeRef: jest.fn(), transform: null, transition: undefined, isDragging: false }),
  verticalListSortingStrategy: jest.fn(),
}));
jest.mock('@dnd-kit/utilities', () => ({ CSS: { Transform: { toString: jest.fn(() => undefined) } } }));

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
const mockDeleteEntity = jest.fn(id => ({ type: 'transactionRule/deleteEntity', payload: id }));
const mockGetSelectableCategories = jest.fn();
const mockGetSelectableTags = jest.fn();
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
  deleteEntity: id => mockDeleteEntity(id),
}));

jest.mock('app/entities/category/category-selectable.service', () => ({
  getSelectableCategories: (...args) => mockGetSelectableCategories(...args),
}));

jest.mock('app/entities/tag/tag-selectable.service', () => ({
  getSelectableTags: (...args) => mockGetSelectableTags(...args),
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
  TranslatorContext.registerTranslations('en', enTransactionOrigin);
  TranslatorContext.registerTranslations('en', enCategory);
  TranslatorContext.registerTranslations('en', enTag);
  TranslatorContext.setLocale('en');
};

const registerSpanishTranslations = () => {
  TranslatorContext.registerTranslations('es', esTransactionRule);
  TranslatorContext.registerTranslations('es', esTransactionRuleCondition);
  TranslatorContext.registerTranslations('es', esTransactionRuleField);
  TranslatorContext.registerTranslations('es', esRuleOperator);
  TranslatorContext.registerTranslations('es', esRuleConditionLogic);
  TranslatorContext.registerTranslations('es', esTransactionFlow);
  TranslatorContext.registerTranslations('es', esTransactionOrigin);
  TranslatorContext.registerTranslations('es', esCategory);
  TranslatorContext.registerTranslations('es', esTag);
  TranslatorContext.setLocale('es');
};

const createListRule = (id: number, name: string, priority: number) => ({
  ...baseRule,
  id,
  name,
  priority,
  resultingCategory: priority === 0 ? baseRule.resultingCategory : null,
  resultingTags: priority === 0 ? baseRule.resultingTags : [],
});

const managerConditions = (ruleId: number) => {
  if (ruleId === 2) {
    return [
      { id: 22, position: 0, field: 'AMOUNT', operator: 'BETWEEN', value: '20', secondValue: '500', caseSensitive: false },
      { id: 23, position: 1, field: 'ACCOUNT', operator: 'EQUALS', value: '2', secondValue: null, caseSensitive: false },
    ];
  }
  return [
    { id: 11, position: 0, field: 'DESCRIPTION', operator: 'CONTAINS', value: 'Coffee', secondValue: null, caseSensitive: true },
    { id: 12, position: 1, field: 'FLOW', operator: 'EQUALS', value: 'OUT', secondValue: null, caseSensitive: false },
    {
      id: 13,
      position: 2,
      field: 'TRANSACTION_DATE',
      operator: 'BETWEEN',
      value: '2026-01-01',
      secondValue: '2026-01-31',
      caseSensitive: false,
    },
  ];
};

const mockManagerList = (entities: any[], options: { failedConditionsFor?: number[]; catalogFailure?: boolean } = {}) => {
  mockAxiosGet.mockImplementation((url: string) => {
    if (url.startsWith('api/transaction-rules?')) {
      return Promise.resolve({ data: entities });
    }
    if (url === 'api/categories?sort=name,ASC') {
      return options.catalogFailure
        ? Promise.reject(new Error('categories unavailable'))
        : Promise.resolve({ data: [{ id: 3, name: 'Food', color: '#d97706', active: false, categoryType: 'EXPENSE' }] });
    }
    if (url === 'api/tags?sort=name,ASC') {
      return options.catalogFailure
        ? Promise.reject(new Error('tags unavailable'))
        : Promise.resolve({ data: [{ id: 5, name: 'Morning', color: '#2563eb', active: false }] });
    }
    if (url === 'api/financial-accounts?sort=name,ASC') {
      return options.catalogFailure
        ? Promise.reject(new Error('accounts unavailable'))
        : Promise.resolve({ data: [{ id: 2, name: 'Checking account', accountType: 'DEBIT', currency: 'MXN', active: true }] });
    }
    const conditionMatch = url.match(/^api\/transaction-rules\/(\d+)\/conditions$/);
    if (conditionMatch) {
      const ruleId = Number(conditionMatch[1]);
      if (options.failedConditionsFor?.includes(ruleId)) {
        return Promise.reject(new Error('conditions unavailable'));
      }
      return Promise.resolve({ data: managerConditions(ruleId) });
    }
    return Promise.resolve({ data: [] });
  });
};

const renderList = (
  entities: any[] = [createListRule(1, 'Coffee rule', 0), createListRule(2, 'Rent rule', 1)],
  options: { admin?: boolean; failedConditionsFor?: number[]; catalogFailure?: boolean } = {},
) => {
  mockState = {
    ...baseState,
    authentication: { account: { authorities: [options.admin ? 'ROLE_ADMIN' : 'ROLE_USER'] } },
    locale: { currentLocale: 'en' },
    transactionRule: {
      ...baseState.transactionRule,
      entities,
    },
  };
  mockManagerList(entities, options);

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

const waitForSelectableOutputs = async () => {
  await screen.findByRole('button', { name: /choose category/i });
  await screen.findByRole('button', { name: /add tag/i });
};

const chooseCategory = async (name: string) => {
  fireEvent.click(screen.getByRole('button', { name: /choose category/i }));
  fireEvent.click(await screen.findByRole('button', { name: new RegExp(name, 'i') }));
};

const clearCategory = () => fireEvent.click(screen.getByRole('button', { name: /remove/i }));

const addResultTag = async (name: string) => {
  fireEvent.click(screen.getByRole('button', { name: /add tag/i }));
  fireEvent.click(await screen.findByRole('button', { name: new RegExp(name, 'i') }));
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
        <Route path="/transaction-rule/:id" element={<div>Updated rule detail route</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = (
  configuredResult: any = configuredRuleResponse(managerConditions(1)),
  options: { accounts?: any; accountFailure?: boolean; locale?: 'en' | 'es' } = {},
) => {
  mockAxiosGet.mockImplementation((url: string) => {
    if (url === 'api/transaction-rules/1/configured') {
      if (configuredResult === 'error') {
        return Promise.reject(new Error('conditions failed'));
      }
      if (configuredResult === 'notFound') {
        return Promise.reject(Object.assign(new Error('not found'), { response: { status: 404 } }));
      }
      if (configuredResult === 'forbidden') {
        return Promise.reject(Object.assign(new Error('forbidden'), { response: { status: 403 } }));
      }
      if (configuredResult === 'pending') {
        return new Promise(() => {});
      }
      return Promise.resolve({ data: configuredResult });
    }
    if (url === 'api/financial-accounts?sort=name,ASC') {
      return options.accountFailure
        ? Promise.reject(new Error('accounts failed'))
        : Promise.resolve({ data: options.accounts ?? [{ id: 2, name: 'Checking account' }] });
    }
    return Promise.resolve({ data: [] });
  });
  mockState = { ...baseState, locale: { currentLocale: options.locale ?? 'en' } };

  return render(
    <MemoryRouter initialEntries={['/transaction-rule/1']}>
      <Routes>
        <Route path="/transaction-rule/:id" element={<TransactionRuleDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDeleteDialog = () => {
  mockState = { ...baseState, locale: { currentLocale: 'en' } };
  return render(
    <MemoryRouter initialEntries={['/transaction-rule/1/delete']}>
      <Routes>
        <Route path="/transaction-rule/:id/delete" element={<TransactionRuleDeleteDialog />} />
        <Route path="/transaction-rule" element={<div>Classification rules manager</div>} />
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
    mockGetSelectableCategories.mockReset();
    mockGetSelectableTags.mockReset();
    mockGetSelectableCategories.mockResolvedValue(baseState.category.entities);
    mockGetSelectableTags.mockResolvedValue(baseState.tag.entities);
    mockCreateEntity.mockClear();
    mockPartialUpdateEntity.mockClear();
    registerTranslations();
  });

  it('renders a product rule manager with readable matching and inactive result identities', async () => {
    renderList([createListRule(1, 'Coffee rule', 0), { ...createListRule(2, 'Rent rule', 1), conditionLogic: 'ANY', active: true }]);

    expect(await screen.findByText('Coffee rule')).toBeTruthy();
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.getByRole('link', { name: /create rule/i }).getAttribute('href')).toBe('/transaction-rule/new');
    expect(screen.getByRole('link', { name: 'Coffee rule' }).getAttribute('href')).toBe('/transaction-rule/1');
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.getByText('Active')).toBeTruthy();
    expect(screen.getByText(/All conditions/)).toBeTruthy();
    expect(screen.getByText(/Any condition/)).toBeTruthy();
    expect(screen.getByText('Position 1')).toBeTruthy();
    expect(screen.getByText('Position 2')).toBeTruthy();
    expect(screen.getByTestId('transactionRuleSummary-1').textContent).toContain('Coffee');
    expect(screen.getByTestId('transactionRuleSummary-1').textContent).toContain('case-sensitive');
    expect(screen.getByTestId('transactionRuleSummary-1').textContent).toContain('Expense');
    expect(screen.getByText('and 1 more')).toBeTruthy();
    expect(screen.getByText('Food')).toBeTruthy();
    expect(screen.getByText('Morning')).toBeTruthy();
    expect(screen.getAllByText('Inactive historical output')).toHaveLength(1);
    const secondRuleSummary = screen.getByTestId('transactionRuleSummary-2').textContent ?? '';
    expect(secondRuleSummary).toContain('Amount');
    expect(secondRuleSummary).toContain('20');
    expect(secondRuleSummary).toContain('500');
    expect(secondRuleSummary).toContain('Checking account');
    expect(screen.getAllByRole('link', { name: /view/i })).toHaveLength(2);
    expect(screen.getAllByRole('link', { name: /edit/i })).toHaveLength(2);
    expect(screen.getByText(/first eligible category wins/i)).toBeTruthy();
  });

  it('renders rules in priority ascending order even when the response is unsorted', async () => {
    renderList([createListRule(3, 'Rule C', 2), createListRule(2, 'Rule B', 1), createListRule(1, 'Rule A', 0)]);

    await screen.findByText('Rule A');
    const cards = screen.getAllByTestId(/transactionRuleCard-/);

    expect(within(cards[0]).getByRole('link', { name: 'Rule A' })).toBeTruthy();
    expect(within(cards[0]).getByText('Position 1')).toBeTruthy();
    expect(within(cards[1]).getByRole('link', { name: 'Rule B' })).toBeTruthy();
    expect(within(cards[1]).getByText('Position 2')).toBeTruthy();
    expect(within(cards[2]).getByRole('link', { name: 'Rule C' })).toBeTruthy();
    expect(within(cards[2]).getByText('Position 3')).toBeTruthy();
  });

  it('keeps boundary arrow controls accessible and disabled', async () => {
    renderList([createListRule(1, 'Rule A', 0), createListRule(2, 'Rule B', 1), createListRule(3, 'Rule C', 2)]);

    await screen.findByText('Rule A');
    const moveUpButtons = screen.getAllByRole('button', { name: /move up/i });
    const moveDownButtons = screen.getAllByRole('button', { name: /move down/i });
    const cards = screen.getAllByTestId(/transactionRuleCard-/);

    expect(moveUpButtons).toHaveLength(3);
    expect(moveDownButtons).toHaveLength(3);
    expect(within(cards[0]).getByRole('button', { name: /move up/i }).disabled).toBe(true);
    expect(within(cards[0]).getByRole('button', { name: /move down/i }).disabled).toBe(false);
    expect(within(cards[1]).getByRole('button', { name: /move up/i }).disabled).toBe(false);
    expect(within(cards[1]).getByRole('button', { name: /move down/i }).disabled).toBe(false);
    expect(within(cards[2]).getByRole('button', { name: /move up/i }).disabled).toBe(false);
    expect(within(cards[2]).getByRole('button', { name: /move down/i }).disabled).toBe(true);
    expect(screen.getAllByRole('link', { name: /view/i })).toHaveLength(3);
    expect(screen.getAllByRole('link', { name: /edit/i })).toHaveLength(3);
    expect(screen.getAllByTestId(/transactionRuleDragHandle-/)).toHaveLength(3);
  });

  it('disables both movement controls for a single-rule list', async () => {
    renderList([createListRule(1, 'Only rule', 0)]);

    await screen.findByText('Only rule');
    expect(screen.getByRole('button', { name: /move up/i }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: /move down/i }).disabled).toBe(true);
    expect(screen.getAllByRole('link', { name: /view/i })).toHaveLength(1);
    expect(screen.getAllByRole('link', { name: /edit/i })).toHaveLength(1);
  });

  it('clicking move up on the last row sends the full swapped priority order and reloads', async () => {
    mockAxiosPut.mockResolvedValue({ data: [] });
    renderList([createListRule(1, 'Rule A', 0), createListRule(2, 'Rule B', 1), createListRule(3, 'Rule C', 2)]);
    await screen.findByText('Rule A');

    fireEvent.click(within(screen.getByTestId('transactionRuleCard-3')).getByRole('button', { name: /move up/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith('api/transaction-rules/reorder', {
        orderedIds: [1, 3, 2],
      }),
    );
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules?sort=priority,ASC&sort=id,ASC'));
  });

  it('clicking move down on the first row sends the full swapped priority order and reloads', async () => {
    mockAxiosPut.mockResolvedValue({ data: [] });
    renderList([createListRule(1, 'Rule A', 0), createListRule(2, 'Rule B', 1), createListRule(3, 'Rule C', 2)]);
    await screen.findByText('Rule A');

    fireEvent.click(within(screen.getByTestId('transactionRuleCard-1')).getByRole('button', { name: /move down/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith('api/transaction-rules/reorder', {
        orderedIds: [2, 1, 3],
      }),
    );
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules?sort=priority,ASC&sort=id,ASC'));
  });

  it('uses the priority ascending array for reorder even when raw entities are reversed', async () => {
    mockAxiosPut.mockResolvedValue({ data: [] });
    renderList([createListRule(3, 'Rule C', 2), createListRule(2, 'Rule B', 1), createListRule(1, 'Rule A', 0)]);
    await screen.findByText('Rule A');

    fireEvent.click(within(screen.getByTestId('transactionRuleCard-1')).getByRole('button', { name: /move down/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith('api/transaction-rules/reorder', {
        orderedIds: [2, 1, 3],
      }),
    );
  });

  it('shows a reorder error when move request fails', async () => {
    mockAxiosPut.mockRejectedValue(new Error('reorder failed'));
    renderList();
    await screen.findByText('Coffee rule');

    fireEvent.click(within(screen.getByTestId('transactionRuleCard-1')).getByRole('button', { name: /move down/i }));

    expect(await screen.findByText('The order could not be saved. The last confirmed order has been restored.')).toBeTruthy();
  });

  it('routes drag completion through the same complete-order persistence contract', async () => {
    mockAxiosPut.mockResolvedValue({ data: [] });
    renderList();
    await screen.findByText('Coffee rule');

    fireEvent.click(screen.getByTestId('transaction-rule-dnd-drop-first-on-second'));

    await waitFor(() => expect(mockAxiosPut).toHaveBeenCalledWith('api/transaction-rules/reorder', { orderedIds: [2, 1] }));
  });

  it('shows per-rule condition failure and preserves the rule identity', async () => {
    renderList([createListRule(1, 'Unavailable conditions', 0)], { failedConditionsFor: [1] });

    expect(await screen.findByText('Unavailable conditions')).toBeTruthy();
    expect(screen.getByText('Conditions could not be loaded.')).toBeTruthy();
  });

  it('shows a truthful dependent-data warning instead of silently claiming no outputs', async () => {
    renderList([createListRule(1, 'Historical rule', 0)], { catalogFailure: true });

    expect(await screen.findByText('Historical rule')).toBeTruthy();
    expect(screen.getAllByText('Some output identity details are temporarily unavailable.').length).toBeGreaterThan(0);
    expect(screen.getByText('Food')).toBeTruthy();
  });

  it('keeps reordering unavailable for a mixed-owner admin list', async () => {
    renderList([createListRule(1, 'Admin visible rule', 0)], { admin: true });

    await screen.findByText('Admin visible rule');
    expect(screen.getByTestId('transactionRuleAdminReorderUnavailable')).toBeTruthy();
    expect(screen.queryByTestId('transactionRuleDragHandle-1')).toBeNull();
    expect(screen.queryByRole('button', { name: /move up/i })).toBeNull();
  });

  it('renders an explanatory empty state', async () => {
    renderList([]);

    expect(await screen.findByText('No classification rules yet')).toBeTruthy();
    expect(screen.getByTestId('transactionRuleEmptyCreate')).toBeTruthy();
  });

  it('renders configured create form with inline conditions editor', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    expect(screen.getByRole('heading', { name: 'Create Transaction Rule' })).toBeTruthy();
    expect(screen.getByText('Configure metadata, outputs, conditions, and active state before saving.')).toBeTruthy();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Priority')).toBeNull();
    expect(screen.queryByLabelText('Resulting Financial Subscription')).toBeNull();
    expect(screen.getByLabelText('Active')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Conditions' })).toBeTruthy();
    expect(screen.getByRole('button', { name: /^add condition$/i })).toBeTruthy();
    expect(screen.getByRole('button', { name: /^save$/i }).disabled).toBe(true);
    expect(screen.getByText('Rule order is managed from the rules list. New rules are added last.')).toBeTruthy();
  });

  it('blocks configured create save with zero conditions and zero outputs', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    expect(screen.getAllByText('Transaction rule must have at least one condition.')).toHaveLength(2);
    expect(screen.getByText('Select at least one output: category or tags.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /^save$/i }).disabled).toBe(true);
  });

  it('auto-adds and locks the required expense Flow OUT condition', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    await chooseCategory('Food');

    expect((await screen.findAllByText('Flow equals Expense')).length).toBeGreaterThan(0);
    expect(screen.getByText('This Flow condition is required by the selected category.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^delete condition$/i })).toBeNull();
  });

  it('removes only the auto-required Flow condition when category no longer requires flow', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    await chooseCategory('Food');
    expect((await screen.findAllByText('Flow equals Expense')).length).toBeGreaterThan(0);

    await chooseCategory('General');

    await waitFor(() => expect(screen.queryByText('Flow equals Expense')).toBeNull());
  });

  it('forces ALL and disables ANY when ANY is selected before choosing an EXPENSE category', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    fireEvent.click(screen.getByLabelText('Any condition'));
    expect(screen.getByLabelText('Any condition').checked).toBe(true);

    await chooseCategory('Food');

    await screen.findAllByText('Flow equals Expense');
    await waitFor(() => expect(screen.getByLabelText('All conditions').checked).toBe(true));
    expect(screen.getByLabelText('Any condition').disabled).toBe(true);
  });

  it('forces ALL and disables ANY when ANY is selected before choosing an INCOME category', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    fireEvent.click(screen.getByLabelText('Any condition'));
    expect(screen.getByLabelText('Any condition').checked).toBe(true);

    await chooseCategory('Salary');

    await screen.findAllByText('Flow equals Income');
    await waitFor(() => expect(screen.getByLabelText('All conditions').checked).toBe(true));
    expect(screen.getByLabelText('Any condition').disabled).toBe(true);
  });

  it('allows ANY again when an EXPENSE category changes to BOTH or is cleared', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    await chooseCategory('Food');

    await screen.findAllByText('Flow equals Expense');
    expect(screen.getByLabelText('Any condition').disabled).toBe(true);

    await chooseCategory('General');

    await waitFor(() => expect(screen.queryByText('Flow equals Expense')).toBeNull());
    expect(screen.getByLabelText('Any condition').disabled).toBe(false);
    fireEvent.click(screen.getByLabelText('Any condition'));
    expect(screen.getByLabelText('Any condition').checked).toBe(true);

    await chooseCategory('Food');
    await screen.findAllByText('Flow equals Expense');
    await waitFor(() => expect(screen.getByLabelText('All conditions').checked).toBe(true));

    clearCategory();

    await waitFor(() => expect(screen.queryByText('Flow equals Expense')).toBeNull());
    expect(screen.getByLabelText('Any condition').disabled).toBe(false);
  });

  it('allows ANY for tag-only rules', async () => {
    renderCreateForm();
    await waitForSelectableOutputs();

    await addResultTag('Morning');
    fireEvent.click(screen.getByLabelText('Any condition'));

    expect(screen.getByLabelText('Any condition').disabled).toBe(false);
    expect(screen.getByLabelText('Any condition').checked).toBe(true);
  });

  it('lists only active outputs for a new rule and retains its current inactive outputs while editing', async () => {
    const inactiveCategory = { id: 30, name: 'Former transport', categoryType: 'EXPENSE', active: false };
    const inactiveTag = { id: 40, name: 'Former business', active: false };
    mockGetSelectableCategories.mockImplementation(includeIds =>
      Promise.resolve(includeIds?.includes(30) ? [...baseState.category.entities, inactiveCategory] : baseState.category.entities),
    );
    mockGetSelectableTags.mockImplementation(includeIds =>
      Promise.resolve(includeIds?.includes(40) ? [...baseState.tag.entities, inactiveTag] : baseState.tag.entities),
    );

    const newRule = renderCreateForm();
    await waitForSelectableOutputs();
    fireEvent.click(screen.getByRole('button', { name: /choose category/i }));
    expect(screen.queryByRole('button', { name: /Former transport/ })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: /add tag/i }));
    expect(screen.queryByRole('button', { name: /Former business/ })).toBeNull();
    newRule.unmount();

    renderEditForm({
      ...configuredRuleResponse(),
      resultingCategory: inactiveCategory,
      resultingTags: [inactiveTag],
    });
    expect((await screen.findAllByText('Former transport')).length).toBeGreaterThan(0);
    expect(screen.getByTestId('transactionRuleCategoryIdentityInactive')).toBeTruthy();
    expect(screen.getAllByText('Former business').length).toBeGreaterThan(0);
    expect(screen.getByTestId('transactionRuleResultTagsInactive')).toBeTruthy();
  });

  it('posts configured create payload instead of creating a draft rule', async () => {
    mockAxiosPost.mockResolvedValue({ data: { id: 42 } });
    renderCreateForm();
    await waitForSelectableOutputs();

    await chooseCategory('Food');
    await screen.findAllByText('Flow equals Expense');
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'New rule' } });
    fireEvent.blur(screen.getByLabelText('Name'));
    await waitFor(() => expect(screen.getByLabelText('Name').value).toBe('New rule'));
    await waitFor(() => expect(screen.getByRole('button', { name: /^save$/i }).disabled).toBe(false));
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

  it('keeps a configured create draft intact after a save error', async () => {
    mockAxiosPost.mockRejectedValueOnce({ response: { data: { detail: 'Rule name already exists' } } });
    renderCreateForm();
    await waitForSelectableOutputs();
    await chooseCategory('Food');
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Reliable rule' } });

    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));
    await waitFor(() =>
      expect(document.querySelector('[data-cy="transactionRuleConfiguredError"]')?.textContent).toContain('Rule name already exists'),
    );
    expect(screen.getByLabelText('Name').value).toBe('Reliable rule');
    expect(screen.getByTestId('transactionRuleCategoryIdentity').textContent).toContain('Food');
  });

  it('prevents duplicate configured save requests while the first request is pending', async () => {
    mockAxiosPost.mockImplementation(() => new Promise(() => undefined));
    renderCreateForm();
    await waitForSelectableOutputs();
    await chooseCategory('Food');
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Single submit rule' } });

    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));
    fireEvent.click(screen.getByRole('button', { name: /^save$/i }));

    expect(mockAxiosPost).toHaveBeenCalledTimes(1);
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
    expect(screen.getAllByText('Description contains "Coffee"').length).toBeGreaterThan(0);
    expect((await screen.findAllByText('Flow equals Expense')).length).toBeGreaterThan(0);
    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules/1/configured'));
  });

  it('hydrates persisted required Flow as locked and updates it when category changes from EXPENSE to INCOME', async () => {
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

    expect((await screen.findAllByText('Flow equals Expense')).length).toBeGreaterThan(0);
    expect(screen.getByText('This Flow condition is required by the selected category.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^delete condition$/i })).toBeNull();
    await waitForSelectableOutputs();

    await chooseCategory('Salary');

    await waitFor(() => expect(screen.queryByText('Flow equals Expense')).toBeNull());
    expect(screen.getAllByText('Flow equals Income').length).toBeGreaterThan(0);
    expect(screen.getByText('This Flow condition is required by the selected category.')).toBeTruthy();
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

    await waitForSelectableOutputs();
    await waitFor(() => expect(screen.getByLabelText('Name').value).toBe('Coffee rule'));
    expect(screen.getByLabelText('Description').value).toBe('Coffee shops');
    expect(screen.getByLabelText('All conditions').checked).toBe(true);
    expect(screen.getByTestId('transactionRuleCategoryIdentity').textContent).toContain('Food');
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

    await waitForSelectableOutputs();
    await waitFor(() => expect(screen.getByLabelText('Name').value).toBe('Coffee rule'));
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
    expect(screen.getByRole('button', { name: /^save$/i }).disabled).toBe(true);
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

    await screen.findByTestId('transactionRuleCategoryIdentity');
    expect(screen.getByTestId('transactionRuleCategoryIdentity').textContent).toContain('Food');
    expect(screen.getByTestId('transactionRuleResultTagsChip').textContent).toContain('Morning');
    expect(screen.getByLabelText('Active').checked).toBe(true);
  });

  it('does not render an empty edit form before the requested entity is loaded', () => {
    renderEditForm('pending');

    expect(screen.getByText('Loading...')).toBeTruthy();
    expect(screen.queryByLabelText('Name')).toBeNull();
  });

  it('handles edit background condition load failure without breaking rule edit page', async () => {
    renderEditForm('error');

    expect(screen.getByRole('heading', { name: 'Edit Transaction Rule' })).toBeTruthy();
    expect(await screen.findByText('Transaction rule configuration could not be loaded.')).toBeTruthy();
    expect(screen.queryByLabelText('Active')).toBeNull();
  });

  it('renders a complete read-only configured detail with typed conditions and product outputs', async () => {
    renderDetail(
      configuredRuleResponse([
        { id: 11, position: 0, field: 'DESCRIPTION', operator: 'CONTAINS', value: 'Coffee', caseSensitive: true },
        { id: 12, position: 1, field: 'AMOUNT', operator: 'BETWEEN', value: '20', secondValue: '500', caseSensitive: false },
        { id: 13, position: 2, field: 'TRANSACTION_DATE', operator: 'BEFORE', value: '2026-02-01', caseSensitive: false },
        { id: 14, position: 3, field: 'FLOW', operator: 'EQUALS', value: 'OUT', caseSensitive: false },
        { id: 15, position: 4, field: 'ORIGIN', operator: 'EQUALS', value: 'FILE_IMPORT', caseSensitive: false },
        { id: 16, position: 5, field: 'ACCOUNT', operator: 'EQUALS', value: '2', caseSensitive: false },
      ]),
    );

    expect(await screen.findByRole('heading', { name: 'Coffee rule' })).toBeTruthy();
    expect(screen.getByTestId('transactionRuleDetailStatus').textContent).toContain('Inactive');
    expect(screen.getByTestId('transactionRuleDetailPosition').textContent).toContain('Position 11');
    expect(screen.getByText('Description contains "Coffee" (case-sensitive)')).toBeTruthy();
    expect(screen.getByText('Amount between 20 and 500')).toBeTruthy();
    expect(screen.getByText(/Transaction date before/)).toBeTruthy();
    expect(screen.getByText('Flow equals Expense')).toBeTruthy();
    expect(screen.getByText('Origin equals File import')).toBeTruthy();
    expect(screen.getByText('Account equals Checking account')).toBeTruthy();
    expect(screen.getByText('Food')).toBeTruthy();
    expect(screen.getByText('Morning')).toBeTruthy();
    expect(screen.getByTestId('transactionRuleInactiveExplanation')).toBeTruthy();
    expect(screen.getByTestId('transactionRuleDetailMultiRuleExplanation').textContent).toContain('first eligible category');
    expect(screen.getByTestId('transactionRuleDetailMultiRuleExplanation').textContent).toContain('accumulate without duplicates');
    expect(screen.getByTestId('transactionRuleDetailWorkflowExplanation').textContent).toContain('manual-draft');
    expect(screen.queryByRole('button', { name: /add condition/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /delete condition/i })).toBeNull();
    expect(screen.queryByText('Conditions technical editor')).toBeNull();
    expect(mockAxiosGet).toHaveBeenCalledWith('api/transaction-rules/1/configured');
  });

  it('explains ANY matching and presents historical inactive outputs', async () => {
    renderDetail({
      ...configuredRuleResponse([{ id: 11, position: 0, field: 'DESCRIPTION', operator: 'REGEX', value: 'uber.*', caseSensitive: false }]),
      conditionLogic: 'ANY',
      active: true,
      resultingCategory: { id: 3, name: 'Food', active: false, color: '#d97706' },
      resultingTags: [
        { id: 5, name: 'Morning', active: false, color: '#2563eb' },
        { id: 6, name: 'Weekend', active: true, color: '#9333ea' },
      ],
    });

    expect(await screen.findByText('At least one condition below must match for this rule to suggest its outputs.')).toBeTruthy();
    expect(screen.getByText('Description matches "uber.*"')).toBeTruthy();
    expect(screen.getAllByText('(Inactive)').length).toBeGreaterThan(0);
    expect(screen.getByTestId('transactionRuleDetailInactiveOutputExplanation')).toBeTruthy();
    expect(screen.getByTestId('transactionRuleDetailStatus').textContent).toContain('Active');
  });

  it('presents category-only and tag-only outputs without inventing an absent result', async () => {
    const categoryOnly = renderDetail({ ...configuredRuleResponse(), resultingTags: [] });
    await screen.findByText('Food');
    expect(categoryOnly.container.querySelector('[data-cy="transactionCategory"]')?.textContent).toContain('Food');
    expect(categoryOnly.container.querySelector('[data-cy="transactionTagChip"]')).toBeNull();

    categoryOnly.unmount();
    const tagOnly = renderDetail({ ...configuredRuleResponse(), resultingCategory: null });
    await screen.findByText('Morning');
    expect(tagOnly.container.querySelector('[data-cy="transactionTagChip"]')?.textContent).toContain('Morning');
    expect(tagOnly.container.querySelector('[data-cy="transactionCategory"]')).toBeNull();
  });

  it('localizes the read-only detail semantics in Spanish', async () => {
    registerSpanishTranslations();
    renderDetail(
      {
        ...configuredRuleResponse([{ id: 11, position: 0, field: 'FLOW', operator: 'EQUALS', value: 'OUT', caseSensitive: false }]),
        conditionLogic: 'ANY',
        active: true,
      },
      { locale: 'es' },
    );

    expect(
      await screen.findByText('Al menos una condición de abajo debe coincidir para que esta regla sugiera sus resultados.'),
    ).toBeTruthy();
    expect(screen.getByText('Flujo igual a Gasto')).toBeTruthy();
    expect(screen.getByTestId('transactionRuleDetailMultiRuleExplanation').textContent).toContain('se acumulan sin duplicados');
    expect(screen.getByTestId('transactionRuleDetailStatus').textContent).toContain('Activa');
  });

  it('keeps missing output identity and account catalog failures honest', async () => {
    const firstDetail = renderDetail(
      configuredRuleResponse([{ id: 15, position: 0, field: 'ACCOUNT', operator: 'EQUALS', value: '2', caseSensitive: false }]),
      { accountFailure: true },
    );
    expect(await screen.findByTestId('transactionRuleDetailAccountsUnavailable')).toBeTruthy();
    expect(screen.getByText('Account equals Account details unavailable')).toBeTruthy();

    firstDetail.unmount();
    renderDetail({ ...configuredRuleResponse(), resultingCategory: { id: 3 }, resultingTags: [{ id: 5 }] });
    expect(await screen.findByTestId('transactionRuleDetailOutputUnavailable')).toBeTruthy();
  });

  it('uses configured editor and product delete navigation without inline technical controls', async () => {
    renderDetail();
    const edit = await screen.findByTestId('transactionRuleDetailEdit');
    expect(edit.getAttribute('href')).toBe('/transaction-rule/1/edit');
    expect(screen.getByRole('link', { name: /delete/i }).getAttribute('href')).toBe('/transaction-rule/1/delete');
  });

  it('handles loading, missing and forbidden configured details', async () => {
    renderDetail('pending');
    expect(screen.getByTestId('transactionRuleDetailLoading')).toBeTruthy();

    renderDetail('notFound');
    expect(await screen.findByText('This classification rule was not found or is no longer available.')).toBeTruthy();

    renderDetail('forbidden');
    expect(await screen.findByText('You do not have access to this classification rule.')).toBeTruthy();
  });

  it('confirms product deletion, returns to the manager on success, and reports a failure', async () => {
    mockDispatch.mockImplementation(action =>
      action.type === 'transactionRule/deleteEntity' ? { unwrap: () => Promise.resolve() } : undefined,
    );
    renderDeleteDialog();
    expect(await screen.findByText(/affects future suggestions only/i)).toBeTruthy();
    fireEvent.click(screen.getByTestId('entityConfirmDeleteButton'));
    expect(mockDeleteEntity).toHaveBeenCalledWith(1);
    expect(await screen.findByText('Classification rules manager')).toBeTruthy();

    mockDispatch.mockImplementation(action =>
      action.type === 'transactionRule/deleteEntity' ? { unwrap: () => Promise.reject(new Error('failed')) } : undefined,
    );
    renderDeleteDialog();
    fireEvent.click(await screen.findByTestId('entityConfirmDeleteButton'));
    expect(await screen.findByTestId('transactionRuleDeleteError')).toBeTruthy();
  });
});
