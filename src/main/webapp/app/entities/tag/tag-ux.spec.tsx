import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import enGlobal from 'app/../i18n/en/global.json';
import enTag from 'app/../i18n/en/tag.json';
import { Tag } from './tag';
import { TagDeleteDialog } from './tag-delete-dialog';
import { TagDetail } from './tag-detail';
import { TagUpdate } from './tag-update';

const mockDispatch = jest.fn();
const mockCreateEntity = jest.fn(entity => ({ type: 'tag/create_entity', payload: { data: { id: 99, ...entity } } }));
const mockPartialUpdateEntity = jest.fn(entity => ({ type: 'tag/partial_update_entity', payload: { data: entity } }));
const mockDeleteEntity = jest.fn(id => ({ type: 'tag/delete_entity', payload: id }));
const mockGetEntity = jest.fn(id => ({ type: 'tag/get_entity', payload: id }));
const mockGetEntities = jest.fn(params => ({ type: 'tag/get_entities', payload: params }));
const mockReset = jest.fn(() => ({ type: 'tag/reset' }));
let mockState: any;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./tag.reducer', () => ({
  createEntity: entity => mockCreateEntity(entity),
  partialUpdateEntity: entity => mockPartialUpdateEntity(entity),
  deleteEntity: id => mockDeleteEntity(id),
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
  TranslatorContext.registerTranslations('en', enGlobal);
  TranslatorContext.setLocale('en');
};

const renderAt = (path: string, route: string, element: React.ReactNode) =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={route} element={element} />
        <Route path="/tag" element={<div data-testid="tagList">Tag list</div>} />
      </Routes>
    </MemoryRouter>,
  );

const renderCreateForm = () => {
  mockState = { ...baseState, tag: { ...baseState.tag, entity: {} } };
  return renderAt('/tag/new', '/tag/new', <TagUpdate />);
};

const renderEditForm = (entity = tags[0]) => {
  mockState = { ...baseState, tag: { ...baseState.tag, entity } };
  return renderAt(`/tag/${entity.id}/edit`, '/tag/:id/edit', <TagUpdate />);
};

const renderDetail = (entity = tags[0]) => {
  mockState = { ...baseState, tag: { ...baseState.tag, entity } };
  return renderAt(`/tag/${entity.id}`, '/tag/:id', <TagDetail />);
};

const renderList = () => {
  mockState = { ...baseState, tag: { ...baseState.tag, entities: tags } };
  return renderAt('/tag', '/tag', <Tag />);
};

const renderDeleteDialog = (entity = tags[0]) => {
  mockState = { ...baseState, tag: { ...baseState.tag, entity } };
  return renderAt(`/tag/${entity.id}/delete`, '/tag/:id/delete', <TagDeleteDialog />);
};

describe('Tag product UX', () => {
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
  });

  it('renders compact tag identity and status together, with description as secondary content', () => {
    renderList();

    expect(screen.getByText('Food shopping')).toBeTruthy();
    expect(screen.getAllByTestId('tagColorChip').length).toBeGreaterThan(0);
    const tagPrimaryRows = Array.from(document.querySelectorAll('[data-cy="tagPrimaryRow"]')) as HTMLElement[];
    expect(tagPrimaryRows).toHaveLength(tags.length);
    tagPrimaryRows.forEach(row => {
      expect(row.querySelector('[data-cy="tagColorChip"]')).toBeTruthy();
      expect(row.querySelector('[data-cy="tagStatusBadge"]')).toBeTruthy();
    });
    const groceriesRow = screen.getByText('Groceries').closest('[data-cy="entityTable"]');
    expect(groceriesRow?.querySelector('[data-cy="tagDescription"]')?.textContent).toBe('Food shopping');
    expect(groceriesRow?.querySelector('[data-cy="tagColorChip"]')?.className).not.toContain('rounded-pill');
    expect(screen.queryByText('#123456')).toBeNull();
    expect(screen.getAllByText('Active').length).toBeGreaterThan(0);
    expect(screen.getByText('Inactive')).toBeTruthy();
    expect(screen.getByTestId('tagProductList')).toBeTruthy();
    expect(screen.getAllByLabelText('More actions').length).toBeGreaterThan(0);
    expect(document.querySelector('.btn-group.flex-btn-group-container')).toBeNull();

    expect(screen.queryByText('ID')).toBeNull();
    expect(screen.queryByText('Created at')).toBeNull();
    expect(screen.queryByText('Updated at')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();
    expect(screen.queryByText('Financial transactions')).toBeNull();
    expect(screen.queryByText('true')).toBeNull();
    expect(screen.queryByText('false')).toBeNull();
  });

  it('uses the detail header for color identity and a keyboard-accessible non-obstructive status tooltip', async () => {
    const rootView = renderDetail();
    expect(screen.getByText('Food shopping')).toBeTruthy();
    expect(screen.getByTestId('tagDetailColorAccent').getAttribute('style')).toContain('#123456');
    expect(screen.queryByTestId('tagDetailAppearance')).toBeNull();
    expect(screen.queryByText('#123456')).toBeNull();
    expect(screen.getByTestId('tagDetailDescription')).toBeTruthy();
    expect(screen.queryByText('Created at')).toBeNull();
    expect(screen.queryByText('User')).toBeNull();

    rootView.unmount();
    renderDetail(tags[1]);
    const statusHelpButton = screen.getByTestId('tagDetailStatusHelpButton');
    fireEvent.focus(statusHelpButton);
    await waitFor(() => expect(screen.getByTestId('tagDetailStatusHelpTooltip').closest('.tooltip')).toBeTruthy());
    expect(screen.queryByTestId('tagInactiveExplanation')).toBeNull();
  });

  it('creates a tag using only product fields and the chosen hex color', async () => {
    renderCreateForm();

    expect((screen.getByLabelText('Active') as HTMLInputElement).checked).toBe(true);
    expect(screen.getByTestId('tagBasicInformationSection')).toBeTruthy();
    expect(screen.getByTestId('tagColorSection')).toBeTruthy();
    expect(screen.queryByTestId('tagAppearanceSection')).toBeNull();
    expect(screen.queryByTestId('tagStatusSection')).toBeNull();
    expect(screen.getByTestId('tagStatusControl')).toBeTruthy();
    expect(screen.getByTestId('tagStatusHelpButton')).toBeTruthy();
    expect(screen.getByTestId('tagColorControl')).toBeTruthy();
    expect(screen.getByLabelText('Choose color')).toBeTruthy();
    expect(screen.getByLabelText('Color')).toBeTruthy();
    const colorControlRow = screen.getByTestId('tagColorControlRow');
    expect(colorControlRow.querySelector('[data-cy="tagColorPicker"]')).toBeTruthy();
    expect(colorControlRow.querySelector('[data-cy="color"]')).toBeTruthy();
    expect(document.querySelector('[data-cy="tagColorPreview"]')).toBeNull();
    expect((screen.getByLabelText('Active') as HTMLInputElement).parentElement?.className).toContain('form-switch');
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Fuel' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Gas stations' } });
    fireEvent.change(screen.getByLabelText('Choose color'), { target: { value: '#112233' } });
    expect((screen.getByLabelText('Color') as HTMLInputElement).value).toBe('#112233');
    expect(document.querySelector('[data-cy="tagColorPreview"]')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mockCreateEntity).toHaveBeenCalled());
    const payload = mockCreateEntity.mock.calls[0][0];
    expect(payload).toEqual({ name: 'Fuel', description: 'Gas stations', color: '#112233', active: true });
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
    expect(payload).not.toHaveProperty('user');
    expect(payload).not.toHaveProperty('financialTransactions');
  });

  it('hydrates editable fields and uses PATCH without server-owned or relationship fields', async () => {
    renderEditForm();

    expect(screen.getByDisplayValue('Food shopping')).toBeTruthy();
    expect((screen.getByLabelText('Choose color') as HTMLInputElement).value).toBe('#123456');
    expect(screen.getByTestId('tagStatusHelpButton')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Weekly groceries' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#abcdef' } });
    expect((screen.getByLabelText('Choose color') as HTMLInputElement).value).toBe('#abcdef');
    expect(document.querySelector('[data-cy="tagColorPreview"]')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(mockPartialUpdateEntity).toHaveBeenCalled());
    const payload = mockPartialUpdateEntity.mock.calls[0][0];
    expect(payload).toEqual({ id: 1, name: 'Groceries', description: 'Weekly groceries', color: '#abcdef', active: true });
    expect(payload).not.toHaveProperty('createdAt');
    expect(payload).not.toHaveProperty('updatedAt');
    expect(payload).not.toHaveProperty('user');
    expect(payload).not.toHaveProperty('transactionRules');
  });

  it('keeps invalid color input in the form and rejects it before save', async () => {
    renderCreateForm();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Fuel' } });
    fireEvent.change(screen.getByLabelText('Color'), { target: { value: '#nothex' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(screen.getByText(/should follow pattern/i)).toBeTruthy());
    expect(mockCreateEntity).not.toHaveBeenCalled();
    expect((screen.getByLabelText('Color') as HTMLInputElement).value).toBe('#nothex');
  });

  it('maps duplicate save failures to a product-safe message and keeps the form open', async () => {
    renderCreateForm();
    mockDispatch.mockImplementation(action =>
      Promise.resolve(
        action.type === 'tag/create_entity'
          ? { ...action, type: 'tag/create_entity/rejected', error: { message: 'A tag with this name already exists' } }
          : action,
      ),
    );
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Fuel' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect((await screen.findByTestId('tagSaveError')).textContent).toContain('A tag with this name already exists.');
    expect(screen.getByLabelText('Name')).toBeTruthy();
  });

  it('names the tag in deletion confirmation and maps candidate-reference failure without exposing the raw error', async () => {
    renderDeleteDialog();
    mockDispatch.mockImplementation(action =>
      Promise.resolve(
        action.type === 'tag/delete_entity'
          ? {
              ...action,
              type: 'tag/delete_entity/rejected',
              error: { message: 'Tag cannot be deleted because it is used by transaction candidates.' },
            }
          : action,
      ),
    );

    expect(screen.getByTestId('tagDeleteLeafMessage')).toBeTruthy();
    expect(screen.getByText(/permanently delete Groceries/i)).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));

    expect((await screen.findByTestId('tagDeleteError')).textContent).toContain(
      'This tag cannot be deleted because it is still needed by an active transaction workflow. Resolve that workflow first.',
    );
    expect(screen.queryByText('transaction candidates.')).toBeNull();
  });
});
