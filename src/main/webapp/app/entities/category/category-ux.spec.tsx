import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import axios from 'axios';

import enCategory from 'app/../i18n/en/category.json';
import enCategoryType from 'app/../i18n/en/categoryType.json';
import enGlobal from 'app/../i18n/en/global.json';
import { Category } from './category';
import { CategoryDeleteDialog } from './category-delete-dialog';
import { CategoryDetail } from './category-detail';
import { CategoryUpdate } from './category-update';

const mockDispatch = jest.fn();
const mockCreateEntity = jest.fn(entity => ({ type: 'category/create_entity', payload: { data: { id: 99, ...entity } } }));
const mockPartialUpdateEntity = jest.fn(entity => ({ type: 'category/partial_update_entity', payload: { data: entity } }));
const mockDeleteEntity = jest.fn(id => ({ type: 'category/delete_entity', payload: id }));
const mockGetEntity = jest.fn(id => ({ type: 'category/get_entity', payload: id }));
const mockGetEntities = jest.fn(params => ({ type: 'category/get_entities', payload: params }));
const mockReset = jest.fn(() => ({ type: 'category/reset' }));
const mockAxios = axios as jest.Mocked<typeof axios>;
let mockState: any;

jest.mock('axios');

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./category.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
  deleteEntity: id => mockDeleteEntity(id),
  getEntity: id => mockGetEntity(id),
  getEntities: params => mockGetEntities(params),
  reset: () => mockReset(),
}));

const categories = [
  {
    id: 1,
    name: 'Transport',
    description: 'Getting around town',
    categoryType: 'EXPENSE',
    color: '#123456',
    icon: 'bus',
    active: true,
  },
  {
    id: 2,
    name: 'Salary',
    description: 'Monthly pay',
    categoryType: 'INCOME',
    color: '#654321',
    icon: 'money',
    active: true,
  },
  {
    id: 3,
    name: 'Bus',
    description: 'Public bus rides',
    categoryType: 'EXPENSE',
    color: '#abcdef',
    icon: 'ticket',
    active: false,
    parentCategory: { id: 1, name: 'Transport', categoryType: 'EXPENSE' },
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
  TranslatorContext.registerTranslations('en', enGlobal);
  TranslatorContext.setLocale('en');
};

const renderAt = (path: string, route: string, element: React.ReactNode) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={route} element={element} />
        <Route path="/category" element={<div data-testid="categoryList">Category list</div>} />
      </Routes>
    </MemoryRouter>,
  );

const renderCreateForm = () => {
  mockState = { ...baseState, category: { ...baseState.category, entity: {} } };
  return renderAt('/category/new', '/category/new', <CategoryUpdate />);
};

const renderEditForm = (entity = categories[2]) => {
  mockState = { ...baseState, category: { ...baseState.category, entity } };
  return renderAt(`/category/${entity.id}/edit`, '/category/:id/edit', <CategoryUpdate />);
};

const renderDetail = (entity = categories[0]) => {
  mockState = { ...baseState, category: { ...baseState.category, entity } };
  return renderAt(`/category/${entity.id}`, '/category/:id', <CategoryDetail />);
};

const renderList = () => {
  mockState = { ...baseState, category: { ...baseState.category, entities: categories } };
  return renderAt('/category', '/category', <Category />);
};

const renderDeleteDialog = (entity = categories[0]) => {
  mockState = { ...baseState, category: { ...baseState.category, entity } };
  return renderAt(`/category/${entity.id}/delete`, '/category/:id/delete', <CategoryDeleteDialog />);
};

describe('Category product UX', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockDispatch.mockClear();
    mockDispatch.mockImplementation(action => Promise.resolve(action));
    mockCreateEntity.mockClear();
    mockPartialUpdateEntity.mockClear();
    mockDeleteEntity.mockClear();
    mockGetEntity.mockClear();
    mockGetEntities.mockClear();
    mockReset.mockClear();
    mockAxios.get.mockResolvedValue({ data: 0 } as any);
  });

  it('lists product fields with hierarchy, translated type, status badge, and visual appearance only', () => {
    renderList();

    expect(screen.getByText('Getting around town')).toBeTruthy();
    expect(screen.getAllByTestId('categoryPath').some(path => path.textContent === 'Transport > Bus')).toBe(true);
    expect(screen.getAllByText('Expense').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.getAllByText('#123456').length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText('Color swatch #123456').length).toBeGreaterThan(0);

    expect(screen.queryByText('ID')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
    expect(screen.queryByText('true')).toBeNull();
    expect(screen.queryByText('false')).toBeNull();
  });

  it('shows detail description, complete hierarchy, child summary, and inactive historical-only explanation', () => {
    const rootView = renderDetail();

    expect(screen.getByText('Getting around town')).toBeTruthy();
    expect(screen.getByRole('heading', { name: /Subcategories/ }).textContent).toContain('(1)');
    expect(screen.getByRole('link', { name: 'Bus' })).toBeTruthy();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();

    rootView.unmount();
    renderDetail(categories[2]);
    expect(screen.getByTestId('categoryInactiveExplanation').textContent).toContain(
      'This category is kept for historical records and cannot be newly assigned until it is reactivated.',
    );
    expect(screen.getByTestId('categoryPath').textContent).toBe('Transport > Bus');
  });

  it('creates a category with description and a hierarchical, type-compatible parent selector', async () => {
    renderCreateForm();

    expect(screen.getByLabelText('Description')).toBeTruthy();
    const parentSelect = screen.getByLabelText('Parent category') as HTMLSelectElement;
    expect(Array.from(parentSelect.options).map(option => option.textContent)).toEqual(['', 'Transport', 'Transport > Bus']);

    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Subway' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Metro rides' } });
    fireEvent.change(screen.getByLabelText('Parent category'), { target: { value: '1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    const payload = mockCreateEntity.mock.calls[0][0];
    expect(payload).toEqual(expect.objectContaining({ name: 'Subway', description: 'Metro rides', categoryType: 'EXPENSE', active: true }));
    expect(payload.parentCategory).toEqual(expect.objectContaining({ id: 1, name: 'Transport' }));
    expect(payload).not.toHaveProperty('user');
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
  });

  it('hydrates edit values and explains that the parent is immutable', () => {
    renderEditForm();

    expect(screen.getByDisplayValue('Public bus rides')).toBeTruthy();
    expect(screen.getByDisplayValue('Transport')).toBeTruthy();
    expect((screen.getByLabelText('Parent category') as HTMLInputElement).disabled).toBe(true);
    expect(screen.getByText('Parent category cannot be changed after creation.')).toBeTruthy();
    expect((screen.getByLabelText('Type') as HTMLSelectElement).disabled).toBe(true);
    expect(screen.getByText('Use this category for outgoing transactions.')).toBeTruthy();
  });

  it('uses PATCH for editable values, including description, without technical fields', async () => {
    renderEditForm(categories[0]);

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Travel and transport' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#111111' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mockPartialUpdateEntity).toHaveBeenCalled());
    const payload = mockPartialUpdateEntity.mock.calls[0][0];
    expect(payload).toEqual(expect.objectContaining({ id: 1, description: 'Travel and transport', color: '#111111' }));
    expect(payload).not.toHaveProperty('parentCategory');
    expect(payload).not.toHaveProperty('user');
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
  });

  it('keeps an editable root type selection and updates the color preview', async () => {
    renderEditForm(categories[1]);

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'BOTH' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#112233' } });
    expect(screen.getByLabelText('Color swatch #112233')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mockPartialUpdateEntity).toHaveBeenCalled());
    expect(mockPartialUpdateEntity.mock.calls[0][0]).toEqual(expect.objectContaining({ id: 2, categoryType: 'BOTH', color: '#112233' }));
  });

  it('maps a category type conflict to a product-safe validation message', async () => {
    renderEditForm(categories[0]);
    mockDispatch.mockImplementation(action =>
      Promise.resolve(
        action.type === 'category/partial_update_entity'
          ? {
              type: 'category/partial_update_entity/rejected',
              error: { message: 'Category type cannot be changed while category is in use' },
            }
          : action,
      ),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect((await screen.findByTestId('categorySaveError')).textContent).toContain(
      'This category type cannot be changed while the category is in use.',
    );
    expect(screen.queryByText('Category type cannot be changed while category is in use')).toBeNull();
  });

  it('shows a clear pre-delete child block with the category name', async () => {
    mockAxios.get.mockResolvedValue({ data: 1 } as any);
    renderDeleteDialog();

    expect((await screen.findByTestId('categoryDeleteBlockedMessage')).textContent).toContain(
      'This category has subcategories. Delete or move those subcategories before trying again.',
    );
    expect(screen.getByText('Are you sure you want to delete Transport?')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Delete' })).toBeNull();
  });

  it('maps a candidate-reference delete rejection without displaying the backend message', async () => {
    mockAxios.get.mockResolvedValue({ data: 0 } as any);
    renderDeleteDialog(categories[2]);
    await waitFor(() => expect(screen.getByTestId('categoryDeleteLeafMessage')).toBeTruthy());
    mockDispatch.mockImplementation(action =>
      Promise.resolve(
        action.type === 'category/delete_entity'
          ? {
              type: 'category/delete_entity/rejected',
              error: { message: 'Category cannot be deleted because it is used by transaction candidates.' },
            }
          : action,
      ),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    expect((await screen.findByTestId('categoryDeleteError')).textContent).toContain(
      'This category cannot be deleted because it is still needed by an active transaction workflow. Resolve that workflow first.',
    );
    expect(screen.queryByText('Category cannot be deleted because it is used by transaction candidates.')).toBeNull();
  });
});
