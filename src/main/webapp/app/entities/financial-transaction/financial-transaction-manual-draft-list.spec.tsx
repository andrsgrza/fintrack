import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFinancialTransaction from 'app/../i18n/en/financialTransaction.json';
import enTransactionFlow from 'app/../i18n/en/transactionFlow.json';
import FinancialTransactionManualDraftList from './financial-transaction-manual-draft-list';
import { cancelManualDraft, getManualDrafts } from './services/manual-transaction-candidate.service';

jest.mock('./services/manual-transaction-candidate.service');

const mockGetManualDrafts = getManualDrafts as jest.Mock;
const mockCancelManualDraft = cancelManualDraft as jest.Mock;

const renderDraftList = () =>
  render(
    <MemoryRouter initialEntries={['/financial-transaction/drafts']}>
      <Routes>
        <Route path="/financial-transaction/drafts" element={<FinancialTransactionManualDraftList />} />
      </Routes>
    </MemoryRouter>,
  );

const findDraftRow = async () => {
  await screen.findByText('Coffee');
  const row = document.querySelector('[data-cy="manualDraftRow"]') as HTMLElement | null;
  expect(row).not.toBeNull();
  return row!;
};

const draftSummary = {
  id: 77,
  status: 'READY_TO_POST',
  classificationReviewStatus: 'USER_SELECTED',
  accountId: 1,
  accountName: 'Checking',
  transactionDate: '2026-07-13',
  description: 'Coffee',
  amount: 12,
  flow: 'OUT',
  currencySnapshot: 'MXN',
  createdAt: '2026-07-13T16:00:00Z',
  updatedAt: '2026-07-13T17:00:00Z',
  categoryName: 'Transport',
  tagNames: ['Business', 'Personal'],
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFinancialTransaction);
  TranslatorContext.registerTranslations('en', enTransactionFlow);
  TranslatorContext.setLocale('en');
};

describe('FinancialTransaction manual draft recovery list', () => {
  beforeAll(registerTranslations);

  beforeEach(() => {
    mockGetManualDrafts.mockReset();
    mockCancelManualDraft.mockReset();
    mockCancelManualDraft.mockResolvedValue({ data: { ...draftSummary, status: 'CANCELLED' } });
  });

  it('loads and renders manual drafts with compact summary data', async () => {
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });

    renderDraftList();

    expect(screen.getByText('Loading manual drafts…')).toBeTruthy();
    expect(await screen.findByText('Manual transaction drafts')).toBeTruthy();
    const row = await findDraftRow();
    expect(within(row).getByText('Coffee')).toBeTruthy();
    expect(within(row).getByText('Checking')).toBeTruthy();
    expect(within(row).getByText('13/07/2026')).toBeTruthy();
    expect(within(row).getByText(/12/)).toBeTruthy();
    expect(within(row).getByText('Expense')).toBeTruthy();
    expect(row.textContent).toContain('MXN');
    expect(within(row).getByText('Ready to post')).toBeTruthy();
    expect(within(row).getByText('Manually selected')).toBeTruthy();
    expect(within(row).getByText('Transport')).toBeTruthy();
    expect(within(row).getByText('Business, Personal')).toBeTruthy();
    expect(within(row).queryByText('77')).toBeNull();
  });

  it('shows empty state with create CTA', async () => {
    mockGetManualDrafts.mockResolvedValue({ data: [] });

    renderDraftList();

    expect(await screen.findByText('No manual drafts.')).toBeTruthy();
    expect(screen.getAllByRole('link', { name: /create manual transaction/i })[1].getAttribute('href')).toBe('/financial-transaction/new');
  });

  it('shows safe fallback labels for incomplete or unknown draft summaries', async () => {
    mockGetManualDrafts.mockResolvedValue({
      data: [
        {
          id: 88,
          status: 'SOMETHING_NEW',
          classificationReviewStatus: 'CLASSIFICATION_UNKNOWN',
          tagNames: [],
        },
      ],
    });

    renderDraftList();

    await screen.findByText('Untitled draft');
    const row = document.querySelector('[data-cy="manualDraftRow"]') as HTMLElement;
    expect(within(row).getByText('Untitled draft')).toBeTruthy();
    expect(within(row).getByText('No account')).toBeTruthy();
    expect(within(row).getByText('No date')).toBeTruthy();
    expect(within(row).getByText('No amount')).toBeTruthy();
    expect(within(row).getByText('SOMETHING_NEW')).toBeTruthy();
    expect(within(row).getByText('CLASSIFICATION_UNKNOWN')).toBeTruthy();
    expect(within(row).getByText('No category')).toBeTruthy();
    expect(within(row).getByText('No tags')).toBeTruthy();
  });

  it('links resume action to the manual draft route', async () => {
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });

    renderDraftList();

    const row = await findDraftRow();
    expect(
      within(row)
        .getByRole('link', { name: /resume/i })
        .getAttribute('href'),
    ).toBe('/financial-transaction/drafts/77');
  });

  it('cancels a draft through the command endpoint and removes it from the list', async () => {
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });

    renderDraftList();
    const row = await findDraftRow();
    fireEvent.click(within(row).getByRole('button', { name: /cancel draft/i }));

    await waitFor(() => expect(mockCancelManualDraft).toHaveBeenCalledWith(77));
    await waitFor(() => expect(screen.queryByText('Coffee')).toBeNull());
    expect(screen.getByText('No manual drafts.')).toBeTruthy();
    confirmSpy.mockRestore();
  });

  it('keeps the draft visible and shows an error when cancel fails', async () => {
    const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
    mockGetManualDrafts.mockResolvedValue({ data: [draftSummary] });
    mockCancelManualDraft.mockRejectedValue(new Error('boom'));

    renderDraftList();
    const row = await findDraftRow();
    fireEvent.click(within(row).getByRole('button', { name: /cancel draft/i }));

    expect(await screen.findByText('Could not cancel the draft.')).toBeTruthy();
    expect(screen.getByText('Coffee')).toBeTruthy();
    confirmSpy.mockRestore();
  });

  it('shows a clear load error if manual drafts cannot be fetched', async () => {
    mockGetManualDrafts.mockRejectedValue(new Error('boom'));

    renderDraftList();

    expect(await screen.findByText('Could not load manual drafts.')).toBeTruthy();
    expect(screen.queryByText('No manual drafts.')).toBeNull();
  });
});
