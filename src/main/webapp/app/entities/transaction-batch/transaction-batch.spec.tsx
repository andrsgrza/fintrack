import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

import TransactionBatch from './transaction-batch';
import TransactionBatchDetail from './transaction-batch-detail';

jest.mock('axios');

jest.mock('app/entities/category/category-selectable.service', () => ({
  getSelectableCategories: jest.fn(() =>
    Promise.resolve([
      { id: 21, name: 'Transport', color: '#2463A5', active: true },
      { id: 22, name: 'Archived category', color: '#777777', active: false },
    ]),
  ),
}));

jest.mock('app/entities/tag/tag-selectable.service', () => ({
  getSelectableTags: jest.fn(() => Promise.resolve([{ id: 31, name: 'Work', color: '#3B82F6', active: true }])),
}));

const mockGet = axios.get as jest.Mock;
const mockPost = axios.post as jest.Mock;
const mockPatch = axios.patch as jest.Mock;
const mockPut = axios.put as jest.Mock;
const mockDelete = axios.delete as jest.Mock;

const workspace = {
  id: 7,
  version: 4,
  status: 'OPEN',
  accountId: 2,
  accountName: 'Cuenta principal',
  autoApplyDescriptions: false,
  autoApplyCategories: false,
  autoApplyTags: false,
  protectManualChanges: true,
  updatedAt: '2026-10-16T12:00:00Z',
  candidates: [
    {
      id: 81,
      version: 6,
      rowPosition: 1,
      source: 'MANUAL',
      validationStatus: 'VALID',
      transactionDate: '2026-10-16',
      description: 'Taxi airport',
      signedAmount: -250,
      flow: 'OUT',
      categoryId: 21,
      categoryName: 'Transport',
      categoryColor: '#2463A5',
      categoryActive: true,
      tags: [{ id: 31, name: 'Work', color: '#3B82F6', active: true }],
      inputIssues: [],
    },
  ],
};

const renderDetail = () =>
  render(
    <MemoryRouter initialEntries={['/transaction-batches/7']}>
      <Routes>
        <Route path="/transaction-batches/:id" element={<TransactionBatchDetail />} />
        <Route path="/financial-transaction" element={<div>Transactions destination</div>} />
      </Routes>
    </MemoryRouter>,
  );

describe('TransactionBatch product workspace', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches') return Promise.resolve({ data: [workspace] });
      if (url === 'api/transaction-batches/7') return Promise.resolve({ data: workspace });
      if (url === 'api/transaction-batches/7/rule-preview') return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
  });

  it('lists open workspaces as a product entry rather than a legacy ingestion page', async () => {
    render(
      <MemoryRouter>
        <TransactionBatch />
      </MemoryRouter>,
    );

    await screen.findByText('Cuenta principal');
    expect(screen.getByRole('heading', { name: 'Lotes de transacciones' })).toBeTruthy();
    expect(screen.getByRole('link', { name: /cuenta principal/i }).getAttribute('href')).toBe('/transaction-batches/7');
    expect(mockGet).toHaveBeenCalledWith('api/transaction-batches');
  });

  it('keeps the persisted category editable exactly once and preserves its color identity', async () => {
    renderDetail();

    const selector = await screen.findByLabelText('Categoría para Taxi airport');
    expect((selector as HTMLSelectElement).value).toBe('21');
    expect(selector.style.borderInlineStart).toBe('0.3rem solid #2463A5');
    expect(screen.queryAllByText('Transport')).toHaveLength(1);
    expect(screen.getByText('Work')).toBeTruthy();
  });

  it('shows a separate meaningful rule suggestion and applies it explicitly', async () => {
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') {
        return Promise.resolve({
          data: { ...workspace, candidates: [{ ...workspace.candidates[0], categoryId: null, categoryName: null, categoryColor: null }] },
        });
      }
      if (url === 'api/transaction-batches/7/rule-preview') {
        return Promise.resolve({
          data: [
            {
              candidateId: 81,
              candidateVersion: 6,
              descriptionEvaluable: false,
              descriptionMatched: false,
              classificationEvaluable: true,
              suggestedCategoryId: 21,
              suggestedTagIds: [],
              eligibleTagIds: [],
            },
          ],
        });
      }
      return Promise.resolve({ data: [] });
    });
    mockPost.mockResolvedValue({ data: {} });
    renderDetail();

    await screen.findByText('Categoría sugerida');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar categoría' }));

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        'api/transaction-batches/7/candidates/81/apply-rules',
        expect.objectContaining({ expectedCandidateVersion: 6, applyCategory: true, expectedSuggestedCategoryId: 21 }),
      ),
    );
  });

  it('blocks publication while a row still needs correction', async () => {
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') {
        return Promise.resolve({ data: { ...workspace, candidates: [{ ...workspace.candidates[0], validationStatus: 'INVALID' }] } });
      }
      if (url === 'api/transaction-batches/7/rule-preview') return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    renderDetail();

    const publish = await screen.findByRole('button', { name: 'Publicar' });
    expect((publish as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText('Corrige las filas pendientes antes de publicar.')).toBeTruthy();
  });

  it('keeps the mobile classification controls inside an explicit expandable row section', async () => {
    renderDetail();

    const category = await screen.findByLabelText('Categoría para Taxi airport');
    const toggle = screen.getByRole('button', { name: 'Mostrar detalles' });
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(toggle);

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.textContent).toBe('Ocultar detalles');
    expect(category.closest('article')?.className).toContain('mobile-expanded');
  });

  it('opens the manual editor before creating a candidate and leaves no empty row when cancelled', async () => {
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByRole('button', { name: /añadir movimiento/i }));

    expect(await screen.findByRole('heading', { name: 'Añadir movimiento' })).toBeTruthy();
    expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/candidates')).toHaveLength(0);
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'Añadir movimiento' })).toBeNull());
    expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/candidates')).toHaveLength(0);
  });

  it('materializes one provisional manual candidate, previews and applies classification before final Save', async () => {
    let materialized = false;
    let serverCandidate = {
      ...workspace.candidates[0],
      id: 82,
      version: 1,
      rowPosition: 2,
      description: 'Taxi batch preview',
      signedAmount: -42,
      categoryId: null,
      categoryName: null,
      categoryColor: null,
      tags: [],
    };
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') {
        return Promise.resolve({
          data: materialized ? { ...workspace, version: 5, candidates: [...workspace.candidates, serverCandidate] } : workspace,
        });
      }
      if (url === 'api/transaction-batches/7/rule-preview') {
        return Promise.resolve({
          data: materialized
            ? [
                {
                  candidateId: 82,
                  candidateVersion: serverCandidate.version,
                  descriptionEvaluable: false,
                  descriptionMatched: false,
                  classificationEvaluable: true,
                  suggestedCategoryId: serverCandidate.categoryId ? null : 21,
                  suggestedCategoryRuleName: 'Taxi rule',
                  suggestedTagIds: serverCandidate.tags.length ? [] : [31],
                  eligibleTagIds: serverCandidate.tags.length ? [] : [31],
                  eligibleTagSuggestions: serverCandidate.tags.length ? [] : [{ tagId: 31, tagName: 'Work', ruleName: 'Taxi rule' }],
                },
              ]
            : [],
        });
      }
      return Promise.resolve({ data: [] });
    });
    mockPost.mockImplementation((url, body) => {
      if (url === 'api/transaction-batches/7/candidates') {
        materialized = true;
        serverCandidate = { ...serverCandidate, description: body.description, signedAmount: body.signedAmount };
        return Promise.resolve({ data: { candidate: serverCandidate, batchVersion: 5 } });
      }
      if (url === 'api/transaction-batches/7/candidates/82/apply-rules') {
        serverCandidate = {
          ...serverCandidate,
          version: serverCandidate.version + 1,
          categoryId: body.applyCategory ? 21 : serverCandidate.categoryId,
          categoryName: body.applyCategory ? 'Transport' : serverCandidate.categoryName,
          categoryColor: body.applyCategory ? '#2463A5' : serverCandidate.categoryColor,
          tags: body.applyTags ? [{ id: 31, name: 'Work', color: '#3B82F6', active: true }] : serverCandidate.tags,
        };
        return Promise.resolve({ data: { candidateId: 82, candidateVersion: serverCandidate.version, batchVersion: 6, changed: true } });
      }
      return Promise.resolve({ data: {} });
    });
    mockPatch.mockImplementation(() => {
      serverCandidate = { ...serverCandidate, version: serverCandidate.version + 1 };
      return Promise.resolve({ data: { candidate: serverCandidate, batchVersion: 6 } });
    });
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByRole('button', { name: /añadir movimiento/i }));
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Taxi batch preview' } });
    fireEvent.change(screen.getByLabelText('Monto'), { target: { value: '42' } });

    await waitFor(() => expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/candidates')).toHaveLength(1));
    expect(screen.queryByText(/descripción sugerida/i)).toBeNull();
    expect((await screen.findAllByText(/categoría sugerida/i)).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/etiquetas sugeridas/i).length).toBeGreaterThan(0);
    expect(document.querySelector('[data-cy="transactionBatchRow-82"]')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Aplicar categoría sugerida' }));
    await waitFor(() => expect(mockPost).toHaveBeenCalledWith('api/transaction-batches/7/candidates/82/apply-rules', expect.any(Object)));
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar etiquetas sugeridas' }));
    await waitFor(() =>
      expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/candidates/82/apply-rules')).toHaveLength(2),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Guardar movimiento' }));
    await waitFor(() => expect(document.querySelector('[data-cy="transactionBatchRow-82"]')).toBeTruthy());
    expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/candidates')).toHaveLength(1);
  });

  it('deletes a provisional manual candidate on Cancel but never deletes an existing row', async () => {
    const provisional = { ...workspace.candidates[0], id: 82, version: 1, rowPosition: 2, description: 'Discard me' };
    let materialized = false;
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') {
        return Promise.resolve({
          data: materialized ? { ...workspace, version: 5, candidates: [...workspace.candidates, provisional] } : workspace,
        });
      }
      if (url === 'api/transaction-batches/7/rule-preview') return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    mockPost.mockImplementation(url => {
      if (url === 'api/transaction-batches/7/candidates') {
        materialized = true;
        return Promise.resolve({ data: { candidate: provisional, batchVersion: 5 } });
      }
      return Promise.resolve({ data: {} });
    });
    mockDelete.mockImplementation(() => {
      materialized = false;
      return Promise.resolve({});
    });
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByRole('button', { name: /añadir movimiento/i }));
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Discard me' } });
    await waitFor(() => expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/candidates')).toHaveLength(1));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    await waitFor(() =>
      expect(mockDelete).toHaveBeenCalledWith('api/transaction-batches/7/candidates/82', { params: { expectedCandidateVersion: 1 } }),
    );

    fireEvent.click(screen.getByRole('button', { name: 'Editar' }));
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Local edit only' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(mockDelete).toHaveBeenCalledTimes(1);
  });

  it('maps a positive manual amount and selected flow to the canonical signed draft command', async () => {
    mockPost.mockResolvedValue({ data: {} });
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByRole('button', { name: /añadir movimiento/i }));
    fireEvent.change(screen.getByLabelText('Fecha de la transacción'), { target: { value: '2026-10-18' } });
    expect(screen.getByLabelText<HTMLInputElement>('Fecha de posteo').value).toBe('2026-10-18');
    fireEvent.click(screen.getByLabelText('Usar la misma fecha para el posteo'));
    fireEvent.change(screen.getByLabelText('Fecha de posteo'), { target: { value: '2026-10-19' } });
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Pago manual' } });
    fireEvent.click(screen.getByRole('button', { name: 'INGRESO' }));
    fireEvent.change(screen.getByLabelText('Monto'), { target: { value: '321.5' } });
    fireEvent.change(screen.getByLabelText('Categoría'), { target: { value: '21' } });
    expect(screen.getByTestId('batchEditorTags')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Guardar movimiento' }));

    await waitFor(() =>
      expect(mockPost).toHaveBeenCalledWith(
        'api/transaction-batches/7/candidates',
        expect.objectContaining({
          expectedBatchVersion: 4,
          transactionDate: '2026-10-18',
          postingDate: '2026-10-19',
          signedAmount: 321.5,
          categoryId: 21,
        }),
      ),
    );
  });

  it('keeps the editor open and maps a category-flow validation response to a useful field error', async () => {
    mockPatch.mockRejectedValueOnce({ response: { data: { detail: 'Category is incompatible with the transaction flow' } } });
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }));
    fireEvent.click(screen.getByRole('button', { name: 'INGRESO' }));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar movimiento' }));

    expect((await screen.findByRole('alert')).textContent).toContain('La categoría elegida sólo es válida para el otro tipo de movimiento');
    expect(screen.getByRole('heading', { name: 'Editar movimiento' })).toBeTruthy();
    expect(screen.getByLabelText<HTMLSelectElement>('Categoría').value).toBe('21');
  });

  it('uses one shared grid class and keeps local visual sorting independent from row positions', async () => {
    const laterRow = {
      ...workspace.candidates[0],
      id: 82,
      rowPosition: 2,
      transactionDate: '2026-10-17',
      description: 'Zebra payment',
      signedAmount: 50,
      categoryName: null,
      categoryId: null,
      categoryColor: null,
    };
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') {
        return Promise.resolve({ data: { ...workspace, candidates: [workspace.candidates[0], laterRow] } });
      }
      if (url === 'api/transaction-batches/7/rule-preview') return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    const { container } = renderDetail();

    await screen.findByText('Zebra payment');
    expect(container.querySelectorAll('.transaction-batch-review-grid')).toHaveLength(3);
    fireEvent.change(screen.getByLabelText('Ordenar'), { target: { value: 'AMOUNT' } });
    expect(Array.from(container.querySelectorAll('[data-cy^="transactionBatchRow-"]')).map(row => row.getAttribute('data-cy'))).toEqual([
      'transactionBatchRow-81',
      'transactionBatchRow-82',
    ]);
    fireEvent.click(screen.getByRole('button', { name: 'Orden ascendente' }));
    expect(Array.from(container.querySelectorAll('[data-cy^="transactionBatchRow-"]')).map(row => row.getAttribute('data-cy'))).toEqual([
      'transactionBatchRow-82',
      'transactionBatchRow-81',
    ]);
    fireEvent.change(screen.getByLabelText('Ordenar'), { target: { value: 'ROW_POSITION' } });
    expect(Array.from(container.querySelectorAll('[data-cy^="transactionBatchRow-"]')).map(row => row.getAttribute('data-cy'))).toEqual([
      'transactionBatchRow-82',
      'transactionBatchRow-81',
    ]);
  });

  it('opens a CSV confirmation after file selection and cancelling does not append', async () => {
    const { container } = renderDetail();

    await screen.findByText('Taxi airport');
    const file = new File(['transactionDate,description,signedAmount\n2026-10-16,Taxi,-25.00\n'], 'cancel.csv', { type: 'text/csv' });
    const fileInput = container.querySelector<HTMLInputElement>('[data-cy="transactionBatchCsvFile"]');
    if (!fileInput) throw new Error('The CSV input is required for the confirmation test');
    expect(fileInput.className).toContain('visually-hidden');
    fireEvent.change(fileInput, { target: { files: [file] } });

    expect(await screen.findByText('cancel.csv')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/append-csv')).toHaveLength(0);
  });

  it('keeps the same append operation key when an uncertain response is retried', async () => {
    mockPost.mockRejectedValueOnce(new Error('connection closed')).mockResolvedValueOnce({ data: {} });
    const { container } = renderDetail();

    await screen.findByText('Taxi airport');
    const file = new File(['transactionDate,description,signedAmount\n2026-10-16,Taxi,-25.00\n'], 'retry.csv', {
      type: 'text/csv',
    });
    const fileInput = container.querySelector<HTMLInputElement>('[data-cy="transactionBatchCsvFile"]');
    if (!fileInput) throw new Error('The CSV input is required for the append retry test');
    fireEvent.change(fileInput, { target: { files: [file] } });

    await screen.findByText('Las filas se añadirán a este lote. Los movimientos existentes no serán reemplazados.');
    fireEvent.click(screen.getByRole('button', { name: 'Importar archivo' }));
    await screen.findByRole('alert');
    fireEvent.click(screen.getByRole('button', { name: 'Importar archivo' }));

    await waitFor(() => expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/append-csv')).toHaveLength(2));
    const appendBodies = mockPost.mock.calls
      .filter(([url]) => url === 'api/transaction-batches/7/append-csv')
      .map(([, body]) => body as FormData);
    expect(appendBodies[0].get('operationKey')).toBe(appendBodies[1].get('operationKey'));
  });

  it('preserves local row edits when the server rejects a stale external version', async () => {
    mockPatch.mockRejectedValueOnce(new Error('stale'));
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByRole('button', { name: 'Editar' }));
    const notes = screen.getByLabelText('Notas');
    fireEvent.change(notes, { target: { value: 'My local note' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar movimiento' }));

    await screen.findByRole('alert');
    expect(screen.getByLabelText<HTMLTextAreaElement>('Notas').value).toBe('My local note');
    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('keeps the persisted category visible and reports an external-version conflict from the local mutation queue', async () => {
    mockPut.mockRejectedValueOnce(new Error('stale category version'));
    renderDetail();

    const selector = await screen.findByLabelText('Categoría para Taxi airport');
    fireEvent.change(selector, { target: { value: '' } });

    await screen.findByText('No se pudo actualizar la categoría; la versión de la fila puede haber cambiado.');
    expect(selector.value).toBe('21');
  });

  it('reconciles a changed A-to-B suggestion without silently applying B', async () => {
    const withoutCategory = { ...workspace.candidates[0], categoryId: null, categoryName: null, categoryColor: null };
    let previewHasChanged = false;
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') return Promise.resolve({ data: { ...workspace, candidates: [withoutCategory] } });
      if (url === 'api/transaction-batches/7/rule-preview') {
        return Promise.resolve({
          data: [
            {
              candidateId: 81,
              candidateVersion: 6,
              descriptionEvaluable: false,
              descriptionMatched: false,
              classificationEvaluable: true,
              suggestedCategoryId: previewHasChanged ? 22 : 21,
              suggestedTagIds: [],
              eligibleTagIds: [],
            },
          ],
        });
      }
      return Promise.resolve({ data: [] });
    });
    mockPost.mockImplementation(url => {
      if (url === 'api/transaction-batches/7/candidates/81/apply-rules') {
        previewHasChanged = true;
        return Promise.reject(new Error('stale suggestion'));
      }
      return Promise.resolve({ data: {} });
    });
    renderDetail();

    await screen.findByText('Categoría sugerida');
    fireEvent.click(screen.getByRole('button', { name: 'Aplicar categoría' }));

    await screen.findByText('La sugerencia cambió o ya no puede aplicarse. Actualizamos la cola para mostrar el estado actual.');
    expect(screen.getByText('Archived category')).toBeTruthy();
    expect(screen.getByLabelText<HTMLSelectElement>('Categoría para Taxi airport').value).toBe('');
    expect(mockPost).toHaveBeenCalledWith(
      'api/transaction-batches/7/candidates/81/apply-rules',
      expect.objectContaining({ expectedSuggestedCategoryId: 21 }),
    );
    expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/candidates/81/apply-rules')).toHaveLength(1);
  });

  it('offers a CSV-only normalization baseline and contextual classification without an implicit account condition', async () => {
    const csvRow = {
      ...workspace.candidates[0],
      id: 82,
      rowPosition: 2,
      source: 'FILE_IMPORT',
      description: 'Normalized taxi airport',
      sourceDescription: 'RAW TAXI AIRPORT',
      flow: 'OUT',
    };
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') {
        return Promise.resolve({ data: { ...workspace, candidates: [workspace.candidates[0], csvRow] } });
      }
      if (url === 'api/transaction-batches/7/rule-preview') return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    renderDetail();

    await screen.findByText('Normalized taxi airport');
    expect(screen.queryByTestId('transactionBatchCreateNormalizationRule-81')).toBeNull();
    fireEvent.click(screen.getByTestId('transactionBatchCreateNormalizationRule-82'));

    expect((await screen.findByTestId('contextualDescriptionRuleConditionValue-0')).getAttribute('value')).toBe('RAW TAXI AIRPORT');
    expect(screen.getByTestId('contextualDescriptionRuleResult').getAttribute('value')).toBe('Normalized taxi airport');
    const cancel = screen
      .getByTestId('transactionBatchRuleCreationModal')
      .querySelector<HTMLButtonElement>('button.btn-outline-secondary:not(.btn-sm)');
    if (!cancel) throw new Error('The contextual rule modal cancel action is required');
    fireEvent.click(cancel);

    fireEvent.click(screen.getByTestId('transactionBatchCreateTransactionRule-81'));
    await screen.findByText(/cuenta se muestra sólo como contexto/i);
    expect(Array.from(document.querySelectorAll('[data-cy="conditionRow"]')).map(row => row.getAttribute('data-condition-field'))).toEqual([
      'DESCRIPTION',
      'FLOW',
    ]);
  });

  it('keeps scratch rule creation separate from reevaluation', async () => {
    mockPost.mockResolvedValueOnce({ data: { id: 91, name: 'Regla contextual' } });
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByTestId('transactionBatchCreateNormalizationRule'));
    const nameInput = document.getElementById('contextual-description-rule-name');
    const addCondition = document.querySelector<HTMLButtonElement>('[data-cy="addDescriptionNormalizationRuleCondition"]');
    if (!nameInput || !addCondition) throw new Error('The contextual DNR form is required');
    fireEvent.change(nameInput, { target: { value: 'Regla contextual' } });
    fireEvent.click(addCondition);
    fireEvent.change(screen.getByTestId('contextualDescriptionRuleConditionValue-0'), { target: { value: 'Taxi airport' } });
    fireEvent.change(screen.getByTestId('contextualDescriptionRuleResult'), { target: { value: 'Taxi normalizado' } });
    fireEvent.click(screen.getByTestId('batchContextualRuleSave'));

    await screen.findByRole('status');
    expect(mockPost.mock.calls.filter(([url]) => url === 'api/description-normalization-rules/configured')).toHaveLength(1);
    expect(mockPost.mock.calls.filter(([url]) => url === 'api/transaction-batches/7/reevaluate')).toHaveLength(0);
  });

  it('reports settings as saved when only the following rule evaluation failed', async () => {
    const persistedSettings = {
      ...workspace,
      autoApplyDescriptions: true,
      autoApplyCategories: true,
      autoApplyTags: false,
      protectManualChanges: true,
    };
    let workspaceReads = 0;
    mockGet.mockImplementation(url => {
      if (url === 'api/transaction-batches/7') {
        workspaceReads += 1;
        return Promise.resolve({ data: workspaceReads === 1 ? workspace : persistedSettings });
      }
      if (url === 'api/transaction-batches/7/rule-preview') return Promise.resolve({ data: [] });
      return Promise.resolve({ data: [] });
    });
    mockPut.mockRejectedValueOnce(new Error('evaluation unavailable'));
    renderDetail();

    await screen.findByText('Taxi airport');
    fireEvent.click(screen.getByRole('button', { name: 'Reglas' }));
    fireEvent.click(screen.getByLabelText('Aplicar automáticamente descripciones normalizadas'));
    fireEvent.click(screen.getByLabelText('Aplicar automáticamente categorías sugeridas'));
    fireEvent.click(screen.getByRole('button', { name: 'Guardar y reevaluar' }));

    await screen.findByText(
      'Las preferencias se guardaron, pero no se pudieron reevaluar las reglas. No se aplicaron cambios automáticos.',
    );
    expect(screen.getByText('Preferencias de reglas').closest('.modal')?.className).not.toContain('show');
  });
});
