import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enDescriptionNormalizationRule from 'app/../i18n/en/descriptionNormalizationRule.json';
import enRuleConditionLogic from 'app/../i18n/en/ruleConditionLogic.json';
import enRules from 'app/../i18n/en/rules.json';
import esDescriptionNormalizationRule from 'app/../i18n/es/descriptionNormalizationRule.json';
import esRuleConditionLogic from 'app/../i18n/es/ruleConditionLogic.json';
import esRules from 'app/../i18n/es/rules.json';
import { DescriptionNormalizationRule } from './description-normalization-rule';
import { DescriptionNormalizationRuleDeleteDialog } from './description-normalization-rule-delete-dialog';
import { DescriptionNormalizationRuleDetail } from './description-normalization-rule-detail';
import { DescriptionNormalizationRuleUpdate } from './description-normalization-rule-update';

jest.mock('axios');
jest.mock('react-toastify', () => ({ toast: { success: jest.fn() } }));
jest.mock('@dnd-kit/core', () => ({
  DndContext: ({ children, onDragEnd }) => (
    <div>
      <button type="button" data-testid="dnd-drop-first-on-second" onClick={() => onDragEnd({ active: { id: 1 }, over: { id: 2 } })}>
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
const mockAxiosDelete = axios.delete as jest.Mock;
let mockState: any;

const firstRule = {
  id: 1,
  name: 'Normalize Uber',
  description: 'Imported Uber rows',
  active: true,
  priority: 0,
  conditionOperator: 'ALL',
  resultingDescription: 'Uber',
};
const secondRule = {
  id: 2,
  name: 'Normalize Oxxo',
  active: false,
  priority: 1,
  conditionOperator: 'ANY',
  resultingDescription: 'OXXO',
};

jest.mock('app/config/store', () => ({
  useAppSelector: selector => selector(mockState),
}));

const registerTranslations = (locale = 'en') => {
  TranslatorContext.registerTranslations('en', enDescriptionNormalizationRule);
  TranslatorContext.registerTranslations('en', enRuleConditionLogic);
  TranslatorContext.registerTranslations('en', enRules);
  TranslatorContext.registerTranslations('es', esDescriptionNormalizationRule);
  TranslatorContext.registerTranslations('es', esRuleConditionLogic);
  TranslatorContext.registerTranslations('es', esRules);
  TranslatorContext.setLocale(locale);
};

const mockRuleList = (rules = [firstRule, secondRule]) => {
  mockAxiosGet.mockImplementation(url => {
    if (String(url).includes('?size=1000')) return Promise.resolve({ data: rules });
    if (String(url).endsWith('/1/conditions')) {
      return Promise.resolve({
        data: [
          { operator: 'CONTAINS', value: 'UBER', caseSensitive: false, position: 0 },
          { operator: 'NOT_CONTAINS', value: 'EATS', caseSensitive: true, position: 1 },
          { operator: 'REGEX', value: 'RIDE.*', caseSensitive: false, position: 2 },
        ],
      });
    }
    if (String(url).endsWith('/2/conditions')) {
      return Promise.resolve({ data: [{ operator: 'EXACT', value: 'OXXO', caseSensitive: false, position: 0 }] });
    }
    return Promise.resolve({ data: [] });
  });
};

const renderList = () =>
  render(
    <MemoryRouter initialEntries={['/description-normalization-rule']}>
      <Routes>
        <Route path="/description-normalization-rule" element={<DescriptionNormalizationRule />} />
      </Routes>
    </MemoryRouter>,
  );

beforeEach(() => {
  jest.clearAllMocks();
  registerTranslations();
  mockState = { authentication: { account: { authorities: ['ROLE_USER'] } } };
  mockAxiosPost.mockResolvedValue({ data: {} });
  mockAxiosPut.mockResolvedValue({ data: {} });
  mockAxiosDelete.mockResolvedValue({ data: {} });
  mockRuleList();
});

describe('DescriptionNormalizationRule product UX', () => {
  it('renders priority order, active state, complete ALL/ANY semantics, results, and inactive rules', async () => {
    mockRuleList([secondRule, firstRule]);
    renderList();

    expect(await screen.findByText('Normalize Uber')).toBeTruthy();
    expect(screen.getByText('Position 1')).toBeTruthy();
    expect(screen.getByText('Position 2')).toBeTruthy();
    expect(screen.getByText('Active')).toBeTruthy();
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.getByText(/All conditions must match:/)).toBeTruthy();
    expect(screen.getByText(/Any condition may match:/)).toBeTruthy();
    expect(screen.getByText(/contains “UBER” · case-insensitive/)).toBeTruthy();
    expect(screen.getByText(/and 1 more/)).toBeTruthy();
    expect(screen.getAllByText(/Then replace the description with/).length).toBe(2);
  });

  it('uses the complete owner rule sequence, including inactive rules, for an arrow reorder', async () => {
    renderList();
    await screen.findByText('Normalize Uber');

    fireEvent.click(screen.getByTestId('descriptionNormalizationRuleMoveDown-1'));

    await waitFor(() => expect(mockAxiosPut).toHaveBeenCalledWith('api/description-normalization-rules/reorder', { orderedIds: [2, 1] }));
  });

  it('keeps boundary arrows disabled and does not persist no-op moves', async () => {
    renderList();
    await screen.findByText('Normalize Uber');

    expect(screen.getByTestId('descriptionNormalizationRuleMoveUp-1')).toHaveProperty('disabled', true);
    expect(screen.getByTestId('descriptionNormalizationRuleMoveDown-2')).toHaveProperty('disabled', true);
    fireEvent.click(screen.getByTestId('descriptionNormalizationRuleMoveUp-1'));
    expect(mockAxiosPut).not.toHaveBeenCalled();
  });

  it('routes real drag completion through the same full reorder persistence contract', async () => {
    renderList();
    await screen.findByText('Normalize Uber');

    fireEvent.click(screen.getByTestId('dnd-drop-first-on-second'));

    await waitFor(() => expect(mockAxiosPut).toHaveBeenCalledWith('api/description-normalization-rules/reorder', { orderedIds: [2, 1] }));
  });

  it('restores the last confirmed order when reorder persistence fails', async () => {
    mockAxiosPut.mockRejectedValueOnce(new Error('failed'));
    renderList();
    await screen.findByText('Normalize Uber');

    fireEvent.click(screen.getByTestId('descriptionNormalizationRuleMoveDown-1'));

    expect(await screen.findByTestId('descriptionNormalizationRuleReorderError')).toBeTruthy();
    expect(screen.getByTestId('descriptionNormalizationRuleCard-1').textContent).toContain('Position 1');
  });

  it('hides reorder controls in the administrator mixed-owner context', async () => {
    mockState.authentication.account.authorities = ['ROLE_ADMIN'];
    renderList();

    expect(await screen.findByText('Normalize Uber')).toBeTruthy();
    expect(screen.getByTestId('descriptionNormalizationRuleAdminReorderUnavailable')).toBeTruthy();
    expect(screen.queryByTestId('descriptionNormalizationRuleDragHandle-1')).toBeNull();
    expect(screen.queryByTestId('descriptionNormalizationRuleMoveDown-1')).toBeNull();
  });

  it('shows an explanatory empty state and a create action', async () => {
    mockRuleList([]);
    renderList();

    expect(await screen.findByText('No description rules yet')).toBeTruthy();
    expect(screen.getByTestId('descriptionNormalizationRuleEmptyCreate')).toBeTruthy();
  });

  it('renders a read-only complete detail and routes editing to the configured editor', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...firstRule,
        conditions: [
          { operator: 'CONTAINS', value: 'UBER', caseSensitive: false, position: 0 },
          { operator: 'NOT_CONTAINS', value: 'EATS', caseSensitive: true, position: 1 },
        ],
      },
    });
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/1']}>
        <Routes>
          <Route path="/description-normalization-rule/:id" element={<DescriptionNormalizationRuleDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Normalize Uber')).toBeTruthy();
    expect(screen.getByText('Matching')).toBeTruthy();
    expect(screen.getByText(/Every condition below must match/)).toBeTruthy();
    expect(screen.getByText(/does not contain “EATS” · case-sensitive/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /add condition/i })).toBeNull();
    expect(screen.getByTestId('descriptionNormalizationRuleDetailEdit').getAttribute('href')).toBe(
      '/description-normalization-rule/1/edit',
    );
  });

  it('uses the configured aggregate for edit and returns to its read-only detail after saving', async () => {
    mockAxiosGet.mockResolvedValue({
      data: { ...firstRule, conditions: [{ operator: 'CONTAINS', value: 'UBER', caseSensitive: false, position: 0 }] },
    });
    mockAxiosPut.mockResolvedValue({
      data: { ...firstRule, conditions: [{ operator: 'CONTAINS', value: 'UBER EATS', caseSensitive: false }] },
    });
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/1/edit']}>
        <Routes>
          <Route path="/description-normalization-rule/:id/edit" element={<DescriptionNormalizationRuleUpdate />} />
          <Route path="/description-normalization-rule/:id" element={<div data-testid="rule-detail-destination" />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByDisplayValue('Normalize Uber')).toBeTruthy();
    fireEvent.change(screen.getByDisplayValue('UBER'), { target: { value: 'UBER EATS' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockAxiosPut).toHaveBeenCalledWith('api/description-normalization-rules/1/configured', expect.anything()));
    expect(await screen.findByTestId('rule-detail-destination')).toBeTruthy();
  });

  it('uses product deletion confirmation and reports a deletion failure without navigating away', async () => {
    mockAxiosGet.mockResolvedValue({ data: { ...firstRule, conditions: [] } });
    mockAxiosDelete.mockRejectedValueOnce(new Error('failed'));
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/1/delete']}>
        <Routes>
          <Route path="/description-normalization-rule/:id/delete" element={<DescriptionNormalizationRuleDeleteDialog />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText(/Deleting this rule removes it from future normalization evaluations/)).toBeTruthy();
    fireEvent.click(screen.getByTestId('entityConfirmDeleteButton'));
    await waitFor(() => expect(mockAxiosDelete).toHaveBeenCalledWith('api/description-normalization-rules/1'));
    expect(await screen.findByTestId('descriptionNormalizationRuleDeleteError')).toBeTruthy();
  });

  it('renders the product manager in Spanish', async () => {
    registerTranslations('es');
    renderList();

    expect(await screen.findByText('Reglas de normalización de descripción')).toBeTruthy();
    expect(screen.getByText('Orden de evaluación')).toBeTruthy();
    expect(await screen.findByText('Activa')).toBeTruthy();
  });
});
