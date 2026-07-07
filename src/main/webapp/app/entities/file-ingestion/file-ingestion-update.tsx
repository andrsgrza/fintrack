import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getTransactionIngestions } from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import { ImportFileType } from 'app/shared/model/enumerations/import-file-type.model';
import { createEntity, getEntity, reset, updateEntity } from './file-ingestion.reducer';

export const FileIngestionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const transactionIngestions = useAppSelector(state => state.transactionIngestion.entities);
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
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getTransactionIngestions({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    if (values.fileSizeBytes !== undefined && typeof values.fileSizeBytes !== 'number') {
      values.fileSizeBytes = Number(values.fileSizeBytes);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);

    const entity = {
      ...fileIngestionEntity,
      ...values,
      transactionIngestion: transactionIngestions.find(it => it.id.toString() === values.transactionIngestion?.toString()),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          createdAt: displayDefaultDateTime(),
        }
      : {
          fileType: 'CSV',
          ...fileIngestionEntity,
          createdAt: convertDateTimeFromServer(fileIngestionEntity.createdAt),
          transactionIngestion: fileIngestionEntity?.transactionIngestion?.id,
        };

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
              <ValidatedField
                label={translate('fintrackApp.fileIngestion.createdAt')}
                id="file-ingestion-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="file-ingestion-transactionIngestion"
                name="transactionIngestion"
                data-cy="transactionIngestion"
                label={translate('fintrackApp.fileIngestion.transactionIngestion')}
                type="select"
                required
              >
                <option value="" key="0" />
                {transactionIngestions
                  ? transactionIngestions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/file-ingestion" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                <Translate contentKey="entity.action.save">Save</Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default FileIngestionUpdate;
