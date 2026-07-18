import React, { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, FormText, Input, Label, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntitiesWhereFileIngestionIsNull } from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import { ImportFileType } from 'app/shared/model/enumerations/import-file-type.model';
import { getEntity, reset, updateEntity } from './file-ingestion.reducer';

export const FileIngestionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { id } = useParams<'id'>();
  const isNew = id === undefined;
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [selectedTransactionIngestionId, setSelectedTransactionIngestionId] = useState('');
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const fileIngestionParentCandidates = useAppSelector(state => state.transactionIngestion.fileIngestionParentCandidates ?? []);
  const fileIngestionEntity = useAppSelector(state => state.fileIngestion.entity);
  const loading = useAppSelector(state => state.fileIngestion.loading);
  const updating = useAppSelector(state => state.fileIngestion.updating);
  const updateSuccess = useAppSelector(state => state.fileIngestion.updateSuccess);
  const importFileTypeValues = Object.keys(ImportFileType);

  const handleClose = () => {
    navigate('/file-ingestion');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
      dispatch(getEntitiesWhereFileIngestionIsNull());
    } else {
      dispatch(getEntity(id));
    }
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (isNew) {
      uploadCsvFile(values);
      return;
    }

    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    if (values.fileSizeBytes !== undefined && typeof values.fileSizeBytes !== 'number') {
      values.fileSizeBytes = Number(values.fileSizeBytes);
    }

    const entity = {
      ...fileIngestionEntity,
      ...values,
      transactionIngestion: fileIngestionEntity?.transactionIngestion,
      createdAt: fileIngestionEntity?.createdAt,
    };

    dispatch(updateEntity(entity));
  };

  const uploadCsvFile = async values => {
    setUploadError(null);

    const transactionIngestionId = selectedTransactionIngestionId || values.transactionIngestion;
    if (!transactionIngestionId) {
      setUploadError(translate('entity.validation.required'));
      return;
    }
    if (!selectedFile) {
      setUploadError(translate('fintrackApp.fileIngestion.upload.fileRequired'));
      return;
    }

    const formData = new FormData();
    formData.append('file', selectedFile);

    setUploading(true);
    try {
      const response = await axios.post(`api/transaction-ingestions/${transactionIngestionId}/file-ingestion`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      const persistedTransactionIngestionId = response.data?.transactionIngestionId ?? transactionIngestionId;
      navigate(`/transaction-ingestion/${persistedTransactionIngestionId}/file-preview`);
    } catch (error) {
      setUploadError(uploadErrorMessage(error));
    } finally {
      setUploading(false);
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  };

  const uploadErrorMessage = error =>
    error?.response?.data?.detail ??
    error?.response?.data?.message ??
    error?.message ??
    translate('fintrackApp.fileIngestion.upload.error');

  const onFileChange = event => {
    setSelectedFile(event.target.files?.[0] ?? null);
    setUploadError(null);
  };

  const defaultValues = () =>
    isNew
      ? {}
      : {
          fileType: 'CSV',
          ...fileIngestionEntity,
          createdAt: convertDateTimeFromServer(fileIngestionEntity.createdAt),
          transactionIngestion: fileIngestionEntity?.transactionIngestion?.id,
        };

  const parentOptions = isNew
    ? fileIngestionParentCandidates.filter(candidate => candidate.ingestionType === 'FILE' && candidate.status === 'PENDING')
    : fileIngestionEntity?.transactionIngestion
      ? [fileIngestionEntity.transactionIngestion]
      : [];

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.fileIngestion.home.createOrEditLabel" data-cy="FileIngestionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.fileIngestion.home.createOrEditLabel">Create or edit a FileIngestion</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {uploadError ? (
                <Alert color="danger" fade={false}>
                  {uploadError}
                </Alert>
              ) : null}
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="file-ingestion-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              {!isNew ? (
                <>
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.originalFilename')}
                    id="file-ingestion-originalFilename"
                    name="originalFilename"
                    data-cy="originalFilename"
                    type="text"
                    validate={{
                      required: { value: true, message: translate('entity.validation.required') },
                      minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                      maxLength: { value: 255, message: translate('entity.validation.maxlength', { max: 255 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.fileType')}
                    id="file-ingestion-fileType"
                    name="fileType"
                    data-cy="fileType"
                    type="select"
                  >
                    {importFileTypeValues.map(importFileType => (
                      <option value={importFileType} key={importFileType}>
                        {translate(`fintrackApp.ImportFileType.${importFileType}`)}
                      </option>
                    ))}
                  </ValidatedField>
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.contentType')}
                    id="file-ingestion-contentType"
                    name="contentType"
                    data-cy="contentType"
                    type="text"
                    validate={{
                      maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.fileSizeBytes')}
                    id="file-ingestion-fileSizeBytes"
                    name="fileSizeBytes"
                    data-cy="fileSizeBytes"
                    type="text"
                    validate={{
                      min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                      validate: v => isNumber(v) || translate('entity.validation.number'),
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.checksum')}
                    id="file-ingestion-checksum"
                    name="checksum"
                    data-cy="checksum"
                    type="text"
                    validate={{
                      maxLength: { value: 128, message: translate('entity.validation.maxlength', { max: 128 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.storageKey')}
                    id="file-ingestion-storageKey"
                    name="storageKey"
                    data-cy="storageKey"
                    type="text"
                    validate={{
                      maxLength: { value: 500, message: translate('entity.validation.maxlength', { max: 500 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.parserName')}
                    id="file-ingestion-parserName"
                    name="parserName"
                    data-cy="parserName"
                    type="text"
                    validate={{
                      maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.parserVersion')}
                    id="file-ingestion-parserVersion"
                    name="parserVersion"
                    data-cy="parserVersion"
                    type="text"
                    validate={{
                      maxLength: { value: 50, message: translate('entity.validation.maxlength', { max: 50 }) },
                    }}
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.statementStartDate')}
                    id="file-ingestion-statementStartDate"
                    name="statementStartDate"
                    data-cy="statementStartDate"
                    type="date"
                  />
                  <ValidatedField
                    label={translate('fintrackApp.fileIngestion.statementEndDate')}
                    id="file-ingestion-statementEndDate"
                    name="statementEndDate"
                    data-cy="statementEndDate"
                    type="date"
                  />
                </>
              ) : null}
              {!isNew ? (
                <ValidatedField
                  label={translate('fintrackApp.fileIngestion.createdAt')}
                  id="file-ingestion-createdAt"
                  name="createdAt"
                  data-cy="createdAt"
                  type="datetime-local"
                  placeholder="YYYY-MM-DD HH:mm"
                  readOnly
                />
              ) : null}
              {isNew ? (
                <div className="mb-3">
                  <Label for="file-ingestion-transactionIngestion">
                    <Translate contentKey="fintrackApp.fileIngestion.transactionIngestion">Transaction Ingestion</Translate>
                  </Label>
                  <Input
                    id="file-ingestion-transactionIngestion"
                    name="transactionIngestion"
                    data-cy="transactionIngestion"
                    type="select"
                    value={selectedTransactionIngestionId}
                    onChange={event => {
                      setSelectedTransactionIngestionId(event.target.value);
                      setUploadError(null);
                    }}
                  >
                    <option value="" key="0" />
                    {parentOptions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))}
                  </Input>
                </div>
              ) : (
                <ValidatedField
                  id="file-ingestion-transactionIngestion"
                  name="transactionIngestion"
                  data-cy="transactionIngestion"
                  label={translate('fintrackApp.fileIngestion.transactionIngestion')}
                  type="select"
                  disabled
                  validate={{ required: { value: true, message: translate('entity.validation.required') } }}
                >
                  <option value="" key="0" />
                  {parentOptions.map(otherEntity => (
                    <option value={otherEntity.id} key={otherEntity.id}>
                      {otherEntity.id}
                    </option>
                  ))}
                </ValidatedField>
              )}
              <FormText>
                {isNew ? (
                  <Translate contentKey="fintrackApp.fileIngestion.upload.parentHelp">
                    Select a pending FILE Transaction Ingestion. Metadata will be derived from the uploaded CSV.
                  </Translate>
                ) : (
                  <Translate contentKey="entity.validation.required">This field is required.</Translate>
                )}
              </FormText>
              {isNew ? (
                <>
                  <div className="mb-3">
                    <Label for="file-ingestion-csv-file">
                      <Translate contentKey="fintrackApp.fileIngestion.upload.csvFile">CSV file</Translate>
                    </Label>
                    <Input
                      id="file-ingestion-csv-file"
                      data-cy="csvFile"
                      name="csvFile"
                      type="file"
                      accept=".csv,text/csv"
                      innerRef={fileInputRef}
                      onChange={onFileChange}
                    />
                    <FormText>
                      <Translate contentKey="fintrackApp.fileIngestion.upload.fileHelp">
                        Upload a canonical CSV. File metadata and ingestion records are created by the server.
                      </Translate>
                    </FormText>
                  </div>
                </>
              ) : null}
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/file-ingestion" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating || uploading}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                {isNew ? (
                  <Translate contentKey="fintrackApp.fileIngestion.upload.submit">Upload CSV</Translate>
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

export default FileIngestionUpdate;
