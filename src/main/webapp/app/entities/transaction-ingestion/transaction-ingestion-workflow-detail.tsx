import React, { FormEvent, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, Form, FormGroup, Input, Label, Row, Spinner, Table } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { getEntity } from './transaction-ingestion.reducer';

interface ICsvIngestionWorkflowMessage {
  code?: string;
  message?: string;
}

interface ICsvIngestionWorkflowCounts {
  recordsReceived?: number;
  recordsCreated?: number;
  recordsSkipped?: number;
  recordsRejected?: number;
  validRows?: number;
  invalidRows?: number;
}

interface ICsvIngestionWorkflowRow {
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
  warnings?: ICsvIngestionWorkflowMessage[];
}

interface ICsvIngestionFileMetadata {
  originalFilename?: string;
  fileType?: string;
  contentType?: string | null;
  fileSizeBytes?: number;
  checksum?: string;
  storageKey?: string | null;
  parserName?: string;
  parserVersion?: string;
  statementStartDate?: string | null;
  statementEndDate?: string | null;
  createdAt?: string;
}

interface ICsvIngestionWorkflowResponse {
  transactionIngestionId?: number;
  fileIngestionId?: number;
  status?: string;
  sourceLabel?: string;
  counts?: ICsvIngestionWorkflowCounts;
  warnings?: ICsvIngestionWorkflowMessage[];
  fileMetadata?: ICsvIngestionFileMetadata;
  rows?: ICsvIngestionWorkflowRow[];
}

interface ICsvIngestionRecordReviewResponse {
  transactionIngestionId?: number;
  status?: string;
  counts?: ICsvIngestionWorkflowCounts;
  row?: ICsvIngestionWorkflowRow;
}

interface ICsvIngestionConfirmImportResponse {
  transactionIngestionId?: number;
  status?: string;
  createdNow?: number;
  alreadyImported?: number;
  skipped?: number;
  rejected?: number;
  failed?: number;
  counts?: ICsvIngestionWorkflowCounts;
  rows?: ICsvIngestionWorkflowRow[];
}

interface ICsvIngestionWorkflowRowEditDraft {
  transactionDate?: string;
  postingDate?: string;
  description?: string;
  signedAmount?: string;
  currency?: string;
  externalReference?: string;
  notes?: string;
}

const createWorkflowApiUrl = 'api/transaction-ingestions/file';

const flowLabel = (flow?: string) => {
  if (!flow) {
    return '';
  }

  return translate(`fintrackApp.TransactionFlow.${flow}`, flow);
};

const renderWarningMessage = (warning: ICsvIngestionWorkflowMessage) => {
  if (warning.code === 'DUPLICATE_FILE_CHECKSUM') {
    return <Translate contentKey="fintrackApp.transactionIngestion.workflow.duplicateFile">Duplicate file</Translate>;
  }

  return warning.message || warning.code;
};

const renderIngestionStatus = (status?: string) => {
  if (!status) {
    return '';
  }

  return translate(`fintrackApp.transactionIngestion.workflow.ingestionStatus.${status}`, status);
};

const recordStatusLabel = (status?: string) => {
  if (!status) {
    return '';
  }

  return translate(`fintrackApp.IngestionRecordStatus.${status}`, status);
};

const editableRowStatuses = ['VALID', 'REJECTED'];

const rowToEditDraft = (row: ICsvIngestionWorkflowRow): ICsvIngestionWorkflowRowEditDraft => ({
  transactionDate: row.transactionDate ?? '',
  postingDate: row.postingDate ?? '',
  description: row.description ?? '',
  signedAmount: row.signedAmount ?? '',
  currency: row.currency ?? '',
  externalReference: row.externalReference ?? '',
  notes: row.notes ?? '',
});

const optionalBlankToNull = (value?: string) => (value === undefined || value.trim() === '' ? null : value);

const editDraftPayload = (draft: ICsvIngestionWorkflowRowEditDraft) => ({
  transactionDate: draft.transactionDate ?? null,
  postingDate: optionalBlankToNull(draft.postingDate),
  description: draft.description ?? null,
  signedAmount: draft.signedAmount ?? null,
  currency: draft.currency ?? null,
  externalReference: optionalBlankToNull(draft.externalReference),
  notes: optionalBlankToNull(draft.notes),
});

const replaceReviewRow = (currentRow: ICsvIngestionWorkflowRow, updatedRow: ICsvIngestionWorkflowRow): ICsvIngestionWorkflowRow => ({
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

export const TransactionIngestionWorkflowDetail = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isReviewPage = Boolean(id);

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const financialAccountsLoading = useAppSelector(state => state.financialAccount.loading);
  const transactionIngestionEntity = useAppSelector(state => state.transactionIngestion.entity);
  const transactionIngestionLoading = useAppSelector(state => state.transactionIngestion.loading);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [accountId, setAccountId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const [backendError, setBackendError] = useState<string | null>(null);
  const [workflow, setWorkflow] = useState<ICsvIngestionWorkflowResponse | null>(null);
  const [loadingReview, setLoadingReview] = useState(false);
  const [reviewActionInProgress, setReviewActionInProgress] = useState<number | null>(null);
  const [confirmingImport, setConfirmingImport] = useState(false);
  const [editingRecordId, setEditingRecordId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<ICsvIngestionWorkflowRowEditDraft>({});
  const [fileReviewUnavailable, setFileReviewUnavailable] = useState(false);

  useEffect(() => {
    if (!isReviewPage) {
      dispatch(getFinancialAccounts({ sort: 'name,asc' }));
    }
  }, [isReviewPage]);

  useEffect(() => {
    if (!isReviewPage || !id) {
      return;
    }

    dispatch(getEntity(id));
  }, [isReviewPage, id]);

  useEffect(() => {
    if (!isReviewPage || !id || transactionIngestionEntity?.id?.toString() !== id) {
      return;
    }

    if (transactionIngestionEntity.ingestionType !== 'FILE') {
      setWorkflow(null);
      setFileReviewUnavailable(false);
      setLoadingReview(false);
      return;
    }

    setLoadingReview(true);
    setBackendError(null);
    setFileReviewUnavailable(false);
    axios
      .get<ICsvIngestionWorkflowResponse>(`api/transaction-ingestions/${id}/workflow`)
      .then(response => setWorkflow(response.data))
      .catch(() => {
        setWorkflow(null);
        setFileReviewUnavailable(true);
      })
      .finally(() => setLoadingReview(false));
  }, [isReviewPage, id, transactionIngestionEntity?.id, transactionIngestionEntity?.ingestionType]);

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
    setWorkflow(currentWorkflow => {
      if (!currentWorkflow) {
        return currentWorkflow;
      }
      return {
        ...currentWorkflow,
        status: data.status ?? currentWorkflow.status,
        counts: data.counts ?? currentWorkflow.counts,
        rows: (currentWorkflow.rows ?? []).map(row => {
          const sameRecord =
            row.ingestionRecordId === updatedRow.ingestionRecordId ||
            (updatedRow.ingestionRecordId === undefined && row.recordIndex === updatedRow.recordIndex);
          return sameRecord ? replaceReviewRow(row, updatedRow) : row;
        }),
      };
    });
  };

  const applyConfirmResponse = (data: ICsvIngestionConfirmImportResponse) => {
    setWorkflow(currentWorkflow => {
      if (!currentWorkflow) {
        return currentWorkflow;
      }
      return {
        ...currentWorkflow,
        status: data.status ?? currentWorkflow.status,
        counts: data.counts ?? currentWorkflow.counts,
        rows: data.rows ?? currentWorkflow.rows,
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
      setLocalError('fintrackApp.transactionIngestion.workflow.errors.accountRequired');
      return;
    }

    if (!file) {
      setLocalError('fintrackApp.transactionIngestion.workflow.errors.fileRequired');
      return;
    }

    const formData = new FormData();
    formData.append('accountId', accountId);
    formData.append('file', file);

    setSubmitting(true);
    try {
      const response = await axios.post<ICsvIngestionWorkflowResponse>(createWorkflowApiUrl, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const transactionIngestionId = response.data.transactionIngestionId;
      if (transactionIngestionId) {
        navigate(`/transaction-ingestion/${transactionIngestionId}`);
      }
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.createFailed'));
    } finally {
      clearFileInput();
      setSubmitting(false);
    }
  };

  const performRowAction = async (row: ICsvIngestionWorkflowRow, action: 'disable' | 'enable') => {
    if (!row.ingestionRecordId || !workflow?.transactionIngestionId) {
      return;
    }
    setBackendError(null);
    setReviewActionInProgress(row.ingestionRecordId);
    try {
      const response = await axios.post<ICsvIngestionRecordReviewResponse>(
        `api/transaction-ingestions/${workflow.transactionIngestionId}/records/${row.ingestionRecordId}/${action}`,
      );
      applyReviewResponse(response.data);
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.recordUpdateFailed'));
    } finally {
      setReviewActionInProgress(null);
    }
  };

  const startEditingRow = (row: ICsvIngestionWorkflowRow) => {
    if (!row.ingestionRecordId) {
      return;
    }
    setBackendError(null);
    setEditingRecordId(row.ingestionRecordId);
    setEditDraft(rowToEditDraft(row));
  };

  const updateEditDraft = (field: keyof ICsvIngestionWorkflowRowEditDraft, value: string) => {
    setEditDraft(currentDraft => ({ ...currentDraft, [field]: value }));
  };

  const cancelEditingRow = () => {
    setEditingRecordId(null);
    setEditDraft({});
  };

  const saveEditingRow = async (row: ICsvIngestionWorkflowRow) => {
    if (!row.ingestionRecordId || !workflow?.transactionIngestionId) {
      return;
    }
    setBackendError(null);
    setReviewActionInProgress(row.ingestionRecordId);
    try {
      const response = await axios.patch<ICsvIngestionRecordReviewResponse>(
        `api/transaction-ingestions/${workflow.transactionIngestionId}/records/${row.ingestionRecordId}`,
        editDraftPayload(editDraft),
      );
      applyReviewResponse(response.data);
      cancelEditingRow();
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.recordUpdateFailed'));
    } finally {
      setReviewActionInProgress(null);
    }
  };

  const counts = workflow?.counts ?? {};
  const rows = workflow?.rows ?? [];
  const warnings = workflow?.warnings ?? [];
  const fileMetadata = workflow?.fileMetadata;
  const reviewActionsEnabled = workflow?.status === 'READY' || workflow?.status === 'PARTIALLY_READY';
  const completed = workflow?.status === 'COMPLETED';
  const failed = workflow?.status === 'FAILED';
  const confirmAvailable = workflow?.status === 'READY' && (counts.validRows ?? 0) > 0;
  const processing = workflow?.status === 'PROCESSING' || confirmingImport;
  const showNoTransactionsCreatedBanner = reviewActionsEnabled;
  const showActionsColumn = reviewActionsEnabled;
  const showErrorColumn = !completed || rows.some(row => row.errorMessage || row.errorCode);

  const canDisable = (row: ICsvIngestionWorkflowRow) => reviewActionsEnabled && ['VALID', 'REJECTED'].includes(row.status ?? '');
  const canEnable = (row: ICsvIngestionWorkflowRow) => reviewActionsEnabled && row.status === 'DISABLED';
  const canEdit = (row: ICsvIngestionWorkflowRow) => reviewActionsEnabled && editableRowStatuses.includes(row.status ?? '');

  const confirmImport = async () => {
    if (!workflow?.transactionIngestionId || !confirmAvailable) {
      return;
    }
    setBackendError(null);
    setConfirmingImport(true);
    try {
      const response = await axios.post<ICsvIngestionConfirmImportResponse>(
        `api/transaction-ingestions/${workflow.transactionIngestionId}/confirm`,
      );
      applyConfirmResponse(response.data);
    } catch (error) {
      setBackendError(translate('fintrackApp.transactionIngestion.workflow.errors.confirmFailed'));
    } finally {
      setConfirmingImport(false);
    }
  };

  const renderCreateForm = () => (
    <Row className="justify-content-center">
      <Col md="10">
        <h2 data-cy="TransactionIngestionWorkflowDetailHeading">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.title">File import workflow</Translate>
        </h2>
        <p className="text-muted">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.help">
            Upload a canonical FINTRACK CSV file to create a persisted workflow.
          </Translate>
        </p>

        <Alert color="info" fade={false}>
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.noTransactionsCreated">
            No transactions were created yet.
          </Translate>
        </Alert>

        {localError ? (
          <Alert color="danger" data-cy="workflowLocalError" fade={false}>
            <Translate contentKey={localError} />
          </Alert>
        ) : null}
        {backendError ? (
          <Alert color="danger" data-cy="workflowBackendError" fade={false}>
            {backendError}
          </Alert>
        ) : null}

        <Form onSubmit={handleSubmit}>
          <FormGroup>
            <Label for="workflow-account">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.account">Account</Translate>
            </Label>
            <Input
              id="workflow-account"
              data-cy="workflowAccount"
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
            <Label for="workflow-file">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.csvFile">CSV file</Translate>
            </Label>
            <Input
              id="workflow-file"
              data-cy="workflowFile"
              type="file"
              innerRef={fileInputRef}
              accept=".csv,text/csv"
              disabled={submitting}
              onChange={event => setFile(event.target.files?.[0] ?? null)}
            />
            <small className="form-text text-muted">
              <Translate contentKey="fintrackApp.transactionIngestion.workflow.csvHint">
                Expected header: transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
              </Translate>
            </small>
          </FormGroup>
          <Button color="primary" type="submit" disabled={submitting} data-cy="workflowSubmit">
            {submitting ? <Spinner size="sm" /> : <FontAwesomeIcon icon="eye" />}{' '}
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.upload">Upload</Translate>
          </Button>
          &nbsp;
          <Button tag={Link} to="/transaction-ingestion" replace color="info">
            <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
          </Button>
        </Form>
      </Col>
    </Row>
  );

  const renderDateTime = (value?: any) => (value ? <TextFormat value={value} type="date" format={APP_DATE_FORMAT} /> : null);

  const renderDetailActions = () =>
    transactionIngestionEntity?.id ? (
      <div className="mt-3">
        <Button tag={Link} to="/transaction-ingestion" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
      </div>
    ) : null;

  const renderParentSummary = () => (
    <>
      <h3>
        <Translate contentKey="fintrackApp.transactionIngestion.workflow.parentSummary">Parent summary</Translate>
      </h3>
      <dl className="jh-entity-details" data-cy="transactionIngestionParentSummary">
        <dt>
          <Translate contentKey="global.field.id">ID</Translate>
        </dt>
        <dd>{transactionIngestionEntity.id}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.account">Account</Translate>
        </dt>
        <dd>
          {transactionIngestionEntity.account ? (
            <Link to={`/financial-account/${transactionIngestionEntity.account.id}`}>{transactionIngestionEntity.account.name}</Link>
          ) : (
            ''
          )}
        </dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.ingestionType">Ingestion Type</Translate>
        </dt>
        <dd>{transactionIngestionEntity.ingestionType}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.status">Status</Translate>
        </dt>
        <dd>{renderIngestionStatus(workflow?.status ?? transactionIngestionEntity.status)}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.sourceLabel">Source Label</Translate>
        </dt>
        <dd>{workflow?.sourceLabel ?? transactionIngestionEntity.sourceLabel}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.startedAt">Started At</Translate>
        </dt>
        <dd>{renderDateTime(transactionIngestionEntity.startedAt)}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.completedAt">Completed At</Translate>
        </dt>
        <dd>{renderDateTime(transactionIngestionEntity.completedAt)}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.recordsReceived">Records Received</Translate>
        </dt>
        <dd>{workflow?.counts?.recordsReceived ?? transactionIngestionEntity.recordsReceived}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.recordsCreated">Records Created</Translate>
        </dt>
        <dd>{workflow?.counts?.recordsCreated ?? transactionIngestionEntity.recordsCreated}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.recordsSkipped">Records Skipped</Translate>
        </dt>
        <dd>{workflow?.counts?.recordsSkipped ?? transactionIngestionEntity.recordsSkipped}</dd>
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.recordsRejected">Records Rejected</Translate>
        </dt>
        <dd>{workflow?.counts?.recordsRejected ?? transactionIngestionEntity.recordsRejected}</dd>
        {transactionIngestionEntity.errorMessage ? (
          <>
            <dt>
              <Translate contentKey="fintrackApp.transactionIngestion.errorMessage">Error Message</Translate>
            </dt>
            <dd>{transactionIngestionEntity.errorMessage}</dd>
          </>
        ) : null}
        <dt>
          <Translate contentKey="fintrackApp.transactionIngestion.createdAt">Created At</Translate>
        </dt>
        <dd>{renderDateTime(transactionIngestionEntity.createdAt)}</dd>
      </dl>
    </>
  );

  const renderFileMetadata = () =>
    fileMetadata ? (
      <>
        <h3>
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.fileMetadata">File metadata</Translate>
        </h3>
        <dl className="jh-entity-details" data-cy="workflowMetadata">
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.originalFilename">Original filename</Translate>
          </dt>
          <dd>{fileMetadata.originalFilename}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.fileType">File type</Translate>
          </dt>
          <dd>{fileMetadata.fileType}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.contentType">Content type</Translate>
          </dt>
          <dd>{fileMetadata.contentType}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.fileSizeBytes">File size bytes</Translate>
          </dt>
          <dd>{fileMetadata.fileSizeBytes}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.checksum">Checksum</Translate>
          </dt>
          <dd>{fileMetadata.checksum}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.storageKey">Storage key</Translate>
          </dt>
          <dd>{fileMetadata.storageKey}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.parserName">Parser name</Translate>
          </dt>
          <dd>{fileMetadata.parserName}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.parserVersion">Parser version</Translate>
          </dt>
          <dd>{fileMetadata.parserVersion}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.statementStartDate">Statement start date</Translate>
          </dt>
          <dd>{fileMetadata.statementStartDate}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.statementEndDate">Statement end date</Translate>
          </dt>
          <dd>{fileMetadata.statementEndDate}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionIngestion.createdAt">Created At</Translate>
          </dt>
          <dd>{renderDateTime(fileMetadata.createdAt)}</dd>
        </dl>
      </>
    ) : null;

  const renderReview = () => (
    <Row className="justify-content-center mt-4">
      <Col md="12">
        <h2 data-cy="workflowReviewHeading">
          <Translate contentKey="fintrackApp.transactionIngestion.workflow.workflowTitle">Transaction ingestion workflow</Translate>
        </h2>

        {showNoTransactionsCreatedBanner ? (
          <Alert color="info" fade={false}>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.noTransactionsCreated">
              No transactions were created yet.
            </Translate>
          </Alert>
        ) : null}

        {backendError ? (
          <Alert color="danger" data-cy="workflowBackendError" fade={false}>
            {backendError}
          </Alert>
        ) : null}

        {transactionIngestionLoading || loadingReview ? (
          <div>
            <Spinner size="sm" /> <Translate contentKey="entity.action.loading">Loading...</Translate>
          </div>
        ) : null}

        {transactionIngestionEntity?.id ? renderParentSummary() : null}

        {transactionIngestionEntity?.ingestionType === 'API' ? (
          <Alert color="info" data-cy="apiIngestionTbd" fade={false}>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.apiTbd">
              API ingestion detail is not implemented yet.
            </Translate>
          </Alert>
        ) : null}

        {fileReviewUnavailable && transactionIngestionEntity?.ingestionType === 'FILE' ? (
          <Alert color="info" data-cy="workflowUnavailable" fade={false}>
            <Translate contentKey="fintrackApp.transactionIngestion.workflow.unavailable">
              No file workflow metadata or review rows are available yet.
            </Translate>
          </Alert>
        ) : null}

        {workflow ? (
          <>
            {workflow.status === 'PARTIALLY_READY' ? (
              <Alert color="warning" data-cy="workflowConfirmBlocked" fade={false}>
                {(counts.validRows ?? 0) === 0 ? (
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.noValidRows">
                    There are no valid rows to import.
                  </Translate>
                ) : (
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.fixRejectedRows">
                    Fix or disable rejected rows before importing.
                  </Translate>
                )}
              </Alert>
            ) : null}

            {workflow.status === 'COMPLETED' ? (
              <Alert color="success" data-cy="workflowCompleted" fade={false}>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.importCompleted">Import completed.</Translate>
              </Alert>
            ) : null}

            {workflow.status === 'PROCESSING' ? (
              <Alert color="info" data-cy="workflowProcessing" fade={false}>
                <Spinner size="sm" />{' '}
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.importProcessing">Import is processing.</Translate>
              </Alert>
            ) : null}

            {failed ? (
              <Alert color="danger" data-cy="workflowFailed" fade={false}>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.importFailed">Import failed.</Translate>
              </Alert>
            ) : null}

            {warnings.length > 0 ? (
              <Alert color="warning" data-cy="workflowWarnings" fade={false}>
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.warnings">Warnings</Translate>
                </strong>
                <ul className="mb-0">
                  {warnings.map((warning, index) => (
                    <li key={`${warning.code}-${index}`}>{renderWarningMessage(warning)}</li>
                  ))}
                </ul>
              </Alert>
            ) : null}

            {renderFileMetadata()}

            <Row className="mb-3" data-cy="workflowCounts" data-testid="workflowCounts">
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsReceived">Records received</Translate>
                </strong>
                <div>{counts.recordsReceived ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.validRows">Valid rows</Translate>
                </strong>
                <div>{counts.validRows ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.invalidRows">Invalid rows</Translate>
                </strong>
                <div>{counts.invalidRows ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.disabledRows">Disabled rows</Translate>
                </strong>
                <div>{counts.recordsSkipped ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsRejected">Records rejected</Translate>
                </strong>
                <div>{counts.recordsRejected ?? 0}</div>
              </Col>
            </Row>

            {confirmAvailable ? (
              <div className="mb-3">
                <Button color="success" disabled={confirmingImport} onClick={confirmImport} data-cy="workflowConfirmImport">
                  {confirmingImport ? <Spinner size="sm" /> : null}{' '}
                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.confirmImport">Confirm Import</Translate>
                </Button>
              </div>
            ) : null}

            <div className="table-responsive">
              <h3>
                <Translate contentKey="fintrackApp.transactionIngestion.workflow.recordsTable">Ingestion records</Translate>
              </h3>
              <Table responsive data-cy="workflowRows">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.status">Status</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.transactionDate">Transaction date</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.postingDate">Posting date</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.description">Description</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.signedAmount">Signed amount</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.amount">Amount</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.flow">Flow</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.currency">Currency</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.externalReference">External reference</Translate>
                    </th>
                    <th>
                      <Translate contentKey="fintrackApp.transactionIngestion.workflow.notes">Notes</Translate>
                    </th>
                    {showErrorColumn ? (
                      <th>
                        <Translate contentKey="fintrackApp.transactionIngestion.workflow.error">Error</Translate>
                      </th>
                    ) : null}
                    {showActionsColumn ? (
                      <th>
                        <Translate contentKey="fintrackApp.transactionIngestion.workflow.actions">Actions</Translate>
                      </th>
                    ) : null}
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
                          <span data-testid={`workflowRowStatus-${row.ingestionRecordId ?? row.recordIndex}`}>
                            {recordStatusLabel(row.status)}
                          </span>
                        </td>
                        <td>
                          {isEditing ? (
                            <Input
                              bsSize="sm"
                              aria-label={translate('fintrackApp.transactionIngestion.workflow.transactionDate')}
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
                              aria-label={translate('fintrackApp.transactionIngestion.workflow.postingDate')}
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
                              aria-label={translate('fintrackApp.transactionIngestion.workflow.description')}
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
                              aria-label={translate('fintrackApp.transactionIngestion.workflow.signedAmount')}
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
                              aria-label={translate('fintrackApp.transactionIngestion.workflow.currency')}
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
                              aria-label={translate('fintrackApp.transactionIngestion.workflow.externalReference')}
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
                              aria-label={translate('fintrackApp.transactionIngestion.workflow.notes')}
                              value={editDraft.notes ?? ''}
                              onChange={event => updateEditDraft('notes', event.target.value)}
                            />
                          ) : (
                            row.notes
                          )}
                        </td>
                        {showErrorColumn ? <td>{row.errorMessage || row.errorCode}</td> : null}
                        {showActionsColumn ? (
                          <td className="text-end">
                            {isEditing ? (
                              <>
                                <Button size="sm" color="primary" disabled={actionBusy} onClick={() => saveEditingRow(row)}>
                                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.saveRow">Save row</Translate>
                                </Button>{' '}
                                <Button size="sm" color="secondary" disabled={actionBusy} onClick={cancelEditingRow}>
                                  <Translate contentKey="fintrackApp.transactionIngestion.workflow.cancel">Cancel</Translate>
                                </Button>
                              </>
                            ) : null}
                            {!isEditing && canEdit(row) ? (
                              <Button size="sm" color="primary" disabled={actionBusy} onClick={() => startEditingRow(row)}>
                                <Translate contentKey="fintrackApp.transactionIngestion.workflow.edit">Edit</Translate>
                              </Button>
                            ) : null}{' '}
                            {!isEditing && canDisable(row) ? (
                              <Button size="sm" color="warning" disabled={actionBusy} onClick={() => performRowAction(row, 'disable')}>
                                <Translate contentKey="fintrackApp.transactionIngestion.workflow.disable">Disable</Translate>
                              </Button>
                            ) : null}
                            {!isEditing && canEnable(row) ? (
                              <Button size="sm" color="success" disabled={actionBusy} onClick={() => performRowAction(row, 'enable')}>
                                <Translate contentKey="fintrackApp.transactionIngestion.workflow.enable">Enable</Translate>
                              </Button>
                            ) : null}
                          </td>
                        ) : null}
                      </tr>
                    );
                  })}
                </tbody>
              </Table>
            </div>
          </>
        ) : null}
        {renderDetailActions()}
      </Col>
    </Row>
  );

  return <div>{isReviewPage ? renderReview() : renderCreateForm()}</div>;
};

export default TransactionIngestionWorkflowDetail;
