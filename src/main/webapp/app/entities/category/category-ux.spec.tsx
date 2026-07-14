import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enCategory from 'app/../i18n/en/category.json';
import enCategoryType from 'app/../i18n/en/categoryType.json';
import { Category } from './category';
import { CategoryDetail } from './category-detail';
import { CategoryUpdate } from './category-update';

const mockDispatch = jest.fn();
const mockCreateEntity = jest.fn(entity => ({ type: 'category/createEntity', payload: { data: { id: 99, ...entity } } }));
const mockPartialUpdateEntity = jest.fn(entity => ({ type: 'category/partialUpdateEntity', payload: { data: entity } }));
const mockGetEntity = jest.fn(id => ({ type: 'category/getEntity', payload: id }));
const mockGetEntities = jest.fn(params => ({ type: 'category/getEntities', payload: params }));
const mockReset = jest.fn(() => ({ type: 'category/reset' }));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./category.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
  getEntity: id => mockGetEntity(id),
  getEntities: params => mockGetEntities(params),
  reset: () => mockReset(),
}));

jest.mock('app/entities/category/category.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
  getEntity: id => mockGetEntity(id),
  getEntities: params => mockGetEntities(params),
  reset: () => mockReset(),
}));

const categories = [
  {
    id: 1,
    name: 'Transport',
    categoryType: 'EXPENSE',
    color: '#123456',
    icon: 'bus',
    active: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
    user: { id: 7, login: 'user' },
  },
  {
    id: 2,
    name: 'Salary',
    categoryType: 'INCOME',
    color: '#654321',
    icon: 'money',
    active: true,
    user: { id: 7, login: 'user' },
  },
  {
    id: 3,
    name: 'Bus',
    categoryType: 'EXPENSE',
    color: '#abcdef',
    icon: 'ticket',
    active: true,
    parentCategory: { id: 1, name: 'Transport', categoryType: 'EXPENSE' },
    user: { id: 7, login: 'user' },
  },
];

const baseState = {
  category: {
    entities: categories,
    entity: {},
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enCategory);
  TranslatorContext.registerTranslations('en', enCategoryType);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = {
    ...baseState,
    category: {
      ...baseState.category,
      entity: {},
    },
  };

  return render(
    <MemoryRouter initialEntries={['/category/new']}>
      <Routes>
        <Route path="/category/new" element={<CategoryUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderEditForm = (entity = categories[2]) => {
  mockState = {
    ...baseState,
    category: {
      ...baseState.category,
      entity,
    },
  };

  return render(
    <MemoryRouter initialEntries={[`/category/${entity.id}/edit`]}>
      <Routes>
        <Route path="/category/:id/edit" element={<CategoryUpdate />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderDetail = () => {
  mockState = {
    ...baseState,
    category: {
      ...baseState.category,
      entity: categories[2],
    },
  };

  return render(
    <MemoryRouter initialEntries={['/category/3']}>
      <Routes>
        <Route path="/category/:id" element={<CategoryDetail />} />
      </Routes>
    </MemoryRouter>,
  );
};

const renderList = () => {
  mockState = {
    ...baseState,
    category: {
      ...baseState.category,
      entities: categories,
    },
  };

  return render(
    <MemoryRouter initialEntries={['/category']}>
      <Routes>
        <Route path="/category" element={<Category />} />
      </Routes>
    </MemoryRouter>,
  );
};

describe('Category CRUD UX cleanup', () => {
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

  it('create form hides technical/generated fields and defaults active to true', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Name')).toBeTruthy();
    expect(screen.getByLabelText('Type')).toBeTruthy();
    expect(screen.getByLabelText('Parent category')).toBeTruthy();
    expect(screen.getByLabelText('Color')).toBeTruthy();
    expect(screen.getByLabelText('Icon')).toBeTruthy();
    expect((screen.getByLabelText('Active') as HTMLInputElement).checked).toBe(true);

    expect(screen.queryByLabelText('User')).toBeNull();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Description')).toBeNull();
    expect(screen.queryByLabelText('Budgets')).toBeNull();
  });

  it('create form filters parent categories by selected category type', () => {
    renderCreateForm();

    const parentSelect = screen.getByLabelText('Parent category') as HTMLSelectElement;
    expect(Array.from(parentSelect.options).map(option => option.textContent)).toEqual(['', 'Transport', 'Bus']);

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'INCOME' } });

    expect(Array.from(parentSelect.options).map(option => option.textContent)).toEqual(['', 'Salary']);
  });

  it('create submit sends current-user-owned catalog fields and hidden timestamp defaults', async () => {
    renderCreateForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Fuel' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#000000' } });
    fireEvent.change(screen.getByLabelText('Icon'), { target: { value: 'gas-pump' } });
    fireEvent.change(screen.getByLabelText('Parent category'), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    const payload = mockCreateEntity.mock.calls[0][0];
    expect(payload.name).toBe('Fuel');
    expect(payload.categoryType).toBe('EXPENSE');
    expect(payload.color).toBe('#000000');
    expect(payload.icon).toBe('gas-pump');
    expect(payload.active).toBe(true);
    expect(payload.parentCategory).toEqual(expect.objectContaining({ id: 1, name: 'Transport' }));
    expect(payload.createdAt).toBeTruthy();
    expect(payload.updatedAt).toBeTruthy();
    expect(payload).not.toHaveProperty('user');
    expect(payload).not.toHaveProperty('budgets');
  });

  it('edit form hides technical fields and represents immutable parent/type constraints', () => {
    renderEditForm();

    expect(screen.queryByLabelText('User')).toBeNull();
    expect(screen.queryByLabelText('Created At')).toBeNull();
    expect(screen.queryByLabelText('Updated At')).toBeNull();
    expect(screen.queryByLabelText('Description')).toBeNull();
    expect(screen.queryByLabelText('Budgets')).toBeNull();

    expect((screen.getByLabelText('Type') as HTMLSelectElement).disabled).toBe(true);
    expect((screen.getByLabelText('Parent category') as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByDisplayValue('Transport')).toBeTruthy();
    expect(screen.queryByRole('option', { name: 'Bus' })).toBeNull();
    expect((screen.getByLabelText('Color') as HTMLInputElement).disabled).toBe(false);
    expect((screen.getByLabelText('Icon') as HTMLInputElement).disabled).toBe(false);
    expect((screen.getByLabelText('Active') as HTMLInputElement).disabled).toBe(false);
  });

  it('edit submit uses PATCH without user, timestamps, parent, or budget relationship editors', async () => {
    renderEditForm();

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Metro' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#111111' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(mockPartialUpdateEntity).toHaveBeenCalled());
    const payload = mockPartialUpdateEntity.mock.calls[0][0];
    expect(payload).toEqual(
      expect.objectContaining({
        id: 3,
        name: 'Metro',
        color: '#111111',
        icon: 'ticket',
        active: true,
      }),
    );
    expect(payload).not.toHaveProperty('user');
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
    expect(payload).not.toHaveProperty('parentCategory');
    expect(payload).not.toHaveProperty('budgets');
    expect(payload).not.toHaveProperty('categoryType');
  });

  it('detail shows clean category fields without technical/generated relationships', () => {
    renderDetail();

    expect(screen.getByText('Bus')).toBeTruthy();
    expect(screen.getByText('Expense')).toBeTruthy();
    expect(screen.getByText('Transport')).toBeTruthy();
    expect(screen.getByText('#abcdef')).toBeTruthy();
    expect(screen.getByText('ticket')).toBeTruthy();
    expect(screen.getByText('true')).toBeTruthy();

    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
  });

  it('list shows catalog columns without raw ids, timestamps, user, or budgets', () => {
    renderList();

    expect(screen.getByText('Name')).toBeTruthy();
    expect(screen.getByText('Type')).toBeTruthy();
    expect(screen.getByText('Parent category')).toBeTruthy();
    expect(screen.getByText('Color')).toBeTruthy();
    expect(screen.getByText('Icon')).toBeTruthy();
    expect(screen.getByText('Active')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: 'Transport' }).length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: 'Bus' })).toBeTruthy();

    expect(screen.queryByText('ID')).toBeNull();
    expect(screen.queryByText('Description')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
  });
});
