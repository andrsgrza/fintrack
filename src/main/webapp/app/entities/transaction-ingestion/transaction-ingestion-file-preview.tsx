import React, { FormEvent, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, Form, FormGroup, Input, Label, Row, Spinner, Table } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';

interface ICsvIngestionPreviewMessage {
  code?: string;
  message?: string;
}

interface ICsvIngestionPreviewCounts {
  recordsReceived?: number;
  recordsCreated?: number;
  recordsSkipped?: number;
  recordsRejected?: number;
  validRows?: number;
  invalidRows?: number;
}

interface ICsvIngestionPreviewRow {
  ingestionRecordId?: number;
  recordIndex?: number;
  status?: string;
  financialTransactionId?: number | null;
  transactionDate?: string;
  postingDate?: string | null;
  description?: string;
  signedAmount?: string;
  amount?: string;
  flow?: string;
  currency?: string;
  externalReference?: string | null;
  notes?: string | null;
  errorCode?: string | null;
  errorMessage?: string | null;
  warnings?: ICsvIngestionPreviewMessage[];
}

interface ICsvIngestionFileMetadata {
  originalFilename?: string;
  fileType?: string;
  contentType?: string | null;
  fileSizeBytes?: number;
  checksum?: string;
  parserName?: string;
  parserVersion?: string;
  statementStartDate?: string | null;
  statementEndDate?: string | null;
}

interface ICsvIngestionPreviewResponse {
  transactionIngestionId?: number;
  fileIngestionId?: number;
  status?: string;
  sourceLabel?: string;
  counts?: ICsvIngestionPreviewCounts;
  warnings?: ICsvIngestionPreviewMessage[];
  fileMetadata?: ICsvIngestionFileMetadata;
  rows?: ICsvIngestionPreviewRow[];
}

interface ICsvIngestionRecordReviewResponse {
  transactionIngestionId?: number;
  status?: string;
  counts?: ICsvIngestionPreviewCounts;
  row?: ICsvIngestionPreviewRow;
}

interface ICsvIngestionConfirmImportResponse {
  transactionIngestionId?: number;
  status?: string;
  createdNow?: number;
  alreadyImported?: number;
  skipped?: number;
  rejected?: number;
  failed?: number;
  counts?: ICsvIngestionPreviewCounts;
  rows?: ICsvIngestionPreviewRow[];
}

interface ICsvIngestionPreviewRowEditDraft {
  transactionDate?: string;
  postingDate?: string;
  description?: string;
  signedAmount?: string;
  currency?: string;
  externalReference?: string;
  notes?: string;
}

const createPreviewApiUrl = 'api/transaction-ingestions/file-preview';

const flowLabel = (flow?: string) => {
  if (!flow) {
    return '';
  }

  return translate(`fintrackApp.TransactionFlow.${flow}`, flow);
};

const renderWarningMessage = (warning: ICsvIngestionPreviewMessage) => {
  if (warning.code === 'DUPLICATE_FILE_CHECKSUM') {
    return <Translate contentKey="fintrackApp.transactionIngestion.filePreview.duplicateFile">Duplicate file</Translate>;
  }

  return warning.message || warning.code;
};

const renderIngestionStatus = (status?: string) => {
  if (!status) {
    return '';
  }

  return translate(`fintrackApp.transactionIngestion.filePreview.ingestionStatus.${status}`, status);
};

const recordStatusLabel = (status?: string) => {
  if (!status) {
    return '';
  }

  return translate(`fintrackApp.IngestionRecordStatus.${status}`, status);
};

const editableRowStatuses = ['VALID', 'REJECTED'];

const rowToEditDraft = (row: ICsvIngestionPreviewRow): ICsvIngestionPreviewRowEditDraft => ({
  transactionDate: row.transactionDate ?? '',
  postingDate: row.postingDate ?? '',
  description: row.description ?? '',
  signedAmount: row.signedAmount ?? '',
  currency: row.currency ?? '',
  externalReference: row.externalReference ?? '',
  notes: row.notes ?? '',
});

const optionalBlankToNull = (value?: string) => (value === undefined || value.trim() === '' ? null : value);

const editDraftPayload = (draft: ICsvIngestionPreviewRowEditDraft) => ({
  transactionDate: draft.transactionDate ?? null,
  postingDate: optionalBlankToNull(draft.postingDate),
  description: draft.description ?? null,
  signedAmount: draft.signedAmount ?? null,
  currency: draft.currency ?? null,
  externalReference: optionalBlankToNull(draft.externalReference),
  notes: optionalBlankToNull(draft.notes),
});

const replaceReviewRow = (currentRow: ICsvIngestionPreviewRow, updatedRow: ICsvIngestionPreviewRow): ICsvIngestionPreviewRow => ({
  ingestionRecordId: updatedRow.ingestionRecordId ?? currentRow.ingestionRecordId,
  recordIndex: updatedRow.recordIndex ?? currentRow.recordIndex,
  status: updatedRow.status,
  transactionDate: updatedRow.transactionDate,
  postingDate: updatedRow.postingDate,
  description: updatedRow.description,
  signedAmount: updatedRow.signedAmount,
  amount: updatedRow.amount,
  flow: updatedRow.flow,
  currency: updatedRow.currency,
  externalReference: updatedRow.externalReference,
  notes: updatedRow.notes,
  errorCode: updatedRow.errorCode,
  errorMessage: updatedRow.errorMessage,
  warnings: updatedRow.warnings ?? [],
});

export const TransactionIngestionFilePreview = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isReviewPage = Boolean(id);

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const financialAccountsLoading = useAppSelector(state => state.financialAccount.loading);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [accountId, setAccountId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const [backendError, setBackendError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ICsvIngestionPreviewResponse | null>(null);
  const [loadingReview, setLoadingReview] = useState(false);
  const [reviewActionInProgress, setReviewActionInProgress] = useState<number | null>(null);
  const [confirmingImport, setConfirmingImport] = useState(false);
  const [editingRecordId, setEditingRecordId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<ICsvIngestionPreviewRowEditDraft>({});

  useEffect(() => {
    if (!isReviewPage) {
      dispatch(getFinancialAccounts({ sort: 'name,asc' }));
    }
  }, [isReviewPage]);

  useEffect(() => {
    if (!isReviewPage || !id) {
      return;
    }

    setLoadingReview(true);
    setBackendError(null);
    axios
      .get<ICsvIngestionPreviewResponse>(`api/transaction-ingestions/${id}/file-preview`)
      .then(response => setPreview(response.data))
      .catch(() => setBackendError(translate('fintrackApp.transactionIngestion.filePreview.errors.reviewLoadFailed')))
      .finally(() => setLoadingReview(false));
  }, [isReviewPage, id]);

  const clearFileInput = () => {
    setFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const applyReviewResponse = (data: ICsvIngestionRecordReviewResponse) => {
    if (!data.row) {
      return;
    }
    const updatedRow = data.row;
    setPreview(currentPreview => {
      if (!currentPreview) {
        return currentPreview;
      }
      return {
        ...currentPreview,
        status: data.status ?? currentPreview.status,
        counts: data.counts ?? currentPreview.counts,
        rows: (currentPreview.rows ?? []).map(row => {
          const sameRecord =
            row.ingestionRecordId === updatedRow.ingestionRecordId ||
            (updatedRow.ingestionRecordId === undefined && row.recordIndex === updatedRow.recordIndex);
          return sameRecord ? replaceReviewRow(row, updatedRow) : row;
        }),
      };
    });
  };

  const applyConfirmResponse = (data: ICsvIngestionConfirmImportResponse) => {
    setPreview(currentPreview => {
      if (!currentPreview) {
        return currentPreview;
      }
      return {
        ...currentPreview,
        status: data.status ?? currentPreview.status,
        counts: data.counts ?? currentPreview.counts,
        rows: data.rows ?? currentPreview.rows,
      };
    });
    setEditingRecordId(null);
    setEditDraft({});
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLocalError(null);
    setBackendError(null);

    if (!accountId) {
      setLocalError('fintrackApp.transactionIngestion.filePreview.errors.accountRequired');
      return;
    }

    if (!file) {
      setLocalError('fintrackApp.transactionIngestion.filePreview.errors.fileRequired');
      return;
    }

    const formData = new FormData();
    formData.append('accountId', accountId);
    formData.append('file', file);

    setSubmitting(true);
    try {
      const response = await axios.post<ICsvIngestionPreviewResponse>(createPreviewApiUrl, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const transactionIngestionId = response.data.transactionIngestionId;
      if (transactionIngestionId) {
        navigate(`/transaction-ingestion/${transactionIngestionId}/file-preview`);
      }
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.filePreview.errors.previewFailed'));
    } finally {
      clearFileInput();
      setSubmitting(false);
    }
  };

  const performRowAction = async (row: ICsvIngestionPreviewRow, action: 'disable' | 'enable') => {
    if (!row.ingestionRecordId || !preview?.transactionIngestionId) {
      return;
    }
    setBackendError(null);
    setReviewActionInProgress(row.ingestionRecordId);
    try {
      const response = await axios.post<ICsvIngestionRecordReviewResponse>(
        `api/transaction-ingestions/${preview.transactionIngestionId}/records/${row.ingestionRecordId}/${action}`,
      );
      applyReviewResponse(response.data);
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.filePreview.errors.reviewFailed'));
    } finally {
      setReviewActionInProgress(null);
    }
  };

  const startEditingRow = (row: ICsvIngestionPreviewRow) => {
    if (!row.ingestionRecordId) {
      return;
    }
    setBackendError(null);
    setEditingRecordId(row.ingestionRecordId);
    setEditDraft(rowToEditDraft(row));
  };

  const updateEditDraft = (field: keyof ICsvIngestionPreviewRowEditDraft, value: string) => {
    setEditDraft(currentDraft => ({ ...currentDraft, [field]: value }));
  };

  const cancelEditingRow = () => {
    setEditingRecordId(null);
    setEditDraft({});
  };

  const saveEditingRow = async (row: ICsvIngestionPreviewRow) => {
    if (!row.ingestionRecordId || !preview?.transactionIngestionId) {
      return;
    }
    setBackendError(null);
    setReviewActionInProgress(row.ingestionRecordId);
    try {
      const response = await axios.patch<ICsvIngestionRecordReviewResponse>(
        `api/transaction-ingestions/${preview.transactionIngestionId}/records/${row.ingestionRecordId}`,
        editDraftPayload(editDraft),
      );
      applyReviewResponse(response.data);
      cancelEditingRow();
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.filePreview.errors.reviewFailed'));
    } finally {
      setReviewActionInProgress(null);
    }
  };

  const counts = preview?.counts ?? {};
  const rows = preview?.rows ?? [];
  const warnings = preview?.warnings ?? [];
  const fileMetadata = preview?.fileMetadata;
  const reviewActionsEnabled = preview?.status === 'READY' || preview?.status === 'PARTIALLY_READY';
  const confirmAvailable = preview?.status === 'READY' && (counts.validRows ?? 0) > 0;
  const processing = preview?.status === 'PROCESSING' || confirmingImport;

  const canDisable = (row: ICsvIngestionPreviewRow) => reviewActionsEnabled && ['VALID', 'REJECTED'].includes(row.status ?? '');
  const canEnable = (row: ICsvIngestionPreviewRow) => reviewActionsEnabled && row.status === 'DISABLED';
  const canEdit = (row: ICsvIngestionPreviewRow) => reviewActionsEnabled && editableRowStatuses.includes(row.status ?? '');

  const confirmImport = async () => {
    if (!preview?.transactionIngestionId || !confirmAvailable) {
      return;
    }
    setBackendError(null);
    setConfirmingImport(true);
    try {
      const response = await axios.post<ICsvIngestionConfirmImportResponse>(
        `api/transaction-ingestions/${preview.transactionIngestionId}/confirm`,
      );
      applyConfirmResponse(response.data);
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.filePreview.errors.confirmFailed'));
    } finally {
      setConfirmingImport(false);
    }
  };

  const renderCreateForm = () => (
    <Row className="justify-content-center">
      <Col md="10">
        <h2 data-cy="TransactionIngestionFilePreviewHeading">
          <Translate contentKey="fintrackApp.transactionIngestion.filePreview.title">File import preview</Translate>
        </h2>
        <p className="text-muted">
          <Translate contentKey="fintrackApp.transactionIngestion.filePreview.help">
            Upload a canonical FINTRACK CSV file to create a persisted preview.
          </Translate>
        </p>

        <Alert color="info" fade={false}>
          <Translate contentKey="fintrackApp.transactionIngestion.filePreview.previewOnly">
            Preview only — no transactions were created.
          </Translate>
        </Alert>

        {localError ? (
          <Alert color="danger" data-cy="filePreviewLocalError" fade={false}>
            <Translate contentKey={localError} />
          </Alert>
        ) : null}
        {backendError ? (
          <Alert color="danger" data-cy="filePreviewBackendError" fade={false}>
            {backendError}
          </Alert>
        ) : null}

        <Form onSubmit={handleSubmit}>
          <FormGroup>
            <Label for="file-preview-account">
              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.account">Account</Translate>
            </Label>
            <Input
              id="file-preview-account"
              data-cy="filePreviewAccount"
              type="select"
              value={accountId}
              disabled={submitting || financialAccountsLoading}
              onChange={event => setAccountId(event.target.value)}
            >
              <option value="" />
              {financialAccounts.map(account => (
                <option value={account.id} key={account.id}>
                  {account.name}
                  {account.currency ? ` (${account.currency})` : ''}
                </option>
              ))}
            </Input>
          </FormGroup>
          <FormGroup>
            <Label for="file-preview-file">
              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.csvFile">CSV file</Translate>
            </Label>
            <Input
              id="file-preview-file"
              data-cy="filePreviewFile"
              type="file"
              innerRef={fileInputRef}
              accept=".csv,text/csv"
              disabled={submitting}
              onChange={event => setFile(event.target.files?.[0] ?? null)}
            />
            <small className="form-text text-muted">
              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.csvHint">
                Expected header: transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
              </Translate>
            </small>
          </FormGroup>
          <Button color="primary" type="submit" disabled={submitting} data-cy="filePreviewSubmit">
            {submitting ? <Spinner size="sm" /> : <FontAwesomeIcon icon="eye" />}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.filePreview.preview">Preview</Translate>
          </Button>
          &nbsp;
          <Button tag={Link} to="/transaction-ingestion" replace color="info">
            <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
          </Button>
        </Form>
      </Col>
    </Row>
  );

  const renderReview = () => (
    <Row className="justify-content-center mt-4">
      <Col md="12">
        <h2 data-cy="filePreviewReviewHeading">
          <Translate contentKey="fintrackApp.transactionIngestion.filePreview.reviewTitle">Review import</Translate>
        </h2>

        <Alert color="info" fade={false}>
          <Translate contentKey="fintrackApp.transactionIngestion.filePreview.previewOnly">
            Preview only — no transactions were created.
          </Translate>
        </Alert>

        {backendError ? (
          <Alert color="danger" data-cy="filePreviewBackendError" fade={false}>
            {backendError}
          </Alert>
        ) : null}

        {loadingReview ? (
          <div>
            <Spinner size="sm" /> <Translate contentKey="entity.action.loading">Loading...</Translate>
          </div>
        ) : null}

        {preview ? (
          <>
            {preview.status === 'PARTIALLY_READY' ? (
              <Alert color="warning" data-cy="filePreviewConfirmBlocked" fade={false}>
                {(counts.validRows ?? 0) === 0 ? (
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.noValidRows">
                    There are no valid rows to import.
                  </Translate>
                ) : (
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.fixRejectedRows">
                    Fix or disable rejected rows before importing.
                  </Translate>
                )}
              </Alert>
            ) : null}

            {preview.status === 'COMPLETED' ? (
              <Alert color="success" data-cy="filePreviewCompleted" fade={false}>
                <Translate contentKey="fintrackApp.transactionIngestion.filePreview.importCompleted">Import completed.</Translate>
              </Alert>
            ) : null}

            {preview.status === 'PROCESSING' ? (
              <Alert color="info" data-cy="filePreviewProcessing" fade={false}>
                <Spinner size="sm" />{' '}
                <Translate contentKey="fintrackApp.transactionIngestion.filePreview.importProcessing">Import is processing.</Translate>
              </Alert>
            ) : null}

            {warnings.length > 0 ? (
              <Alert color="warning" data-cy="filePreviewWarnings" fade={false}>
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.warnings">Warnings</Translate>
                </strong>
                <ul className="mb-0">
                  {warnings.map((warning, index) => (
                    <li key={`${warning.code}-${index}`}>{renderWarningMessage(warning)}</li>
                  ))}
                </ul>
              </Alert>
            ) : null}

            <dl className="jh-entity-details">
              <dt>
                <Translate contentKey="fintrackApp.transactionIngestion.filePreview.transactionIngestionId">
                  Transaction Ingestion ID
                </Translate>
              </dt>
              <dd>{preview.transactionIngestionId}</dd>
              <dt>
                <Translate contentKey="fintrackApp.transactionIngestion.filePreview.fileIngestionId">File Ingestion ID</Translate>
              </dt>
              <dd>{preview.fileIngestionId}</dd>
              <dt>
                <Translate contentKey="fintrackApp.transactionIngestion.status">Status</Translate>
              </dt>
              <dd>{renderIngestionStatus(preview.status)}</dd>
              <dt>
                <Translate contentKey="fintrackApp.transactionIngestion.sourceLabel">Source Label</Translate>
              </dt>
              <dd>{preview.sourceLabel}</dd>
            </dl>

            {fileMetadata ? (
              <>
                <h3>
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.fileMetadata">File metadata</Translate>
                </h3>
                <dl className="jh-entity-details" data-cy="filePreviewMetadata">
                  <dt>
                    <Translate contentKey="fintrackApp.transactionIngestion.filePreview.originalFilename">Original filename</Translate>
                  </dt>
                  <dd>{fileMetadata.originalFilename}</dd>
                  <dt>
                    <Translate contentKey="fintrackApp.transactionIngestion.filePreview.fileType">File type</Translate>
                  </dt>
                  <dd>{fileMetadata.fileType}</dd>
                  <dt>
                    <Translate contentKey="fintrackApp.transactionIngestion.filePreview.contentType">Content type</Translate>
                  </dt>
                  <dd>{fileMetadata.contentType}</dd>
                  <dt>
                    <Translate contentKey="fintrackApp.transactionIngestion.filePreview.fileSizeBytes">File size bytes</Translate>
                  </dt>
                  <dd>{fileMetadata.fileSizeBytes}</dd>
                  <dt>
                    <Translate contentKey="fintrackApp.transactionIngestion.filePreview.checksum">Checksum</Translate>
                  </dt>
                  <dd>{fileMetadata.checksum}</dd>
                  <dt>
                    <Translate contentKey="fintrackApp.transactionIngestion.filePreview.parser">Parser</Translate>
                  </dt>
                  <dd>
                    {fileMetadata.parserName} {fileMetadata.parserVersion}
                  </dd>
                  <dt>
                    <Translate contentKey="fintrackApp.transactionIngestion.filePreview.statementPeriod">Statement period</Translate>
                  </dt>
                  <dd>
                    {fileMetadata.statementStartDate} - {fileMetadata.statementEndDate}
                  </dd>
                </dl>
              </>
            ) : null}

            <Row className="mb-3" data-cy="filePreviewCounts" data-testid="filePreviewCounts">
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.recordsReceived">Records received</Translate>
                </strong>
                <div>{counts.recordsReceived ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.validRows">Valid rows</Translate>
                </strong>
                <div>{counts.validRows ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.invalidRows">Invalid rows</Translate>
                </strong>
                <div>{counts.invalidRows ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.disabledRows">Disabled rows</Translate>
                </strong>
                <div>{counts.recordsSkipped ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.recordsRejected">Records rejected</Translate>
                </strong>
                <div>{counts.recordsRejected ?? 0}</div>
              </Col>
            </Row>

            {confirmAvailable ? (
              <div className="mb-3">
                <Button color="success" disabled={confirmingImport} onClick={confirmImport} data-cy="filePreviewConfirmImport">
                  {confirmingImport ? <Spinner size="sm" /> : null}{' '}
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.confirmImport">Confirm Import</Translate>
                </Button>
              </div>
            ) : null}

            <div className="table-responsive">
              <Table responsive data-cy="filePreviewRows">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.status">Status</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.transactionDate">Transaction date</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.postingDate">Posting date</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.description">Description</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.signedAmount">Signed amount</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.amount">Amount</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.flow">Flow</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.currency">Currency</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.externalReference">External reference</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.notes">Notes</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.error">Error</Translate>
                    </th>
                    <th>
                      <Translate contentKey="entity.action.actions">Actions</Translate>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(row => {
                    const actionBusy = processing || reviewActionInProgress === row.ingestionRecordId;
                    const isEditing = editingRecordId === row.ingestionRecordId;
                    return (
                      <tr key={row.ingestionRecordId ?? row.recordIndex} className={row.status === 'DISABLED' ? 'text-muted' : ''}>
                        <td>{row.recordIndex}</td>
                        <td>
                          <span data-testid={`filePreviewRowStatus-${row.ingestionRecordId ?? row.recordIndex}`}>
                            {recordStatusLabel(row.status)}
                          </span>
                        </td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.filePreview.transactionDate')}
                              value={editDraft.transactionDate ?? ''}
                              onChange={event => updateEditDraft('transactionDate', event.target.value)}
                            />
                          ) : (
                            row.transactionDate
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.filePreview.postingDate')}
                              value={editDraft.postingDate ?? ''}
                              onChange={event => updateEditDraft('postingDate', event.target.value)}
                            />
                          ) : (
                            row.postingDate
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.filePreview.description')}
                              value={editDraft.description ?? ''}
                              onChange={event => updateEditDraft('description', event.target.value)}
                            />
                          ) : (
                            row.description
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.filePreview.signedAmount')}
                              value={editDraft.signedAmount ?? ''}
                              onChange={event => updateEditDraft('signedAmount', event.target.value)}
                            />
                          ) : (
                            row.signedAmount
                          )}
                        </td>
                        <td>{row.amount}</td>
                        <td>{flowLabel(row.flow)}</td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.filePreview.currency')}
                              value={editDraft.currency ?? ''}
                              onChange={event => updateEditDraft('currency', event.target.value)}
                            />
                          ) : (
                            row.currency
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.filePreview.externalReference')}
                              value={editDraft.externalReference ?? ''}
                              onChange={event => updateEditDraft('externalReference', event.target.value)}
                            />
                          ) : (
                            row.externalReference
                          )}
                        </td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.filePreview.notes')}
                              value={editDraft.notes ?? ''}
                              onChange={event => updateEditDraft('notes', event.target.value)}
                            />
                          ) : (
                            row.notes
                          )}
                        </td>
                        <td>{row.errorMessage || row.errorCode}</td>
                        <td className="text-end">
                          {isEditing ? (
                            <>
                              <Button size="sm" color="primary" disabled={actionBusy} onClick={() => saveEditingRow(row)}>
                                <Translate contentKey="fintrackApp.transactionIngestion.filePreview.saveRow">Save row</Translate>
                              </Button>{' '}
                              <Button size="sm" color="secondary" disabled={actionBusy} onClick={cancelEditingRow}>
                                <Translate contentKey="fintrackApp.transactionIngestion.filePreview.cancel">Cancel</Translate>
                              </Button>
                            </>
                          ) : null}
                          {!isEditing && canEdit(row) ? (
                            <Button size="sm" color="primary" disabled={actionBusy} onClick={() => startEditingRow(row)}>
                              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.edit">Edit</Translate>
                            </Button>
                          ) : null}{' '}
                          {!isEditing && canDisable(row) ? (
                            <Button size="sm" color="warning" disabled={actionBusy} onClick={() => performRowAction(row, 'disable')}>
                              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.disable">Disable</Translate>
                            </Button>
                          ) : null}
                          {!isEditing && canEnable(row) ? (
                            <Button size="sm" color="success" disabled={actionBusy} onClick={() => performRowAction(row, 'enable')}>
                              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.enable">Enable</Translate>
                            </Button>
                          ) : null}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            </div>
          </>
        ) : null}
      </Col>
    </Row>
  );

  return <div>{isReviewPage ? renderReview() : renderCreateForm()}</div>;
};

export default TransactionIngestionFilePreview;
