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
  counts?: ICsvIngestionPreviewCounts;
  row?: ICsvIngestionPreviewRow;
}

const createPreviewApiUrl = 'api/transaction-ingestions/file-preview';

const renderFlow = (flow?: string) => {
  if (!flow) {
    return '';
  }

  return <Translate contentKey={`fintrackApp.TransactionFlow.${flow}`}>{flow}</Translate>;
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

  return <Translate contentKey={`fintrackApp.transactionIngestion.filePreview.ingestionStatus.${status}`}>{status}</Translate>;
};

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
    setPreview(currentPreview => {
      if (!currentPreview) {
        return currentPreview;
      }
      return {
        ...currentPreview,
        counts: data.counts ?? currentPreview.counts,
        rows: (currentPreview.rows ?? []).map(row =>
          row.ingestionRecordId === data.row?.ingestionRecordId ? { ...row, ...data.row } : row,
        ),
      };
    });
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

  const canDisable = (row: ICsvIngestionPreviewRow) => ['VALID', 'REJECTED'].includes(row.status ?? '');
  const canEnable = (row: ICsvIngestionPreviewRow) => row.status === 'DISABLED';

  const counts = preview?.counts ?? {};
  const rows = preview?.rows ?? [];
  const warnings = preview?.warnings ?? [];
  const fileMetadata = preview?.fileMetadata;

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
                    const actionBusy = reviewActionInProgress === row.ingestionRecordId;
                    return (
                      <tr key={row.ingestionRecordId ?? row.recordIndex} className={row.status === 'DISABLED' ? 'text-muted' : ''}>
                        <td>{row.recordIndex}</td>
                        <td>
                          {row.status ? (
                            <Translate contentKey={`fintrackApp.IngestionRecordStatus.${row.status}`}>{row.status}</Translate>
                          ) : (
                            ''
                          )}
                        </td>
                        <td>{row.transactionDate}</td>
                        <td>{row.postingDate}</td>
                        <td>{row.description}</td>
                        <td>{row.signedAmount}</td>
                        <td>{row.amount}</td>
                        <td>{renderFlow(row.flow)}</td>
                        <td>{row.currency}</td>
                        <td>{row.externalReference}</td>
                        <td>{row.notes}</td>
                        <td>{row.errorMessage || row.errorCode}</td>
                        <td className="text-end">
                          {canDisable(row) ? (
                            <Button size="sm" color="warning" disabled={actionBusy} onClick={() => performRowAction(row, 'disable')}>
                              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.disable">Disable</Translate>
                            </Button>
                          ) : null}
                          {canEnable(row) ? (
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
