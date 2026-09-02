import React from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import FinancialTransactionManualDraft from './financial-transaction-manual-draft';
import {
  cancelManualDraft,
  createManualDraft,
  getManualDraft,
  postManualDraft,
  updateManualDraft,
} from './services/manual-transaction-candidate.service';
import { FinancialTransactionUpdate } from './financial-transaction-update';

const mockDispatch = jest.fn();
let mockState;

jest.mock('./services/manual-transaction-candidate.service');

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('app/entities/financial-account/financial-account.reducer', () => ({
  getEntities: () => ({ type: 'financialAccount/getEntities' }),
}));

jest.mock('app/entities/category/category.reducer', () => ({
  getEntities: () => ({ type: 'category/getEntities' }),
}));

jest.mock('app/entities/tag/tag.reducer', () => ({
  getEntities: () => ({ type: 'tag/getEntities' }),
}));

jest.mock('./financial-transaction.reducer', () => ({
  partialUpdateEntity: entity => ({ type: 'financialTransaction/partialUpdateEntity', payload: entity }),
  getEntity: id => ({ type: 'financialTransaction/getEntity', payload: id }),
  reset: () => ({ type: 'financialTransaction/reset' }),
}));

const mockCreateManualDraft = createManualDraft as jest.Mock;
const mockGetManualDraft = getManualDraft as jest.Mock;
const mockUpdateManualDraft = updateManualDraft as jest.Mock;
const mockCancelManualDraft = cancelManualDraft as jest.Mock;
const mockPostManualDraft = postManualDraft as jest.Mock;

const accounts = [{ id: 1, name: 'Checking', currency: 'MXN' }];
const categories = [{ id: 10, name: 'Transport', categoryType: 'EXPENSE' }];
const tags = [{ id: 20, name: 'Business' }];

const baseState = {
  financialAccount: { entities: accounts },
  category: { entities: categories },
  tag: { entities: tags },
  financialTransaction: {
    entity: {
      id: 101,
      transactionDate: '2026-07-13',
      description: 'Existing transaction',
      amount: 12,
      flow: 'OUT',
      account: accounts[0],
    },
    loading: false,
    updating: false,
    updateSuccess: false,
  },
};

const candidate = {
  id: 77,
  source: 'MANUAL',
  status: 'DRAFT',
  validationStatus: 'UNKNOWN',
  transactionDate: null,
  postingDate: null,
  description: 'Coffee',
  signedAmount: null,
  account: null,
  category: null,
  tags: [],
};

const readyCandidate = {
  ...candidate,
  status: 'READY_TO_POST',
  validationStatus: 'VALID',
  account: accounts[0],
  transactionDate: '2026-07-13',
  description: 'Coffee',
  signedAmount: -12,
  amount: 12,
  flow: 'OUT',
};

const LocationDisplay = () => {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.setLocale('en');
};

const renderManualDraft = (initialEntry = '/financial-transaction/new') => {
  mockState = baseState;
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/financial-transaction" element={<div data-testid="financial-transaction-list" />} />
        <Route path="/financial-transaction/new" element={<FinancialTransactionManualDraft />} />
        <Route path="/financial-transaction/drafts/:draftId" element={<FinancialTransactionManualDraft />} />
        <Route path="/financial-transaction/:id" element={<div data-testid="posted-transaction-detail" />} />
        <Route path="/financial-transaction/:id/edit" element={<FinancialTransactionUpdate />} />
      </Routes>
      <LocationDisplay />
    </MemoryRouter>,
  );
};

const flushPromises = async () => {
  await act(async () => {
    await Promise.resolve();
  });
};

const deferred = <T,>() => {
  let resolve: (value: T) => void;
  let reject: (reason?: unknown) => void;
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve;
    reject = promiseReject;
  });
  return { promise, resolve: resolve!, reject: reject! };
};

describe('FinancialTransaction manual candidate draft autosave', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    jest.useFakeTimers();
    mockDispatch.mockClear();
    mockCreateManualDraft.mockReset();
    mockGetManualDraft.mockReset();
    mockUpdateManualDraft.mockReset();
    mockCancelManualDraft.mockReset();
    mockPostManualDraft.mockReset();
    mockCreateManualDraft.mockResolvedValue({ data: candidate });
    mockGetManualDraft.mockResolvedValue({ data: candidate });
    mockUpdateManualDraft.mockResolvedValue({ data: readyCandidate });
    mockCancelManualDraft.mockResolvedValue({ data: { ...candidate, status: 'CANCELLED' } });
    mockPostManualDraft.mockResolvedValue({ data: { ...readyCandidate, status: 'POSTED', financialTransaction: { id: 9001 } } });
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('renders new manual draft form without creating a candidate on page load', () => {
    renderManualDraft();

    expect(screen.getByText('Create manual transaction')).toBeTruthy();
    expect(screen.getByText('Unsaved — start typing to create a recoverable draft.')).toBeTruthy();
    expect(mockCreateManualDraft).not.toHaveBeenCalled();
    expect(mockGetManualDraft).not.toHaveBeenCalled();
  });

  it('does not create a candidate for non-meaningful postingDate-only changes', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Posting date'), { target: { value: '2026-07-14' } });
    await flushPromises();

    expect(mockCreateManualDraft).not.toHaveBeenCalled();
  });

  it('does not create a candidate for externalReference-only changes', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('External Reference'), { target: { value: 'external-only' } });
    await flushPromises();

    expect(mockCreateManualDraft).not.toHaveBeenCalled();
  });

  it('does not create a candidate for notes-only changes', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Notes'), { target: { value: 'notes-only' } });
    await flushPromises();

    expect(mockCreateManualDraft).not.toHaveBeenCalled();
  });

  it('does not create a candidate for blank description changes', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: '   ' } });
    await flushPromises();

    expect(mockCreateManualDraft).not.toHaveBeenCalled();
  });

  it('does not create a candidate for default flow changes without amount', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'OUT' } });
    await flushPromises();

    expect(mockCreateManualDraft).not.toHaveBeenCalled();
  });

  it('does not create a candidate on focus or blur', async () => {
    renderManualDraft();

    fireEvent.focus(screen.getByLabelText('Description'));
    fireEvent.blur(screen.getByLabelText('Description'));
    await flushPromises();

    expect(mockCreateManualDraft).not.toHaveBeenCalled();
  });

  it('creates a candidate on the first meaningful change and redirects to draft URL', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee' } });
    await waitFor(() => expect(mockCreateManualDraft).toHaveBeenCalledTimes(1));

    expect(mockCreateManualDraft).toHaveBeenCalledWith(expect.objectContaining({ description: 'Coffee' }));
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/financial-transaction/drafts/77'));
  });

  it('rapid edits create only one candidate', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'C' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Co' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee' } });
    await waitFor(() => expect(mockCreateManualDraft).toHaveBeenCalledTimes(1));
  });

  it('debounced PATCH sends the latest draft values and maps amount plus flow to signedAmount', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'OUT' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalled());
    expect(mockUpdateManualDraft).toHaveBeenLastCalledWith(
      77,
      expect.objectContaining({
        signedAmount: -100,
      }),
    );
  });

  it('queues exactly one latest-state PATCH after an in-flight PATCH receives another edit', async () => {
    const firstPatch = deferred<{ data: typeof readyCandidate }>();
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockUpdateManualDraft
      .mockReturnValueOnce(firstPatch.promise)
      .mockResolvedValueOnce({ data: { ...readyCandidate, description: 'Latest' } });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Intermediate' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });
    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Latest' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });
    expect(mockUpdateManualDraft).toHaveBeenCalledTimes(1);

    await act(async () => {
      firstPatch.resolve({ data: { ...readyCandidate, description: 'Intermediate' } });
      await firstPatch.promise;
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledTimes(2));
    expect(mockUpdateManualDraft).toHaveBeenLastCalledWith(77, expect.objectContaining({ description: 'Latest' }));
  });

  it('stale PATCH response does not overwrite newer local description', async () => {
    const firstPatch = deferred<{ data: typeof readyCandidate }>();
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockUpdateManualDraft
      .mockReturnValueOnce(firstPatch.promise)
      .mockResolvedValueOnce({ data: { ...readyCandidate, description: 'Newest local' } });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Old value in flight' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });
    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Newest local' } });
    await act(async () => {
      firstPatch.resolve({ data: { ...readyCandidate, description: 'Old server response' } });
      await firstPatch.promise;
    });

    expect(screen.getByLabelText('Description').value).toBe('Newest local');
    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledTimes(2));
  });

  it('stale PATCH response does not overwrite newer local amount', async () => {
    const firstPatch = deferred<{ data: typeof readyCandidate }>();
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockUpdateManualDraft.mockReturnValueOnce(firstPatch.promise).mockResolvedValueOnce({ data: { ...readyCandidate, signedAmount: -15 } });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '14' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });
    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '15' } });
    await act(async () => {
      firstPatch.resolve({ data: { ...readyCandidate, signedAmount: -14 } });
      await firstPatch.promise;
    });

    expect(screen.getByLabelText('Amount').value).toBe('15');
    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledTimes(2));
  });

  it('failed create shows an error', async () => {
    mockCreateManualDraft.mockRejectedValue(new Error('boom'));
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee' } });

    expect(await screen.findByText('Could not create the draft.')).toBeTruthy();
  });

  it('failed PATCH shows Save failed and disables Post', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockUpdateManualDraft.mockRejectedValue(new Error('boom'));
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee updated' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    expect(await screen.findByText('Save failed. Changes may not be saved.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(true);
  });

  it('pending autosave is flushed before posting and successful post redirects to FinancialTransaction detail', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee updated' } });
    fireEvent.click(screen.getByRole('button', { name: /post transaction/i }));

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalled());
    await waitFor(() => expect(mockPostManualDraft).toHaveBeenCalledWith(77));
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/financial-transaction/9001'));
  });

  it('already-posted idempotent post response redirects safely without retry', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockPostManualDraft.mockResolvedValue({ data: { ...readyCandidate, status: 'POSTED', financialTransaction: { id: 9001 } } });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.click(screen.getByRole('button', { name: /post transaction/i }));

    await waitFor(() => expect(mockPostManualDraft).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/financial-transaction/9001'));
    expect(screen.queryByTestId('manual-draft-error')).toBeNull();
  });

  it('incomplete draft cannot post', async () => {
    mockGetManualDraft.mockResolvedValue({ data: candidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(true);
    expect(mockPostManualDraft).not.toHaveBeenCalled();
  });

  it('cancel before candidate exists does not call backend', () => {
    renderManualDraft();

    fireEvent.click(screen.getByRole('button', { name: /cancel draft/i }));

    expect(mockCancelManualDraft).not.toHaveBeenCalled();
    expect(screen.getByTestId('location').textContent).toBe('/financial-transaction');
  });

  it('cancel after candidate exists calls cancel endpoint', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.click(screen.getByRole('button', { name: /cancel draft/i }));

    await waitFor(() => expect(mockCancelManualDraft).toHaveBeenCalledWith(77));
  });

  it('resume draft by URL loads candidate values', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByDisplayValue('Coffee')).toBeTruthy();
    expect(screen.getByLabelText('Account').value).toBe('1');
    expect(screen.getByLabelText('Amount').value).toBe('12');
    expect(screen.getByLabelText('Type').value).toBe('OUT');
  });

  it('non-MANUAL candidate shows safe route error without rendering editable form', async () => {
    mockGetManualDraft.mockResolvedValue({ data: { ...readyCandidate, source: 'FILE_IMPORT' } });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByText('This draft cannot be edited in the manual transaction flow.')).toBeTruthy();
    expect(screen.queryByLabelText('Description')).toBeNull();
    expect(screen.queryByRole('button', { name: /post transaction/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /cancel draft/i })).toBeNull();
  });

  it('non-MANUAL candidate does not trigger autosave PATCH or post', async () => {
    mockGetManualDraft.mockResolvedValue({ data: { ...readyCandidate, source: 'API_IMPORT' } });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByTestId('manual-draft-route-error')).toBeTruthy();
    act(() => {
      jest.advanceTimersByTime(700);
    });

    expect(mockUpdateManualDraft).not.toHaveBeenCalled();
    expect(mockPostManualDraft).not.toHaveBeenCalled();
  });

  it('GET draft failure shows safe route error without rendering editable form', async () => {
    mockGetManualDraft.mockRejectedValue(new Error('not found'));
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByText('Could not load the draft.')).toBeTruthy();
    expect(screen.queryByLabelText('Description')).toBeNull();
    expect(screen.queryByRole('button', { name: /post transaction/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /cancel draft/i })).toBeNull();
  });

  it('cancelled draft is read-only', async () => {
    mockGetManualDraft.mockResolvedValue({ data: { ...candidate, status: 'CANCELLED' } });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByText('This draft was cancelled and cannot be edited.')).toBeTruthy();
    expect(screen.getByLabelText('Description').disabled).toBe(true);
  });

  it('cancelled draft does not autosave or post', async () => {
    mockGetManualDraft.mockResolvedValue({ data: { ...readyCandidate, status: 'CANCELLED' } });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByText('This draft was cancelled and cannot be edited.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(true);
    act(() => {
      jest.advanceTimersByTime(700);
    });

    expect(mockUpdateManualDraft).not.toHaveBeenCalled();
    expect(mockPostManualDraft).not.toHaveBeenCalled();
  });

  it('posted draft redirects to FinancialTransaction detail', async () => {
    mockGetManualDraft.mockResolvedValue({ data: { ...readyCandidate, status: 'POSTED', financialTransaction: { id: 9001 } } });
    renderManualDraft('/financial-transaction/drafts/77');

    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/financial-transaction/9001'));
  });

  it('candidate flow does not call FinancialTransaction rule-preview', async () => {
    renderManualDraft();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee' } });

    await waitFor(() => expect(mockCreateManualDraft).toHaveBeenCalled());
    expect(mockGetManualDraft).not.toHaveBeenCalled();
    expect(mockUpdateManualDraft).not.toHaveBeenCalled();
    expect(mockPostManualDraft).not.toHaveBeenCalled();
  });

  it('posted FinancialTransaction edit route does not use TransactionCandidate endpoints', () => {
    renderManualDraft('/financial-transaction/101/edit');

    expect(screen.getByDisplayValue('Existing transaction')).toBeTruthy();
    expect(mockCreateManualDraft).not.toHaveBeenCalled();
    expect(mockGetManualDraft).not.toHaveBeenCalled();
    expect(mockUpdateManualDraft).not.toHaveBeenCalled();
    expect(mockCancelManualDraft).not.toHaveBeenCalled();
    expect(mockPostManualDraft).not.toHaveBeenCalled();
  });
});
