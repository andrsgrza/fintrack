import React from 'react';
import axios from 'axios';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TranslatorContext } from 'react-jhipster';
import { MemoryRouter, Route, Routes } from 'react-router';

import enFileIngestion from 'app/../i18n/en/fileIngestion.json';
import { FileIngestionUpdate } from './file-ingestion-update';

jest.mock('axios');

const mockAxiosPost = axios.post as jest.Mock;
const mockDispatch = jest.fn();
const mockGetEntity = jest.fn(id => ({ type: 'fileIngestion/getEntity', payload: id }));
const mockReset = jest.fn(() => ({ type: 'fileIngestion/reset' }));
const mockUpdateEntity = jest.fn(entity => ({ type: 'fileIngestion/updateEntity', payload: entity }));
const mockGetFileIngestionParentCandidates = jest.fn(() => ({
  type: 'transactionIngestion/fetch_file_ingestion_parent_candidates',
}));
let mockState;

jest.mock('app/config/store', () => ({
  useAppDispatch: () => mockDispatch,
  useAppSelector: selector => selector(mockState),
}));

jest.mock('./file-ingestion.reducer', () => ({
  getEntity: id => mockGetEntity(id),
  reset: () => mockReset(),
  updateEntity: entity => mockUpdateEntity(entity),
}));

jest.mock('app/entities/transaction-ingestion/transaction-ingestion.reducer', () => ({
  getEntitiesWhereFileIngestionIsNull: () => mockGetFileIngestionParentCandidates(),
}));

const baseState = {
  fileIngestion: {
    entity: {},
    loading: false,
    updating: false,
    updateSuccess: false,
  },
  transactionIngestion: {
    fileIngestionParentCandidates: [
      { id: 10, ingestionType: 'FILE', status: 'PENDING' },
      { id: 11, ingestionType: 'FILE', status: 'READY' },
      { id: 12, ingestionType: 'API', status: 'PENDING' },
    ],
  },
};

const registerTranslations = () => {
  TranslatorContext.registerTranslations('en', enFileIngestion);
  TranslatorContext.setLocale('en');
};

const renderCreateForm = () => {
  mockState = baseState;

  return render(
    <MemoryRouter initialEntries={['/file-ingestion/new']}>
      <Routes>
        <Route path="/file-ingestion/new" element={<FileIngestionUpdate />} />
        <Route path="/transaction-ingestion/:id/file-preview" element={<div>Review route</div>} />
      </Routes>
    </MemoryRouter>,
  );
};

const selectPendingParent = () => {
  const parentSelector = screen.getByLabelText('Transaction Ingestion') as HTMLSelectElement;
  fireEvent.change(parentSelector, { target: { value: '10' } });
  return parentSelector;
};

const uploadFile = () => {
  const file = new File(['transactionDate,postingDate,description,signedAmount,currency,externalReference,notes'], 'upload.csv', {
    type: 'text/csv',
  });
  const input = screen.getByLabelText('CSV file') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [file] } });
  return input;
};

describe('FileIngestion create upload form', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    registerTranslations();
  });

  it('shows only parent selector and CSV file input in create mode', () => {
    renderCreateForm();

    expect(screen.getByLabelText('Transaction Ingestion')).not.toBeNull();
    expect(screen.getByLabelText('CSV file')).not.toBeNull();
    expect(screen.getByRole('option', { name: '10' })).not.toBeNull();
    expect(screen.queryByRole('option', { name: '11' })).toBeNull();
    expect(screen.queryByRole('option', { name: '12' })).toBeNull();

    expect(screen.queryByLabelText('Original Filename')).toBeNull();
    expect(screen.queryByLabelText('File Type')).toBeNull();
    expect(screen.queryByLabelText('Content Type')).toBeNull();
    expect(screen.queryByLabelText('File Size Bytes')).toBeNull();
    expect(screen.queryByLabelText('Checksum')).toBeNull();
    expect(screen.queryByLabelText('Storage Key')).toBeNull();
    expect(screen.queryByLabelText('Parser Name')).toBeNull();
    expect(screen.queryByLabelText('Parser Version')).toBeNull();
    expect(screen.queryByLabelText('Statement Start Date')).toBeNull();
    expect(screen.queryByLabelText('Statement End Date')).toBeNull();
  });

  it('submits multipart upload to parent command endpoint and redirects to review route', async () => {
    mockAxiosPost.mockResolvedValue({ data: { transactionIngestionId: 10 } });
    renderCreateForm();

    selectPendingParent();
    uploadFile();
    fireEvent.click(screen.getByRole('button', { name: /Upload CSV/ }));

    await waitFor(() => {
      expect(mockAxiosPost).toHaveBeenCalledWith(
        'api/transaction-ingestions/10/file-ingestion',
        expect.any(FormData),
        expect.objectContaining({ headers: { 'Content-Type': 'multipart/form-data' } }),
      );
    });
    await waitFor(() => expect(screen.getByText('Review route')).not.toBeNull());
  });

  it('shows backend validation errors and clears the file input after failure', async () => {
    mockAxiosPost.mockRejectedValue({ response: { data: { detail: 'Transaction ingestion must be PENDING before file upload' } } });
    renderCreateForm();

    selectPendingParent();
    const input = uploadFile();
    fireEvent.click(screen.getByRole('button', { name: /Upload CSV/ }));

    await waitFor(() => expect(screen.getByText('Transaction ingestion must be PENDING before file upload')).not.toBeNull());
    expect(input.value).toBe('');
  });
});
