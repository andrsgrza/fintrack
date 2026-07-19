import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, FormText, Input, Label, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { IngestionType } from 'app/shared/model/enumerations/ingestion-type.model';
import { IngestionStatus } from 'app/shared/model/enumerations/ingestion-status.model';
import { getEntity, reset, updateEntity } from './transaction-ingestion.reducer';

export const TransactionIngestionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { id } = useParams<'id'>();
  const isNew = id === undefined;
  const [selectedAccountId, setSelectedAccountId] = useState('');
  const [selectedIngestionType, setSelectedIngestionType] = useState('FILE');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const transactionIngestionEntity = useAppSelector(state => state.transactionIngestion.entity);
  const loading = useAppSelector(state => state.transactionIngestion.loading);
  const updating = useAppSelector(state => state.transactionIngestion.updating);
  const updateSuccess = useAppSelector(state => state.transactionIngestion.updateSuccess);
  const ingestionTypeValues = Object.keys(IngestionType);
  const ingestionStatusValues = Object.keys(IngestionStatus);

  const handleClose = () => {
    navigate(`/transaction-ingestion${location.search}`);
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getFinancialAccounts({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (isNew) {
      createFileTransactionIngestion();
      return;
    }

    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }

    const entity = {
      ...transactionIngestionEntity,
      ...values,
      account: transactionIngestionEntity?.account,
      ingestionType: transactionIngestionEntity?.ingestionType,
      startedAt: transactionIngestionEntity?.startedAt,
      createdAt: transactionIngestionEntity?.createdAt,
    };

    entity.completedAt = convertDateTimeToServer(values.completedAt);
    if (values.recordsReceived !== undefined && typeof values.recordsReceived !== 'number') {
      entity.recordsReceived = Number(values.recordsReceived);
    }
    if (values.recordsCreated !== undefined && typeof values.recordsCreated !== 'number') {
      entity.recordsCreated = Number(values.recordsCreated);
    }
    if (values.recordsSkipped !== undefined && typeof values.recordsSkipped !== 'number') {
      entity.recordsSkipped = Number(values.recordsSkipped);
    }
    if (values.recordsRejected !== undefined && typeof values.recordsRejected !== 'number') {
      entity.recordsRejected = Number(values.recordsRejected);
    }

    dispatch(updateEntity(entity));
  };

  const createFileTransactionIngestion = async () => {
    setCreateError(null);

    if (selectedIngestionType !== 'FILE') {
      setCreateError(translate('fintrackApp.transactionIngestion.create.apiTbd'));
      return;
    }
    if (!selectedAccountId) {
      setCreateError(translate('fintrackApp.transactionIngestion.filePreview.errors.accountRequired'));
      return;
    }
    if (!selectedFile) {
      setCreateError(translate('fintrackApp.transactionIngestion.filePreview.errors.fileRequired'));
      return;
    }

    const formData = new FormData();
    formData.append('accountId', selectedAccountId);
    formData.append('file', selectedFile);

    setUploading(true);
    try {
      const response = await axios.post('api/transaction-ingestions/file', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      navigate(`/transaction-ingestion/${response.data.transactionIngestionId}/file-preview`);
    } catch (error) {
      setCreateError(createErrorMessage(error));
    } finally {
      setUploading(false);
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const createErrorMessage = error =>
    error?.response?.data?.detail ??
    error?.response?.data?.message ??
    error?.message ??
    translate('fintrackApp.transactionIngestion.create.createFailed');

  const onFileChange = event => {
    setSelectedFile(event.target.files?.[0] ?? null);
    setCreateError(null);
  };

  const defaultValues = () =>
    isNew
      ? {
          ingestionType: 'FILE',
        }
      : {
          ...transactionIngestionEntity,
          startedAt: convertDateTimeFromServer(transactionIngestionEntity.startedAt),
          completedAt: convertDateTimeFromServer(transactionIngestionEntity.completedAt),
          createdAt: convertDateTimeFromServer(transactionIngestionEntity.createdAt),
          account: transactionIngestionEntity?.account?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.transactionIngestion.home.createOrEditLabel" data-cy="TransactionIngestionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.transactionIngestion.home.createOrEditLabel">
              Create or edit a TransactionIngestion
            </Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {createError ? (
                <Alert color="danger" fade={false}>
                  {createError}
                </Alert>
              ) : null}
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="transaction-ingestion-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              {isNew ? (
                <>
                  <div className="mb-3">
                    <Label for="transaction-ingestion-account">
                      <Translate contentKey="fintrackApp.transactionIngestion.account">Account</Translate>
                    </Label>
                    <Input
                      id="transaction-ingestion-account"
                      data-cy="account"
                      name="account"
                      type="select"
                      value={selectedAccountId}
                      onChange={event => {
                        setSelectedAccountId(event.target.value);
                        setCreateError(null);
                      }}
                    >
                      <option value="" key="0" />
                      {financialAccounts
                        ? financialAccounts.map(otherEntity => (
                            <option value={otherEntity.id} key={otherEntity.id}>
                              {otherEntity.name}
                            </option>
                          ))
                        : null}
                    </Input>
                  </div>
                  <div className="mb-3">
                    <Label for="transaction-ingestion-ingestionType">
                      <Translate contentKey="fintrackApp.transactionIngestion.ingestionType">Ingestion Type</Translate>
                    </Label>
                    <Input
                      id="transaction-ingestion-ingestionType"
                      data-cy="ingestionType"
                      name="ingestionType"
                      type="select"
                      value={selectedIngestionType}
                      onChange={event => {
                        setSelectedIngestionType(event.target.value);
                        setCreateError(null);
                      }}
                    >
                      {ingestionTypeValues.map(ingestionType => (
                        <option value={ingestionType} key={ingestionType}>
                          {translate(`fintrackApp.IngestionType.${ingestionType}`)}
                        </option>
                      ))}
                    </Input>
                  </div>
                  {selectedIngestionType === 'FILE' ? (
                    <div className="mb-3">
                      <Label for="transaction-ingestion-csv-file">
                        <Translate contentKey="fintrackApp.transactionIngestion.filePreview.csvFile">CSV file</Translate>
                      </Label>
                      <Input
                        id="transaction-ingestion-csv-file"
                        data-cy="csvFile"
                        name="csvFile"
                        type="file"
                        accept=".csv,text/csv"
                        innerRef={fileInputRef}
                        onChange={onFileChange}
                      />
                      <FormText>
                        <Translate contentKey="fintrackApp.transactionIngestion.filePreview.csvHint">
                          Expected header: transactionDate,postingDate,description,signedAmount,currency,externalReference,notes
                        </Translate>
                      </FormText>
                    </div>
                  ) : (
                    <Alert color="info" fade={false} data-cy="apiIngestionTbd">
                      <Translate contentKey="fintrackApp.transactionIngestion.create.apiTbd">API ingestion is coming soon.</Translate>
                    </Alert>
                  )}
                </>
              ) : (
                <>
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.ingestionType')}
                    id="transaction-ingestion-ingestionType"
                    name="ingestionType"
                    data-cy="ingestionType"
                    type="select"
                    disabled
                  >
                    {ingestionTypeValues.map(ingestionType => (
                      <option value={ingestionType} key={ingestionType}>
                        {translate(`fintrackApp.IngestionType.${ingestionType}`)}
                      </option>
                    ))}
                  </ValidatedField>
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.status')}
                    id="transaction-ingestion-status"
                    name="status"
                    data-cy="status"
                    type="select"
                  >
                    {ingestionStatusValues.map(ingestionStatus => (
                      <option value={ingestionStatus} key={ingestionStatus}>
                        {translate(`fintrackApp.IngestionStatus.${ingestionStatus}`)}
                      </option>
                    ))}
                  </ValidatedField>
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.startedAt')}
                    id="transaction-ingestion-startedAt"
                    name="startedAt"
                    data-cy="startedAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.completedAt')}
                    id="transaction-ingestion-completedAt"
                    name="completedAt"
                    data-cy="completedAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.recordsReceived')}
                    id="transaction-ingestion-recordsReceived"
                    name="recordsReceived"
                    data-cy="recordsReceived"
                    type="text"
                    validate={{
                      min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                      validate: v => isNumber(v) || translate('entity.validation.number'),
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.recordsCreated')}
                    id="transaction-ingestion-recordsCreated"
                    name="recordsCreated"
                    data-cy="recordsCreated"
                    type="text"
                    validate={{
                      min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                      validate: v => isNumber(v) || translate('entity.validation.number'),
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.recordsSkipped')}
                    id="transaction-ingestion-recordsSkipped"
                    name="recordsSkipped"
                    data-cy="recordsSkipped"
                    type="text"
                    validate={{
                      min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                      validate: v => isNumber(v) || translate('entity.validation.number'),
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.recordsRejected')}
                    id="transaction-ingestion-recordsRejected"
                    name="recordsRejected"
                    data-cy="recordsRejected"
                    type="text"
                    validate={{
                      min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                      validate: v => isNumber(v) || translate('entity.validation.number'),
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.errorMessage')}
                    id="transaction-ingestion-errorMessage"
                    name="errorMessage"
                    data-cy="errorMessage"
                    type="text"
                    validate={{
                      maxLength: { value: 2000, message: translate('entity.validation.maxlength', { max: 2000 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.createdAt')}
                    id="transaction-ingestion-createdAt"
                    name="createdAt"
                    data-cy="createdAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.sourceLabel')}
                    id="transaction-ingestion-sourceLabel"
                    name="sourceLabel"
                    data-cy="sourceLabel"
                    type="text"
                    validate={{
                      maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.transactionIngestion.account')}
                    id="transaction-ingestion-account"
                    data-cy="account"
                    type="select"
                    name="account"
                    disabled
                    validate={{
                      required: { value: true, message: translate('entity.validation.required') },
                    }}
                  >
                    <option value="" key="0" />
                    {financialAccounts
                      ? financialAccounts.map(otherEntity => (
                          <option value={otherEntity.id} key={otherEntity.id}>
                            {otherEntity.name}
                          </option>
                        ))
                      : null}
                  </ValidatedField>
                </>
              )}
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/transaction-ingestion" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button
                color="primary"
                id="save-entity"
                data-cy="entityCreateSaveButton"
                type="submit"
                disabled={updating || uploading || (isNew && selectedIngestionType !== 'FILE')}
              >
                <FontAwesomeIcon icon="save" />
                &nbsp;
                {isNew ? (
                  <Translate contentKey="fintrackApp.transactionIngestion.create.submitFile">Create preview</Translate>
                ) : (
                  <Translate contentKey="entity.action.save">Save</Translate>
                )}
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default TransactionIngestionUpdate;
