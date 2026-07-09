import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, isNumber, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { IngestionType } from 'app/shared/model/enumerations/ingestion-type.model';
import { IngestionStatus } from 'app/shared/model/enumerations/ingestion-status.model';
import { createEntity, getEntity, reset, updateEntity } from './transaction-ingestion.reducer';

export const TransactionIngestionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

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
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }
    values.startedAt = convertDateTimeToServer(values.startedAt);
    values.completedAt = convertDateTimeToServer(values.completedAt);
    if (values.recordsReceived !== undefined && typeof values.recordsReceived !== 'number') {
      values.recordsReceived = Number(values.recordsReceived);
    }
    if (values.recordsCreated !== undefined && typeof values.recordsCreated !== 'number') {
      values.recordsCreated = Number(values.recordsCreated);
    }
    if (values.recordsSkipped !== undefined && typeof values.recordsSkipped !== 'number') {
      values.recordsSkipped = Number(values.recordsSkipped);
    }
    if (values.recordsRejected !== undefined && typeof values.recordsRejected !== 'number') {
      values.recordsRejected = Number(values.recordsRejected);
    }
    values.createdAt = convertDateTimeToServer(values.createdAt);

    const entity = {
      ...transactionIngestionEntity,
      ...values,
      account: financialAccounts.find(it => it.id.toString() === values.account?.toString()),
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
          startedAt: displayDefaultDateTime(),
          completedAt: displayDefaultDateTime(),
          createdAt: displayDefaultDateTime(),
        }
      : {
          ingestionType: 'FILE',
          status: 'PENDING',
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
              <ValidatedField
                label={translate('fintrackApp.transactionIngestion.ingestionType')}
                id="transaction-ingestion-ingestionType"
                name="ingestionType"
                data-cy="ingestionType"
                type="select"
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
                label={translate('fintrackApp.transactionIngestion.startedAt')}
                id="transaction-ingestion-startedAt"
                name="startedAt"
                data-cy="startedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
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
                  required: { value: true, message: translate('entity.validation.required') },
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
                  required: { value: true, message: translate('entity.validation.required') },
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
                  required: { value: true, message: translate('entity.validation.required') },
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
                  required: { value: true, message: translate('entity.validation.required') },
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
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.transactionIngestion.account')}
                id="transaction-ingestion-account"
                data-cy="account"
                type="select"
                name="account"
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/transaction-ingestion" replace color="info">
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

export default TransactionIngestionUpdate;
