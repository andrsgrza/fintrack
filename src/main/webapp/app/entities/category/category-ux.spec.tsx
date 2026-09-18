import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom';
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

const renderDetail = (entity = categories[0], entities = categories) => {
  mockState = { ...baseState, category: { ...baseState.category, entity, entities } };
  return renderAt(`/category/${entity.id}`, '/category/:id', <CategoryDetail />);
};

const CategoryBreadcrumbDestination = () => {
  const { id } = useParams();
  return <div data-testid="categoryBreadcrumbDestination">{id}</div>;
};

const renderList = (entities = categories) => {
  mockState = { ...baseState, category: { ...baseState.category, entities } };
  return render(
    <MemoryRouter initialEntries={['/category']}>
      <Routes>
        <Route path="/category" element={<Category />} />
        <Route path="/category/:id" element={<CategoryBreadcrumbDestination />} />
      </Routes>
    </MemoryRouter>,
  );
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

  it('renders the default nested hierarchy with visual color accents and no raw hex list metadata', () => {
    renderList();

    expect(screen.getByText('Getting around town')).toBeTruthy();
    const parentRow = screen.getByText('Transport').closest('[data-cy="entityTable"]');
    const childRow = screen.getByText('Bus').closest('[data-cy="entityTable"]');
    const leafRow = screen.getByText('Salary').closest('[data-cy="entityTable"]');
    const parentDisclosureSlot = parentRow?.querySelector('[data-cy="categoryDisclosureSlot"]');
    const leafDisclosureSlot = leafRow?.querySelector('[data-cy="categoryDisclosureSlot"]');

    expect(childRow?.getAttribute('data-category-depth')).toBe('1');
    expect(screen.getByRole('button', { name: 'Collapse Transport' }).getAttribute('aria-expanded')).toBe('true');
    expect(parentDisclosureSlot?.className).toContain('category-disclosure-slot');
    expect(parentDisclosureSlot?.querySelector('[data-cy="categoryDisclosure"]')).toBeTruthy();
    expect(parentDisclosureSlot?.nextElementSibling?.getAttribute('data-cy')).toBe('categoryNameContent');
    expect(leafDisclosureSlot?.className).toContain('category-disclosure-slot');
    expect(leafDisclosureSlot?.querySelector('[data-cy="categoryDisclosure"]')).toBeNull();
    expect(leafDisclosureSlot?.querySelector('[data-cy="categoryDisclosurePlaceholder"]')).toBeTruthy();
    expect(leafDisclosureSlot?.nextElementSibling?.getAttribute('data-cy')).toBe('categoryNameContent');
    expect(childRow?.querySelector('[data-cy="categoryDisclosure"]')).toBeNull();

    expect(screen.queryByText('└')).toBeNull();
    expect(document.querySelector('[data-cy="categoryHierarchyConnector"]')).toBeNull();
    expect(screen.getAllByText('Expense').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.queryByText('#123456')).toBeNull();
    expect(screen.getByTestId('categoryProductList')).toBeTruthy();
    expect(screen.getAllByTestId('categoryTypeBadge').length).toBeGreaterThan(0);
    expect(screen.getAllByTestId('categoryStatusBadge').length).toBeGreaterThan(0);
    expect(screen.getAllByLabelText('More actions').length).toBeGreaterThan(0);
    expect(document.querySelector('.btn-group.flex-btn-group-container')).toBeNull();

    expect(screen.queryByText('ID')).toBeNull();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('Updated At')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();
    expect(screen.queryByText('Budgets')).toBeNull();
    expect(screen.queryByText('true')).toBeNull();
    expect(screen.queryByText('false')).toBeNull();

    const disclosureSlotClass = parentDisclosureSlot?.className;
    fireEvent.click(screen.getByRole('button', { name: 'Collapse Transport' }));
    expect(screen.getByRole('button', { name: 'Expand Transport' }).getAttribute('aria-expanded')).toBe('false');
    expect(parentDisclosureSlot?.className).toBe(disclosureSlotClass);
    expect(parentDisclosureSlot?.nextElementSibling?.getAttribute('data-cy')).toBe('categoryNameContent');
  });

  it('supports accessible nested and flat hierarchy views without ASCII tree decoration', () => {
    const hierarchyCategories = [
      { id: 10, name: 'Expenses', categoryType: 'EXPENSE', color: '#123456', active: true },
      {
        id: 11,
        name: 'Transport',
        categoryType: 'EXPENSE',
        color: '#234567',
        active: true,
        parentCategory: { id: 10, name: 'Expenses', categoryType: 'EXPENSE' },
      },
      {
        id: 12,
        name: 'RideApps',
        categoryType: 'EXPENSE',
        color: '#345678',
        active: true,
        parentCategory: { id: 11, name: 'Transport', categoryType: 'EXPENSE' },
      },
      {
        id: 13,
        name: 'Uber Black',
        categoryType: 'EXPENSE',
        color: '#456789',
        active: true,
        parentCategory: { id: 12, name: 'RideApps', categoryType: 'EXPENSE' },
      },
      { id: 14, name: 'Income', categoryType: 'INCOME', color: '#56789a', active: true },
    ];
    renderList(hierarchyCategories);

    const nestedRows = screen.getAllByTestId('categoryProductList')[0].querySelectorAll('[data-cy="entityTable"]');
    const nestedNames = Array.from(nestedRows).map(row => row.querySelector('[data-cy="entityDetailsLink"]')?.textContent);
    expect(nestedNames).toEqual(['Expenses', 'Transport', 'RideApps', 'Uber Black', 'Income']);
    expect(screen.getByText('Transport').closest('[data-cy="entityTable"]')?.getAttribute('data-category-depth')).toBe('1');
    expect(screen.getByText('Uber Black').closest('[data-cy="entityTable"]')?.getAttribute('data-category-depth')).toBe('3');
    expect(screen.getByRole('button', { name: 'Collapse Expenses' }).getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByRole('button', { name: 'Collapse RideApps' })).toBeTruthy();
    expect(screen.queryByText('└')).toBeNull();
    expect(document.querySelector('[data-cy="categoryHierarchyConnector"]')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Collapse Expenses' }));
    expect(screen.getByRole('button', { name: 'Expand Expenses' }).getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByText('Transport')).toBeNull();
    expect(screen.queryByText('Uber Black')).toBeNull();
    expect(screen.getByRole('link', { name: 'Income' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Expand Expenses' }));
    expect(screen.getByText('Uber Black')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Flat' }));
    expect(screen.getByRole('button', { name: 'Flat' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: 'Nested' }).getAttribute('aria-pressed')).toBe('false');
    const flatPaths = Array.from(document.querySelectorAll('[data-cy="categoryFlatPath"]')) as HTMLElement[];
    expect(flatPaths.map(path => path.textContent)).toContain('Expenses › Transport');
    expect(flatPaths.map(path => path.textContent)).toContain('Expenses › Transport › RideApps › Uber Black');
    expect(screen.queryByText('Expenses > Transport')).toBeNull();
    expect(screen.getAllByTestId('categoryProductList')[0].querySelectorAll('[data-category-depth="0"]')).toHaveLength(5);
    expect(document.querySelector('[data-cy="categoryDisclosure"]')).toBeNull();

    const deepestFlatPath = flatPaths.find(path => path.textContent === 'Expenses › Transport › RideApps › Uber Black') as HTMLElement;
    const ancestorLinks = Array.from(deepestFlatPath.querySelectorAll('[data-cy="categoryBreadcrumbAncestor"]')) as HTMLAnchorElement[];
    expect(ancestorLinks.map(link => link.textContent)).toEqual(['Expenses', 'Transport', 'RideApps']);
    expect(ancestorLinks.map(link => link.getAttribute('href'))).toEqual(['/category/10', '/category/11', '/category/12']);
    expect(deepestFlatPath.querySelector('a[href="/category/13"]')).toBeNull();
    expect(ancestorLinks[1].className).toContain('category-breadcrumb-link');
    expect(ancestorLinks[1].className).not.toContain('text-primary');

    fireEvent.click(screen.getByRole('button', { name: 'Nested' }));
    expect(screen.getByText('Uber Black').closest('[data-cy="entityTable"]')?.getAttribute('data-category-depth')).toBe('3');
  });

  it('navigates to a flat breadcrumb ancestor without navigating to the current child category', () => {
    renderList();

    fireEvent.click(screen.getByRole('button', { name: 'Flat' }));
    const childPath = Array.from(document.querySelectorAll('[data-cy="categoryFlatPath"]')).find(
      path => path.textContent === 'Transport › Bus',
    ) as HTMLElement;
    const parentLink = childPath.querySelector('[data-cy="categoryBreadcrumbAncestor"]') as HTMLAnchorElement;
    expect(parentLink.getAttribute('href')).toBe('/category/1');
    expect(childPath.querySelector('a[href="/category/3"]')).toBeNull();

    fireEvent.click(parentLink);
    expect(screen.getByTestId('categoryBreadcrumbDestination').textContent).toBe('1');
  });

  it('requests the selected sort while preserving hierarchy grouping in nested mode and source ordering in flat mode', () => {
    renderList();

    fireEvent.click(screen.getByRole('button', { name: /Sort by name/ }));
    expect(mockGetEntities).toHaveBeenLastCalledWith({ sort: 'name,desc' });
    const nestedRows = screen.getAllByTestId('categoryProductList')[0].querySelectorAll('[data-cy="entityTable"]');
    const nestedNames = Array.from(nestedRows).map(row => row.querySelector('[data-cy="entityDetailsLink"]')?.textContent);
    expect(nestedNames.indexOf('Transport')).toBeLessThan(nestedNames.indexOf('Bus'));

    fireEvent.click(screen.getByRole('button', { name: 'Flat' }));
    const flatNames = screen.getAllByTestId('categoryProductList')[0].querySelectorAll('[data-cy="entityDetailsLink"]');
    expect(Array.from(flatNames).map(link => link.textContent)).toEqual(categories.map(category => category.name));
  });

  it('uses the detail header for color identity and a keyboard-accessible non-obstructive status tooltip', async () => {
    const rootView = renderDetail();

    expect(screen.getByText('Getting around town')).toBeTruthy();
    expect(screen.getByTestId('categoryDetailColorAccent').getAttribute('style')).toContain('#123456');
    expect(screen.queryByTestId('categoryDetailAppearance')).toBeNull();
    expect(screen.queryByText('#123456')).toBeNull();
    expect(screen.getByRole('heading', { name: /Subcategories/ }).textContent).toContain('(1)');
    const inactiveChildLink = screen.getByRole('link', { name: 'Bus' }) as HTMLAnchorElement;
    expect(inactiveChildLink.getAttribute('href')).toBe('/category/3');
    expect(inactiveChildLink.className).toContain('category-child-link');
    expect(inactiveChildLink.className).not.toContain('text-primary');
    const inactiveChildRow = inactiveChildLink.closest('[data-cy="categoryChildRow"]');
    expect(inactiveChildRow?.querySelector('[data-cy="categoryStatusBadge"]')).toBeTruthy();
    expect(screen.getByTestId('categoryDetailDescription')).toBeTruthy();
    expect(screen.getByTestId('categoryDetailClassification')).toBeTruthy();
    expect(screen.getByTestId('categoryDetailHierarchy')).toBeTruthy();
    expect(screen.queryByText('Created At')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();

    rootView.unmount();
    renderDetail(categories[2]);
    const statusHelpButton = screen.getByTestId('categoryDetailStatusHelpButton');
    fireEvent.focus(statusHelpButton);
    await waitFor(() => expect(screen.getByTestId('categoryDetailStatusHelpTooltip').closest('.tooltip')).toBeTruthy());
    expect(screen.queryByTestId('categoryInactiveExplanation')).toBeNull();
    expect(screen.getByTestId('categoryPath').textContent).toBe('Transport › Bus');
  });

  it('keeps both active and inactive direct child names navigable while status stays separate', () => {
    const activeChild = {
      id: 4,
      name: 'Taxi',
      categoryType: 'EXPENSE',
      color: '#111111',
      active: true,
      parentCategory: { id: 1, name: 'Transport', categoryType: 'EXPENSE' },
    };
    renderDetail(categories[0], [...categories, activeChild]);

    const links = ['Bus', 'Taxi'].map(name => screen.getByRole('link', { name }) as HTMLAnchorElement);
    expect(links.map(link => link.getAttribute('href'))).toEqual(['/category/3', '/category/4']);
    links.forEach(link => {
      const childRow = link.closest('[data-cy="categoryChildRow"]');
      expect(childRow?.querySelector('[data-cy="categoryStatusBadge"]')).toBeTruthy();
      expect(link.className).toContain('category-child-link');
      expect(link.className).not.toContain('text-primary');
    });
  });

  it('creates a category with description and a hierarchical, type-compatible parent selector', async () => {
    renderCreateForm();

    expect(screen.getByLabelText('Description')).toBeTruthy();
    expect(screen.getByTestId('categoryBasicInformationSection')).toBeTruthy();
    expect(screen.getByTestId('categoryClassificationSection')).toBeTruthy();
    expect(screen.getByTestId('categoryColorSection')).toBeTruthy();
    expect(screen.queryByTestId('categoryAppearanceSection')).toBeNull();
    expect(screen.queryByTestId('categoryStatusSection')).toBeNull();
    expect(screen.getByTestId('categoryStatusControl')).toBeTruthy();
    expect(screen.getByTestId('categoryStatusHelpButton')).toBeTruthy();
    expect(screen.getByTestId('categoryColorControl')).toBeTruthy();
    expect(screen.getByLabelText('Choose color')).toBeTruthy();
    expect(screen.getByLabelText('Color')).toBeTruthy();
    const colorControlRow = screen.getByTestId('categoryColorControlRow');
    expect(colorControlRow.querySelector('[data-cy="categoryColorPicker"]')).toBeTruthy();
    expect(colorControlRow.querySelector('[data-cy="color"]')).toBeTruthy();
    expect(document.querySelector('[data-cy="categoryColorPreview"]')).toBeNull();
    expect(screen.queryByLabelText('Icon')).toBeNull();
    expect((screen.getByLabelText('Active') as HTMLInputElement).parentElement?.className).toContain('form-switch');
    const parentSelect = screen.getByLabelText('Parent category') as HTMLSelectElement;
    expect(Array.from(parentSelect.options).map(option => option.textContent)).toEqual(['', 'Transport', 'Transport › Bus']);

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

  it('keeps an editable root type selection and synchronizes the compact picker and hex input', async () => {
    renderEditForm(categories[1]);

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'BOTH' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#112233' } });
    expect((screen.getByLabelText('Choose color') as HTMLInputElement).value).toBe('#112233');
    expect(document.querySelector('[data-cy="categoryColorPreview"]')).toBeNull();
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
