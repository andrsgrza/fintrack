import React from 'react';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import enAccountType from 'app/../i18n/en/accountType.json';
import enFinancialAccount from 'app/../i18n/en/financialAccount.json';
import FinancialTransactionManualDraft from './financial-transaction-manual-draft';
import {
  applyManualDraftRules,
  cancelManualDraft,
  createManualDraft,
  getManualDraft,
  postManualDraft,
  previewManualDraftRules,
  updateManualDraft,
} from './services/manual-transaction-candidate.service';
import { FinancialTransactionUpdate } from './financial-transaction-update';
import { getSelectableFinancialAccounts } from 'app/entities/financial-account/financial-account-selectable.service';

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

jest.mock('app/entities/financial-account/financial-account-selectable.service', () => ({
  getSelectableFinancialAccounts: jest.fn(),
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
const mockPreviewManualDraftRules = previewManualDraftRules as jest.Mock;
const mockApplyManualDraftRules = applyManualDraftRules as jest.Mock;
const mockGetSelectableFinancialAccounts = getSelectableFinancialAccounts as jest.Mock;

const accounts = [
  { id: 1, name: 'Checking', accountType: 'DEBIT', currency: 'MXN', lastFourDigits: '1234', active: true },
  { id: 2, name: 'Savings', accountType: 'CREDIT_CARD', currency: 'MXN', lastFourDigits: '9876', active: true },
];
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
  classificationReviewStatus: 'USER_SELECTED',
  account: accounts[0],
  transactionDate: '2026-07-13',
  description: 'Coffee',
  signedAmount: -12,
  amount: 12,
  flow: 'OUT',
};

const notEvaluatedReadyCandidate = {
  ...readyCandidate,
  classificationReviewStatus: 'NOT_EVALUATED',
};

const staleReadyCandidate = {
  ...readyCandidate,
  classificationReviewStatus: 'STALE',
};

const suggestedReadyCandidate = {
  ...readyCandidate,
  category: categories[0],
  tags,
  classificationReviewStatus: 'SUGGESTED',
};

const noSuggestionsPreview = {
  candidateId: 77,
  candidateUpdatedAt: '2026-07-13T00:00:00Z',
  classificationReviewStatus: 'NOT_EVALUATED',
  suggestedCategory: null,
  suggestedTags: [],
  conflicts: [],
  skippedOutputs: [],
  matchedRules: [],
  hasSuggestions: false,
  hasConflicts: false,
};

const suggestionsPreview = {
  ...noSuggestionsPreview,
  suggestedCategory: { categoryId: 10, categoryName: 'Transport', sourceRuleId: 1, sourceRuleName: 'Uber rule' },
  suggestedTags: [{ tagId: 20, tagName: 'Business', sourceRuleId: 1, sourceRuleName: 'Uber rule' }],
  matchedRules: [{ ruleId: 1, ruleName: 'Uber rule', priority: 0, conditionLogic: 'ALL', proposedOutputs: ['CATEGORY', 'TAGS'] }],
  hasSuggestions: true,
};

const LocationDisplay = () => {
  const location = useLocation();
  return <div data-testid="location">{location.pathname}</div>;
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.registerTranslations('en', enAccountType);
  TranslatorContext.registerTranslations('en', enFinancialAccount);
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
    mockPreviewManualDraftRules.mockReset();
    mockApplyManualDraftRules.mockReset();
    mockGetSelectableFinancialAccounts.mockReset();
    mockCreateManualDraft.mockResolvedValue({ data: candidate });
    mockGetManualDraft.mockResolvedValue({ data: candidate });
    mockUpdateManualDraft.mockResolvedValue({ data: readyCandidate });
    mockCancelManualDraft.mockResolvedValue({ data: { ...candidate, status: 'CANCELLED' } });
    mockPostManualDraft.mockResolvedValue({ data: { ...readyCandidate, status: 'POSTED', financialTransaction: { id: 9001 } } });
    mockPreviewManualDraftRules.mockResolvedValue({ data: suggestionsPreview });
    mockApplyManualDraftRules.mockResolvedValue({
      data: {
        candidate: suggestedReadyCandidate,
        evaluation: suggestionsPreview,
        categoryApplied: true,
        tagIdsApplied: [20],
      },
    });
    mockGetSelectableFinancialAccounts.mockImplementation(() => new Promise(() => {}));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('renders new manual draft form without creating a candidate on page load', () => {
    renderManualDraft();

    expect(screen.getByText('Create manual transaction')).toBeTruthy();
    expect(screen.queryByText('Start entering details to create a recoverable draft.')).toBeNull();
    expect(screen.queryByText('Creating draft…')).toBeNull();
    expect(screen.queryByText('Saving changes…')).toBeNull();
    expect(screen.queryByText('Draft saved automatically.')).toBeNull();
    expect(mockCreateManualDraft).not.toHaveBeenCalled();
    expect(mockGetManualDraft).not.toHaveBeenCalled();
  });

  it('existing draft route does not show a normal autosave or draft banner', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByDisplayValue('Coffee')).toBeTruthy();
    expect(screen.queryByText(/Unsaved/i)).toBeNull();
    expect(screen.queryByText('Start entering details to create a recoverable draft.')).toBeNull();
    expect(screen.queryByText('Saving changes…')).toBeNull();
    expect(screen.queryByText('Draft saved automatically.')).toBeNull();
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
    expect(mockUpdateManualDraft.mock.calls.at(-1)?.[1]).not.toHaveProperty('category');
    expect(mockUpdateManualDraft.mock.calls.at(-1)?.[1]).not.toHaveProperty('tags');
  });

  it('sends category and tags only when classification fields are edited', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: '10' } });
    const tagsSelect = screen.getByLabelText('Tags') as HTMLSelectElement;
    const tagOption = within(tagsSelect).getByRole('option', { name: 'Business' }) as HTMLOptionElement;
    tagOption.selected = true;
    fireEvent.change(tagsSelect);
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalled());
    expect(mockUpdateManualDraft).toHaveBeenLastCalledWith(
      77,
      expect.objectContaining({
        category: { id: 10 },
        tags: [{ id: 20 }],
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

  it('saving state does not show a large autosave progress banner', async () => {
    const patch = deferred<{ data: typeof readyCandidate }>();
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockUpdateManualDraft.mockReturnValueOnce(patch.promise);
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee updated' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalled());
    expect(screen.queryByText('Saving changes…')).toBeNull();
    expect(screen.queryByText('Draft saved automatically.')).toBeNull();

    await act(async () => {
      patch.resolve({ data: { ...readyCandidate, description: 'Coffee updated' } });
      await patch.promise;
    });
  });

  it('failed PATCH shows save error copy and disables Post', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockUpdateManualDraft.mockRejectedValue(new Error('boom'));
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Coffee updated' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    expect(await screen.findByText('Could not save changes.')).toBeTruthy();
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

  it('renders rule suggestions section for editable drafts', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByText('Rule suggestions')).toBeTruthy();
    expect(screen.getByText('Classification selected manually.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /refresh suggestions/i })).toBeNull();
    expect(await screen.findByRole('button', { name: /apply suggestions/i })).toBeTruthy();
    expect(screen.queryByText('Complete account, date, description, and amount to see suggestions.')).toBeNull();
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledWith(77));
  });

  it('unavailable preview state shows missing-fields copy without Apply or Confirm', async () => {
    mockGetManualDraft.mockResolvedValue({ data: candidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(await screen.findByText('Complete account, date, description, and amount to see suggestions.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /apply suggestions/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /confirm no suggestions/i })).toBeNull();
    expect(mockPreviewManualDraftRules).not.toHaveBeenCalled();
  });

  it('rule-input edits autosave and then auto-preview without mutating form selections', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Uber trip' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledWith(77, expect.objectContaining({ description: 'Uber trip' })));
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(2));
    expect(screen.getByLabelText('Category').value).toBe('');
    expect(screen.getByText('Suggested category')).toBeTruthy();
    expect(screen.getAllByText('Transport').length).toBeGreaterThan(1);
    expect(screen.getByText('Suggested tags')).toBeTruthy();
    expect(screen.getAllByText('Business').length).toBeGreaterThan(1);
    expect(screen.getByText('Uber rule')).toBeTruthy();
  });

  it('account, date, external reference, amount and flow edits trigger autosave then auto-preview', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockGetSelectableFinancialAccounts.mockResolvedValue(accounts);
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    await screen.findByRole('option', { name: /Savings/ });
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Account'), { target: { value: '2' } });
    fireEvent.change(screen.getByLabelText('Transaction date'), { target: { value: '2026-07-14' } });
    fireEvent.change(screen.getByLabelText('External Reference'), { target: { value: 'ext-123' } });
    fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '25' } });
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'IN' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() =>
      expect(mockUpdateManualDraft).toHaveBeenCalledWith(
        77,
        expect.objectContaining({
          account: { id: 2 },
          transactionDate: '2026-07-14',
          externalReference: 'ext-123',
          signedAmount: 25,
        }),
      ),
    );
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(2));
  });

  it('notes-only edits autosave but do not auto-preview again', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Notes'), { target: { value: 'memo only' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalledWith(77, expect.objectContaining({ notes: 'memo only' })));
    expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1);
  });

  it('manual category and tags edits autosave but do not auto-preview again', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: '10' } });
    const tagsSelect = screen.getByLabelText('Tags') as HTMLSelectElement;
    const tagOption = within(tagsSelect).getByRole('option', { name: 'Business' }) as HTMLOptionElement;
    tagOption.selected = true;
    fireEvent.change(tagsSelect);
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalled());
    expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1);
  });

  it('preview waits for autosave completion before calling rule-preview', async () => {
    const patch = deferred<{ data: typeof readyCandidate }>();
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockUpdateManualDraft.mockReturnValueOnce(patch.promise);
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Uber delayed' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalled());
    expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1);

    await act(async () => {
      patch.resolve({ data: { ...readyCandidate, description: 'Uber delayed' } });
      await patch.promise;
    });

    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(2));
  });

  it('stale preview response does not overwrite newer preview state', async () => {
    const firstPreview = deferred<{ data: typeof suggestionsPreview }>();
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockPreviewManualDraftRules.mockReturnValueOnce(firstPreview.promise).mockResolvedValueOnce({ data: noSuggestionsPreview });
    mockUpdateManualDraft.mockResolvedValueOnce({ data: { ...readyCandidate, description: 'No matching merchant' } });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(1));

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'No matching merchant' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockPreviewManualDraftRules).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole('button', { name: /confirm no suggestions/i })).toBeTruthy();

    await act(async () => {
      firstPreview.resolve({ data: suggestionsPreview });
      await firstPreview.promise;
    });

    expect(screen.queryByText('Suggested category')).toBeNull();
    expect(screen.getByRole('button', { name: /confirm no suggestions/i })).toBeTruthy();
  });

  it('failed automatic preview shows retry state', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockPreviewManualDraftRules.mockRejectedValue(new Error('preview failed'));
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    expect(screen.queryByRole('button', { name: /apply suggestions/i })).toBeNull();

    expect(await screen.findByText('Could not refresh rule suggestions.')).toBeTruthy();
    expect(screen.getByText('Could not update suggestions.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /retry suggestions/i })).toBeTruthy();
  });

  it('preview renders no-suggestions state', async () => {
    mockGetManualDraft.mockResolvedValue({ data: notEvaluatedReadyCandidate });
    mockPreviewManualDraftRules.mockResolvedValue({ data: noSuggestionsPreview });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(await screen.findByText('No rule suggestions found.')).toBeTruthy();
    expect(screen.getByText('Confirm that there are no applicable suggestions before posting.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /confirm no suggestions/i })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^apply suggestions$/i })).toBeNull();
    expect(screen.queryByText('Complete account, date, description, and amount to see suggestions.')).toBeNull();
  });

  it('stale state with an existing preview says displayed suggestions may be outdated and hides Apply', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    expect(await screen.findByText('Suggested category')).toBeTruthy();
    expect(screen.getByRole('button', { name: /apply suggestions/i })).toBeTruthy();

    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Uber changed' } });

    expect(screen.getByText('Displayed suggestions may be out of date.')).toBeTruthy();
    expect(screen.queryByText('Complete account, date, description, and amount to see suggestions.')).toBeNull();
    expect(screen.queryByRole('button', { name: /apply suggestions/i })).toBeNull();
    expect(screen.queryByRole('button', { name: /confirm no suggestions/i })).toBeNull();
    expect(screen.getByText('Suggested category')).toBeTruthy();
  });

  it('no-suggestions confirmation calls apply-rules explicitly', async () => {
    mockGetManualDraft.mockResolvedValue({ data: notEvaluatedReadyCandidate });
    mockPreviewManualDraftRules.mockResolvedValue({ data: noSuggestionsPreview });
    mockApplyManualDraftRules.mockResolvedValue({
      data: {
        candidate: { ...readyCandidate, classificationReviewStatus: 'NOT_APPLICABLE' },
        evaluation: noSuggestionsPreview,
      },
    });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    expect(await screen.findByText('No rule suggestions found.')).toBeTruthy();

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /confirm no suggestions/i }));
      await Promise.resolve();
    });

    await waitFor(() => expect(mockApplyManualDraftRules).toHaveBeenCalledWith(77));
    await act(async () => {
      await mockApplyManualDraftRules.mock.results[0].value;
    });
    await waitFor(() =>
      expect(screen.getByTestId('manual-draft-classification-status').textContent).toContain(
        'Classification reviewed with no applicable suggestions.',
      ),
    );
  });

  it('preview renders conflict warning', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    mockPreviewManualDraftRules.mockResolvedValue({
      data: {
        ...suggestionsPreview,
        hasConflicts: true,
        conflicts: [
          { field: 'CATEGORY', currentValueId: 11, currentValueLabel: 'Food', suggestedValueId: 10, suggestedValueLabel: 'Transport' },
        ],
      },
    });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(await screen.findByText('Some suggestions conflict with existing selections.')).toBeTruthy();
  });

  it('apply suggestions applies the latest successful preview and updates local category, tags and status', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');
    expect(await screen.findByRole('button', { name: /apply suggestions/i })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /apply suggestions/i }));

    expect(mockUpdateManualDraft).not.toHaveBeenCalled();
    await waitFor(() => expect(mockApplyManualDraftRules).toHaveBeenCalledWith(77));
    await waitFor(() => expect(screen.getByLabelText('Category').value).toBe('10'));
    expect(screen.getByLabelText('Tags').selectedOptions[0].value).toBe('20');
    await waitFor(() =>
      expect(screen.getByTestId('manual-draft-classification-status').textContent).toContain(
        'Classification reviewed with suggestions applied.',
      ),
    );
  });

  it('post is blocked when classificationReviewStatus is NOT_EVALUATED', async () => {
    mockGetManualDraft.mockResolvedValue({ data: notEvaluatedReadyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(await screen.findByText('Review pending: apply suggestions or manually select a category/tag before posting.')).toBeTruthy();
    expect(screen.getByText('Review classification before posting.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(true);
  });

  it('post is blocked when classificationReviewStatus is STALE', async () => {
    mockGetManualDraft.mockResolvedValue({ data: staleReadyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(screen.getByText('Details changed. Wait for suggestions to update and review classification before posting.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(true);
  });

  it('post is allowed when classificationReviewStatus is SUGGESTED', async () => {
    mockGetManualDraft.mockResolvedValue({ data: suggestedReadyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(await screen.findByText('Classification reviewed with suggestions applied.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(false);
  });

  it('post is allowed when classificationReviewStatus is USER_SELECTED', async () => {
    mockGetManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(await screen.findByText('Classification selected manually.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(false);
  });

  it('post is allowed when classificationReviewStatus is NOT_APPLICABLE', async () => {
    mockGetManualDraft.mockResolvedValue({ data: { ...readyCandidate, classificationReviewStatus: 'NOT_APPLICABLE' } });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    expect(await screen.findByText('Classification reviewed with no applicable suggestions.')).toBeTruthy();
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(false);
  });

  it('manual category change returned as USER_SELECTED allows post', async () => {
    mockGetManualDraft.mockResolvedValue({ data: notEvaluatedReadyCandidate });
    mockUpdateManualDraft.mockResolvedValue({ data: readyCandidate });
    renderManualDraft('/financial-transaction/drafts/77');
    await screen.findByDisplayValue('Coffee');

    fireEvent.change(screen.getByLabelText('Category'), { target: { value: '10' } });
    act(() => {
      jest.advanceTimersByTime(700);
    });

    await waitFor(() => expect(mockUpdateManualDraft).toHaveBeenCalled());
    await waitFor(() =>
      expect(screen.getByTestId('manual-draft-classification-status').textContent).toContain('Classification selected manually.'),
    );
    expect(screen.getByRole('button', { name: /post transaction/i }).disabled).toBe(false);
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
    mockGetSelectableFinancialAccounts.mockResolvedValue(accounts);
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByDisplayValue('Coffee')).toBeTruthy();
    await screen.findByRole('option', { name: /Checking/ });
    expect(screen.getByLabelText('Account').value).toBe('1');
    expect(screen.getByLabelText('Amount').value).toBe('12');
    expect(screen.getByLabelText('Type').value).toBe('OUT');
  });

  it('shows only active accounts for a new draft and retains its current inactive account for an existing draft', async () => {
    const inactiveHistoricalAccount = {
      id: 3,
      name: 'Closed checking',
      accountType: 'DEBIT',
      currency: 'MXN',
      lastFourDigits: '0001',
      active: false,
    };
    mockGetSelectableFinancialAccounts.mockImplementation(includeId =>
      Promise.resolve(includeId === 3 ? [...accounts, inactiveHistoricalAccount] : accounts),
    );

    const newDraft = renderManualDraft();
    expect(await screen.findByRole('option', { name: /Checking/ })).toBeTruthy();
    expect(screen.queryByRole('option', { name: /Closed checking/ })).toBeNull();
    newDraft.unmount();

    mockGetManualDraft.mockResolvedValue({ data: { ...readyCandidate, account: inactiveHistoricalAccount } });
    renderManualDraft('/financial-transaction/drafts/77');

    expect(await screen.findByRole('option', { name: /Closed checking.*Inactive/ })).toBeTruthy();
    expect((screen.getByLabelText('Account') as HTMLSelectElement).value).toBe('3');
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
    expect(mockPreviewManualDraftRules).not.toHaveBeenCalled();
    expect(mockApplyManualDraftRules).not.toHaveBeenCalled();
  });

  it('posted FinancialTransaction edit route does not use TransactionCandidate endpoints', () => {
    renderManualDraft('/financial-transaction/101/edit');

    expect(screen.getByDisplayValue('Existing transaction')).toBeTruthy();
    expect(mockCreateManualDraft).not.toHaveBeenCalled();
    expect(mockGetManualDraft).not.toHaveBeenCalled();
    expect(mockUpdateManualDraft).not.toHaveBeenCalled();
    expect(mockCancelManualDraft).not.toHaveBeenCalled();
    expect(mockPostManualDraft).not.toHaveBeenCalled();
    expect(mockPreviewManualDraftRules).not.toHaveBeenCalled();
    expect(mockApplyManualDraftRules).not.toHaveBeenCalled();
  });
});
