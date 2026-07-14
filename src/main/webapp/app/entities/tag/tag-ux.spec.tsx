import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enTag from 'app/../i18n/en/tag.json';
import { Tag } from './tag';
import { TagDetail } from './tag-detail';
import { TagUpdate } from './tag-update';

const mockDispatch = jest.fn();
const mockCreateEntity = jest.fn(entity => ({ type: 'tag/createEntity', payload: { data: { id: 99, ...entity } } }));
const mockPartialUpdateEntity = jest.fn(entity => ({ type: 'tag/partialUpdateEntity', payload: { data: entity } }));
const mockGetEntity = jest.fn(id => ({ type: 'tag/getEntity', payload: id }));
const mockGetEntities = jest.fn(params => ({ type: 'tag/getEntities', payload: params }));
const mockReset = jest.fn(() => ({ type: 'tag/reset' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./tag.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
  getEntity: id => mockGetEntity(id),
  getEntities: params => mockGetEntities(params),
  reset: () => mockReset(),
}));

const tags = [
  {
    id: 1,
    name: 'Groceries',
    description: 'Food shopping',
    color: '#123456',
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    user: { id: 7, login: 'user' },
    financialTransactions: [{ id: 10 }],
    transactionRules: [{ id: 20 }],
    subscriptions: [{ id: 30 }],
    budgets: [{ id: 40 }],
  },
  {
    id: 2,
    name: 'Travel',
    description: 'Trips',
    color: '#654321',
    active: false,
    user: { id: 7, login: 'user' },
  },
];

const baseState = {
  tag: {
    entities: tags,
    entity: {},
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enTag);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = {
    ...baseState,
    tag: {
      ...baseState.tag,
      entity: {},
    },
  };

  return render(
    <MemoryRouter initialEntries={['/tag/new']}>
      <Routes>
        <Route path="/tag/new" element={<TagUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = (entity = tags[0]) => {
  mockState = {
    ...baseState,
    tag: {
      ...baseState.tag,
      entity,
    },
  };

  return render(
    <MemoryRouter initialEntries={[`/tag/${entity.id}/edit`]}>
      <Routes>
        <Route path="/tag/:id/edit" element={<TagUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = {
    ...baseState,
    tag: {
      ...baseState.tag,
      entity: tags[0],
    },
  };

  return render(
    <MemoryRouter initialEntries={['/tag/1']}>
      <Routes>
        <Route path="/tag/:id" element={<TagDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderList = () => {
  mockState = {
    ...baseState,
    tag: {
      ...baseState.tag,
      entities: tags,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/tag']}>
      <Routes>
        <Route path="/tag" element={<Tag />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('Tag CRUD UX cleanup', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
    mockDispatch.mockImplementation(action => action);
    mockCreateEntity.mockClear();
    mockPartialUpdateEntity.mockClear();
    mockGetEntity.mockClear();
    mockGetEntities.mockClear();
    mockReset.mockClear();
  });

  it('create form shows editable catalog fields only and defaults active to true', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Name')).toBeTruthy();
    expect(screen.getByLabelText('Description')).toBeTruthy();
    expect(screen.getByLabelText('Color')).toBeTruthy();
    expect((screen.getByLabelText('Active') as HTMLInputElement).checked).toBe(true);

    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Financial Transactions')).toBeNull();
    expect(screen.queryByLabelText('Transaction Rules')).toBeNull();
    expect(screen.queryByLabelText('Subscriptions')).toBeNull();
    expect(screen.queryByLabelText('Budgets')).toBeNull();
    expect(screen.queryByLabelText('User')).toBeNull();
  });

  it('create submit sends editable fields without timestamps or relationship fields', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Fuel' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Gas stations' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#000000' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    const payload = mockCreateEntity.mock.calls[0][0];
    expect(payload).toEqual(
      expect.objectContaining({
        name: 'Fuel',
        description: 'Gas stations',
        color: '#000000',
        active: true,
      }),
    );
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
    expect(payload).not.toHaveProperty('financialTransactions');
    expect(payload).not.toHaveProperty('transactionRules');
    expect(payload).not.toHaveProperty('subscriptions');
    expect(payload).not.toHaveProperty('budgets');
    expect(payload).not.toHaveProperty('user');
  });

  it('edit form shows only editable catalog fields', () => {
    renderEditForm();

    expect(screen.getByLabelText('Name')).toBeTruthy();
    expect(screen.getByLabelText('Description')).toBeTruthy();
    expect(screen.getByLabelText('Color')).toBeTruthy();
    expect(screen.getByLabelText('Active')).toBeTruthy();

    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Financial Transactions')).toBeNull();
    expect(screen.queryByLabelText('Transaction Rules')).toBeNull();
    expect(screen.queryByLabelText('Subscriptions')).toBeNull();
    expect(screen.queryByLabelText('Budgets')).toBeNull();
    expect(screen.queryByLabelText('User')).toBeNull();
  });

  it('edit submit uses PATCH payload with editable fields only', async () => {
    renderEditForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Supermarket' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Weekly groceries' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockPartialUpdateEntity).toHaveBeenCalled());
    const payload = mockPartialUpdateEntity.mock.calls[0][0];
    expect(payload).toEqual({
      id: 1,
      name: 'Supermarket',
      description: 'Weekly groceries',
      color: '#123456',
      active: true,
    });
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
    expect(payload).not.toHaveProperty('financialTransactions');
    expect(payload).not.toHaveProperty('transactionRules');
    expect(payload).not.toHaveProperty('subscriptions');
    expect(payload).not.toHaveProperty('budgets');
    expect(payload).not.toHaveProperty('user');
  });

  it('detail shows clean tag fields without technical or relationship data', () => {
    renderDetail();

    expect(screen.getByText('Groceries')).toBeTruthy();
    expect(screen.getByText('Food shopping')).toBeTruthy();
    expect(screen.getByText('#123456')).toBeTruthy();
    expect(screen.getByText('true')).toBeTruthy();

    expect(screen.queryByText('ID')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('Financial Transactions')).toBeNull();
    expect(screen.queryByText('Transaction Rules')).toBeNull();
    expect(screen.queryByText('Subscriptions')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();
  });

  it('list shows catalog columns without raw ids, timestamps, user, or relationship columns', () => {
    renderList();

    expect(screen.getByText('Name')).toBeTruthy();
    expect(screen.getByText('Description')).toBeTruthy();
    expect(screen.getByText('Color')).toBeTruthy();
    expect(screen.getByText('Active')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Groceries' })).toBeTruthy();

    expect(screen.queryByText('ID')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('Financial Transactions')).toBeNull();
    expect(screen.queryByText('Transaction Rules')).toBeNull();
    expect(screen.queryByText('Subscriptions')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();
  });
});
