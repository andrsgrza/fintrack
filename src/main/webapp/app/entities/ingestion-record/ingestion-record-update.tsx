import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntitiesWhereIngestionRecordIsNull } from 'app/entities/financial-transaction/financial-transaction.reducer';
import { getEntities as getTransactionIngestions } from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import { IngestionRecordStatus } from 'app/shared/model/enumerations/ingestion-record-status.model';
import { createEntity, getEntity, reset, updateEntity } from './ingestion-record.reducer';

export const IngestionRecordUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const ingestionRecordParentCandidates = useAppSelector(state => state.financialTransaction.ingestionRecordParentCandidates ?? []);
  const transactionIngestions = useAppSelector(state => state.transactionIngestion.entities);
  const ingestionRecordEntity = useAppSelector(state => state.ingestionRecord.entity);
  const loading = useAppSelector(state => state.ingestionRecord.loading);
  const updating = useAppSelector(state => state.ingestionRecord.updating);
  const updateSuccess = useAppSelector(state => state.ingestionRecord.updateSuccess);
  const ingestionRecordStatusValues = Object.keys(IngestionRecordStatus);

  const handleClose = () => {
    navigate(`/ingestion-record${location.search}`);
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
      dispatch(getEntitiesWhereIngestionRecordIsNull());
      dispatch(getTransactionIngestions({}));
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
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    if (values.recordIndex !== undefined && typeof values.recordIndex !== 'number') {
      values.recordIndex = Number(values.recordIndex);
    }

    const entity = isNew
      ? {
          recordIndex: values.recordIndex,
          externalRecordId: values.externalRecordId,
          status: values.status,
          rawData: values.rawData,
          errorCode: values.errorCode,
          errorMessage: values.errorMessage,
          transactionIngestion: transactionIngestions.find(it => it.id.toString() === values.transactionIngestion?.toString()),
          financialTransaction: values.financialTransaction
            ? ingestionRecordParentCandidates.find(it => it.id.toString() === values.financialTransaction?.toString())
            : undefined,
        }
      : {
          ...ingestionRecordEntity,
          ...values,
          recordIndex: ingestionRecordEntity?.recordIndex,
          transactionIngestion: ingestionRecordEntity?.transactionIngestion,
          financialTransaction: ingestionRecordEntity?.financialTransaction,
          createdAt: ingestionRecordEntity?.createdAt,
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
          status: 'VALID',
        }
      : {
          status: 'VALID',
          ...ingestionRecordEntity,
          createdAt: convertDateTimeFromServer(ingestionRecordEntity.createdAt),
          financialTransaction: ingestionRecordEntity?.financialTransaction?.id,
          transactionIngestion: ingestionRecordEntity?.transactionIngestion?.id,
        };

  const financialTransactionOptions = isNew
    ? ingestionRecordParentCandidates
    : ingestionRecordEntity?.financialTransaction
      ? [ingestionRecordEntity.financialTransaction]
      : [];

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.ingestionRecord.home.createOrEditLabel" data-cy="IngestionRecordCreateUpdateHeading">
            <Translate contentKey="fintrackApp.ingestionRecord.home.createOrEditLabel">Create or edit a IngestionRecord</Translate>
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
                  id="ingestion-record-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.ingestionRecord.recordIndex')}
                id="ingestion-record-recordIndex"
                name="recordIndex"
                data-cy="recordIndex"
                type="text"
                readOnly={!isNew}
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  min: { value: 0, message: translate('entity.validation.min', { min: 0 }) },
                  validate: v => isNumber(v) || translate('entity.validation.number'),
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.ingestionRecord.externalRecordId')}
                id="ingestion-record-externalRecordId"
                name="externalRecordId"
                data-cy="externalRecordId"
                type="text"
                validate={{
                  maxLength: { value: 150, message: translate('entity.validation.maxlength', { max: 150 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.ingestionRecord.status')}
                id="ingestion-record-status"
                name="status"
                data-cy="status"
                type="select"
              >
                {ingestionRecordStatusValues.map(ingestionRecordStatus => (
                  <option value={ingestionRecordStatus} key={ingestionRecordStatus}>
                    {translate(`fintrackApp.IngestionRecordStatus.${ingestionRecordStatus}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.ingestionRecord.rawData')}
                id="ingestion-record-rawData"
                name="rawData"
                data-cy="rawData"
                type="textarea"
              />
              <ValidatedField
                label={translate('fintrackApp.ingestionRecord.errorCode')}
                id="ingestion-record-errorCode"
                name="errorCode"
                data-cy="errorCode"
                type="text"
                validate={{
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.ingestionRecord.errorMessage')}
                id="ingestion-record-errorMessage"
                name="errorMessage"
                data-cy="errorMessage"
                type="text"
                validate={{
                  maxLength: { value: 1000, message: translate('entity.validation.maxlength', { max: 1000 }) },
                }}
              />
              {!isNew ? (
                <ValidatedField
                  label={translate('fintrackApp.ingestionRecord.createdAt')}
                  id="ingestion-record-createdAt"
                  name="createdAt"
                  data-cy="createdAt"
                  type="datetime-local"
                  placeholder="YYYY-MM-DD HH:mm"
                  readOnly
                />
              ) : null}
              <ValidatedField
                id="ingestion-record-financialTransaction"
                name="financialTransaction"
                data-cy="financialTransaction"
                label={translate('fintrackApp.ingestionRecord.financialTransaction')}
                type="select"
                disabled={!isNew}
              >
                <option value="" key="0" />
                {financialTransactionOptions
                  ? financialTransactionOptions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                id="ingestion-record-transactionIngestion"
                name="transactionIngestion"
                data-cy="transactionIngestion"
                label={translate('fintrackApp.ingestionRecord.transactionIngestion')}
                type="select"
                disabled={!isNew}
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/ingestion-record" replace color="info">
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

export default IngestionRecordUpdate;
