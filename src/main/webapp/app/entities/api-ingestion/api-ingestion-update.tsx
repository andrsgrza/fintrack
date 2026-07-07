import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getTransactionIngestions } from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import { getEntities as getApiAccessTokens } from 'app/entities/api-access-token/api-access-token.reducer';
import { createEntity, getEntity, reset, updateEntity } from './api-ingestion.reducer';

export const ApiIngestionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const transactionIngestions = useAppSelector(state => state.transactionIngestion.entities);
  const apiAccessTokens = useAppSelector(state => state.apiAccessToken.entities);
  const apiIngestionEntity = useAppSelector(state => state.apiIngestion.entity);
  const loading = useAppSelector(state => state.apiIngestion.loading);
  const updating = useAppSelector(state => state.apiIngestion.updating);
  const updateSuccess = useAppSelector(state => state.apiIngestion.updateSuccess);

  const handleClose = () => {
    navigate('/api-ingestion');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getTransactionIngestions({}));
    dispatch(getApiAccessTokens({}));
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
    values.receivedAt = convertDateTimeToServer(values.receivedAt);
    values.createdAt = convertDateTimeToServer(values.createdAt);

    const entity = {
      ...apiIngestionEntity,
      ...values,
      transactionIngestion: transactionIngestions.find(it => it.id.toString() === values.transactionIngestion?.toString()),
      apiAccessToken: apiAccessTokens.find(it => it.id.toString() === values.apiAccessToken?.toString()),
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
          receivedAt: displayDefaultDateTime(),
          createdAt: displayDefaultDateTime(),
        }
      : {
          ...apiIngestionEntity,
          receivedAt: convertDateTimeFromServer(apiIngestionEntity.receivedAt),
          createdAt: convertDateTimeFromServer(apiIngestionEntity.createdAt),
          transactionIngestion: apiIngestionEntity?.transactionIngestion?.id,
          apiAccessToken: apiIngestionEntity?.apiAccessToken?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.apiIngestion.home.createOrEditLabel" data-cy="ApiIngestionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.apiIngestion.home.createOrEditLabel">Create or edit a ApiIngestion</Translate>
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
                  id="api-ingestion-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.requestId')}
                id="api-ingestion-requestId"
                name="requestId"
                data-cy="requestId"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.idempotencyKey')}
                id="api-ingestion-idempotencyKey"
                name="idempotencyKey"
                data-cy="idempotencyKey"
                type="text"
                validate={{
                  maxLength: { value: 150, message: translate('entity.validation.maxlength', { max: 150 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.sourceSystem')}
                id="api-ingestion-sourceSystem"
                name="sourceSystem"
                data-cy="sourceSystem"
                type="text"
                validate={{
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.apiVersion')}
                id="api-ingestion-apiVersion"
                name="apiVersion"
                data-cy="apiVersion"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  maxLength: { value: 20, message: translate('entity.validation.maxlength', { max: 20 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.endpoint')}
                id="api-ingestion-endpoint"
                name="endpoint"
                data-cy="endpoint"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  maxLength: { value: 150, message: translate('entity.validation.maxlength', { max: 150 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.clientReference')}
                id="api-ingestion-clientReference"
                name="clientReference"
                data-cy="clientReference"
                type="text"
                validate={{
                  maxLength: { value: 150, message: translate('entity.validation.maxlength', { max: 150 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.receivedAt')}
                id="api-ingestion-receivedAt"
                name="receivedAt"
                data-cy="receivedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiIngestion.createdAt')}
                id="api-ingestion-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="api-ingestion-transactionIngestion"
                name="transactionIngestion"
                data-cy="transactionIngestion"
                label={translate('fintrackApp.apiIngestion.transactionIngestion')}
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
              <ValidatedField
                id="api-ingestion-apiAccessToken"
                name="apiAccessToken"
                data-cy="apiAccessToken"
                label={translate('fintrackApp.apiIngestion.apiAccessToken')}
                type="select"
                required
              >
                <option value="" key="0" />
                {apiAccessTokens
                  ? apiAccessTokens.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.name}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/api-ingestion" replace color="info">
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

export default ApiIngestionUpdate;
