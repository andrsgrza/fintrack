import React, { FormEvent, useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link } from 'react-router-dom';
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

interface ICsvIngestionPreviewResponse {
  transactionIngestionId?: number;
  fileIngestionId?: number;
  status?: string;
  sourceLabel?: string;
  counts?: ICsvIngestionPreviewCounts;
  warnings?: ICsvIngestionPreviewMessage[];
  rows?: ICsvIngestionPreviewRow[];
}

const apiUrl = 'api/transaction-ingestions/file-preview';

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

export const TransactionIngestionFilePreview = () => {
  const dispatch = useAppDispatch();
  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const financialAccountsLoading = useAppSelector(state => state.financialAccount.loading);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [accountId, setAccountId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);
  const [backendError, setBackendError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ICsvIngestionPreviewResponse | null>(null);

  useEffect(() => {
    dispatch(getFinancialAccounts({ sort: 'name,asc' }));
  }, []);

  const clearFileInput = () => {
    setFile(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
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
      const response = await axios.post<ICsvIngestionPreviewResponse>(apiUrl, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      setPreview(response.data);
    } catch (error) {
      setPreview(null);
      setBackendError(translate('fintrackApp.transactionIngestion.filePreview.errors.previewFailed'));
    } finally {
      clearFileInput();
      setSubmitting(false);
    }
  };

  const counts = preview?.counts ?? {};
  const rows = preview?.rows ?? [];
  const warnings = preview?.warnings ?? [];

  return (
    <div>
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

      {preview ? (
        <Row className="justify-content-center mt-4">
          <Col md="12">
            <h3 data-cy="filePreviewCreated">
              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.previewCreated">Preview created</Translate>
            </h3>

            <Alert color="success" fade={false}>
              <Translate contentKey="fintrackApp.transactionIngestion.filePreview.previewOnly">
                Preview only — no transactions were created.
              </Translate>
            </Alert>

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
              <dd>
                {preview.status ? <Translate contentKey={`fintrackApp.IngestionStatus.${preview.status}`}>{preview.status}</Translate> : ''}
              </dd>
              <dt>
                <Translate contentKey="fintrackApp.transactionIngestion.sourceLabel">Source Label</Translate>
              </dt>
              <dd>{preview.sourceLabel}</dd>
            </dl>

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
                  <Translate contentKey="fintrackApp.transactionIngestion.filePreview.recordsRejected">Records rejected</Translate>
                </strong>
                <div>{counts.recordsRejected ?? 0}</div>
              </Col>
              <Col md="2">
                <strong>
                  <Translate contentKey="fintrackApp.transactionIngestion.recordsCreated">Records Created</Translate>
                </strong>
                <div>{counts.recordsCreated ?? 0}</div>
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
                      <Translate contentKey="fintrackApp.transactionIngestion.filePreview.error">Error</Translate>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(row => (
                    <tr key={row.ingestionRecordId ?? row.recordIndex}>
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
                      <td>{row.errorMessage || row.errorCode}</td>
                    </tr>
                  ))}
                </tbody>
              </Table>
            </div>
          </Col>
        </Row>
      ) : null}
    </div>
  );
};

export default TransactionIngestionFilePreview;
