import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enDescriptionNormalizationRule from 'app/../i18n/en/descriptionNormalizationRule.json';
import enRuleConditionLogic from 'app/../i18n/en/ruleConditionLogic.json';
import { DescriptionNormalizationRule } from './description-normalization-rule';
import { DescriptionNormalizationRuleDetail } from './description-normalization-rule-detail';
import { DescriptionNormalizationRuleUpdate } from './description-normalization-rule-update';

jest.mock('axios');

const mockAxiosGet = axios.get as jest.Mock;
const mockAxiosPost = axios.post as jest.Mock;
const mockAxiosPut = axios.put as jest.Mock;
const mockDispatch = jest.fn();
const mockGetEntities = jest.fn(params => ({ type: 'descriptionNormalizationRule/getEntities', payload: params }));
const mockGetEntity = jest.fn(id => ({ type: 'descriptionNormalizationRule/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'descriptionNormalizationRule/reset' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./description-normalization-rule.reducer', () => ({
  getEntities: params => mockGetEntities(params),
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
}));

const baseRule = {
  id: 1,
  name: 'Normalize Uber',
  description: 'Imported Uber rows',
  active: true,
  priority: 0,
  conditionOperator: 'ALL',
  resultingDescription: 'Uber',
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enDescriptionNormalizationRule);
  TranslatorContext.registerTranslations('en', enRuleConditionLogic);
  TranslatorContext.setLocale('en');
};

beforeEach(() => {
  jest.clearAllMocks();
  registerTranslations();
  mockState = {
    descriptionNormalizationRule: {
      entity: baseRule,
      entities: [baseRule],
      loading: false,
      updating: false,
      updateSuccess: false,
    },
  };
  mockAxiosGet.mockResolvedValue({ data: [] });
  mockAxiosPost.mockResolvedValue({ data: {} });
  mockAxiosPut.mockResolvedValue({ data: {} });
});

describe('DescriptionNormalizationRule UX', () => {
  it('list renders order, name, status, resulting description and actions', () => {
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule']}>
        <Routes>
          <Route path="/description-normalization-rule" element={<DescriptionNormalizationRule />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByText('#1')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Normalize Uber' })).toBeTruthy();
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    expect(screen.getByText('Uber')).toBeTruthy();
  });

  it('reorder controls call endpoint with full orderedIds', async () => {
    mockState.descriptionNormalizationRule.entities = [
      { ...baseRule, id: 1, name: 'First', priority: 0 },
      { ...baseRule, id: 2, name: 'Second', priority: 1 },
    ];

    render(
      <MemoryRouter initialEntries={['/description-normalization-rule']}>
        <Routes>
          <Route path="/description-normalization-rule" element={<DescriptionNormalizationRule />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: /move down/i }));

    await waitFor(() => expect(mockAxiosPut).toHaveBeenCalledWith('api/description-normalization-rules/reorder', { orderedIds: [2, 1] }));
  });

  it('create starts inactive, omits priority, and atomically posts configured conditions', async () => {
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/new']}>
        <Routes>
          <Route path="/description-normalization-rule/new" element={<DescriptionNormalizationRuleUpdate />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.queryByLabelText(/priority/i)).toBeNull();
    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'Normalize Uber' } });
    fireEvent.change(screen.getByLabelText(/resulting description/i), { target: { value: 'Uber' } });
    fireEvent.click(screen.getByRole('button', { name: /add condition/i }));
    fireEvent.change(screen.getByLabelText(/^value$/i), { target: { value: 'UBER BV' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/description-normalization-rules/configured',
        expect.objectContaining({
          name: 'Normalize Uber',
          active: false,
          resultingDescription: 'Uber',
          conditions: [expect.objectContaining({ operator: 'CONTAINS', value: 'UBER BV', position: 0 })],
        }),
      ),
    );
  });

  it('edit loads the complete configured aggregate, supports condition changes, and sends the configured update', async () => {
    mockAxiosGet.mockResolvedValue({
      data: {
        ...baseRule,
        conditions: [
          { operator: 'CONTAINS', value: 'UBER', caseSensitive: false, position: 0 },
          { operator: 'REGEX', value: 'RIDE.*', caseSensitive: true, position: 1 },
        ],
      },
    });
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/1/edit']}>
        <Routes>
          <Route path="/description-normalization-rule/:id/edit" element={<DescriptionNormalizationRuleUpdate />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(mockAxiosGet).toHaveBeenCalledWith('api/description-normalization-rules/1/configured'));
    expect(screen.getByDisplayValue('Normalize Uber')).toBeTruthy();
    expect(screen.getByDisplayValue('Imported Uber rows')).toBeTruthy();
    expect(screen.getByDisplayValue('Uber')).toBeTruthy();
    expect(screen.getByLabelText(/condition logic/i)).toHaveProperty('value', 'ALL');
    expect(screen.getByLabelText(/active/i)).toHaveProperty('checked', true);

    expect(screen.getByDisplayValue('UBER')).toBeTruthy();
    expect(screen.getByDisplayValue('RIDE.*')).toBeTruthy();
    fireEvent.change(screen.getByLabelText(/condition logic/i), { target: { value: 'ANY' } });
    fireEvent.change(screen.getByDisplayValue('UBER'), { target: { value: 'UBER EATS' } });
    fireEvent.click(screen.getByTestId('deleteDescriptionNormalizationRuleCondition-1'));
    fireEvent.change(screen.getByLabelText(/resulting description/i), { target: { value: 'Uber food' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(mockAxiosPut).toHaveBeenCalledWith(
        'api/description-normalization-rules/1/configured',
        expect.objectContaining({
          name: 'Normalize Uber',
          active: true,
          conditionOperator: 'ANY',
          resultingDescription: 'Uber food',
          conditions: [expect.objectContaining({ operator: 'CONTAINS', value: 'UBER EATS', position: 0 })],
        }),
      ),
    );
    expect(mockAxiosPut.mock.calls[0][1]).not.toHaveProperty('priority');
  });

  it('adds conditions, changes ALL/ANY preview, and prevents an active rule without conditions client-side', async () => {
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/new']}>
        <Routes>
          <Route path="/description-normalization-rule/new" element={<DescriptionNormalizationRuleUpdate />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'Normalize Uber' } });
    fireEvent.change(screen.getByLabelText(/resulting description/i), { target: { value: 'Uber' } });
    fireEvent.click(screen.getByLabelText(/active/i));
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    expect(await screen.findByText('Active rules require at least one condition.')).toBeTruthy();
    expect(mockAxiosPost).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /add condition/i }));
    fireEvent.change(screen.getByLabelText(/^value$/i), { target: { value: 'UBER' } });
    expect(screen.getByText(/IF original description/i)).toBeTruthy();
    expect(screen.getByText(/contains “UBER”/i)).toBeTruthy();
    fireEvent.change(screen.getByLabelText(/condition logic/i), { target: { value: 'ANY' } });
    expect(screen.getByText(/Any condition/i)).toBeTruthy();
  });

  it('preserves an edit draft and displays backend save failures', async () => {
    mockAxiosGet.mockResolvedValue({ data: { ...baseRule, conditions: [{ operator: 'CONTAINS', value: 'UBER', caseSensitive: false }] } });
    mockAxiosPut.mockRejectedValue({ response: { data: { detail: 'Description normalization rule name already exists' } } });
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/1/edit']}>
        <Routes>
          <Route path="/description-normalization-rule/:id/edit" element={<DescriptionNormalizationRuleUpdate />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByDisplayValue('Normalize Uber')).toBeTruthy();
    fireEvent.change(screen.getByDisplayValue('Normalize Uber'), { target: { value: 'A retained draft' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    expect(await screen.findByText('Description normalization rule name already exists')).toBeTruthy();
    expect(screen.getByDisplayValue('A retained draft')).toBeTruthy();
  });

  it('detail renders embedded conditions editor without field selector', async () => {
    mockAxiosGet.mockResolvedValue({
      data: [{ id: 10, operator: 'CONTAINS', value: 'Uber', caseSensitive: false, position: 0 }],
    });

    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/1']}>
        <Routes>
          <Route path="/description-normalization-rule/:id" element={<DescriptionNormalizationRuleDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('CONTAINS')).toBeTruthy();
    expect(screen.queryByLabelText(/field/i)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /add condition/i }));
    expect(screen.getByLabelText(/operator/i)).toBeTruthy();
    expect(screen.getByLabelText(/^value$/i)).toBeTruthy();
    expect(screen.getByLabelText(/case sensitive/i)).toBeTruthy();
  });

  it('embedded condition form posts operator value and caseSensitive', async () => {
    render(
      <MemoryRouter initialEntries={['/description-normalization-rule/1']}>
        <Routes>
          <Route path="/description-normalization-rule/:id" element={<DescriptionNormalizationRuleDetail />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('button', { name: /add condition/i }));
    fireEvent.change(screen.getByLabelText(/^value$/i), { target: { value: 'Uber' } });
    fireEvent.click(screen.getByLabelText(/case sensitive/i));
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() =>
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/description-normalization-rule-conditions',
        expect.objectContaining({
          operator: 'CONTAINS',
          value: 'Uber',
          caseSensitive: true,
          descriptionNormalizationRule: { id: 1 },
        }),
      ),
    );
  });
});
