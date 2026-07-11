import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntitiesWhereApiIngestionIsNull } from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import { getEntities as getApiAccessTokens } from 'app/entities/api-access-token/api-access-token.reducer';
import { createEntity, getEntity, reset, updateEntity } from './api-ingestion.reducer';

export const ApiIngestionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const apiIngestionParentCandidates = useAppSelector(state => state.transactionIngestion.apiIngestionParentCandidates ?? []);
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
      dispatch(getEntitiesWhereApiIngestionIsNull());
      dispatch(getApiAccessTokens({}));
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

    const entity = isNew
      ? {
          requestId: values.requestId,
          idempotencyKey: values.idempotencyKey,
          sourceSystem: values.sourceSystem,
          apiVersion: values.apiVersion,
          endpoint: values.endpoint,
          clientReference: values.clientReference,
          transactionIngestion: apiIngestionParentCandidates.find(it => it.id.toString() === values.transactionIngestion?.toString()),
          apiAccessToken: apiAccessTokens.find(it => it.id.toString() === values.apiAccessToken?.toString()),
        }
      : {
          ...apiIngestionEntity,
          ...values,
          requestId: apiIngestionEntity?.requestId,
          transactionIngestion: apiIngestionEntity?.transactionIngestion,
          apiAccessToken: apiIngestionEntity?.apiAccessToken,
          createdAt: apiIngestionEntity?.createdAt,
          receivedAt: apiIngestionEntity?.receivedAt,
          apiTokenIdSnapshot: apiIngestionEntity?.apiTokenIdSnapshot,
          apiTokenPrefixSnapshot: apiIngestionEntity?.apiTokenPrefixSnapshot,
          apiTokenNameSnapshot: apiIngestionEntity?.apiTokenNameSnapshot,
        };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {}
      : {
          ...apiIngestionEntity,
          receivedAt: convertDateTimeFromServer(apiIngestionEntity.receivedAt),
          createdAt: convertDateTimeFromServer(apiIngestionEntity.createdAt),
          transactionIngestion: apiIngestionEntity?.transactionIngestion?.id,
        };

  const parentOptions = isNew
    ? apiIngestionParentCandidates
    : apiIngestionEntity?.transactionIngestion
      ? [apiIngestionEntity.transactionIngestion]
      : [];

  const tokenOptions = isNew ? apiAccessTokens : [];

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
                readOnly={!isNew}
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
              {!isNew ? (
                <>
                  <ValidatedField
                    label={translate('fintrackApp.apiIngestion.receivedAt')}
                    id="api-ingestion-receivedAt"
                    name="receivedAt"
                    data-cy="receivedAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiIngestion.createdAt')}
                    id="api-ingestion-createdAt"
                    name="createdAt"
                    data-cy="createdAt"
                    type="datetime-local"
                    placeholder="YYYY-MM-DD HH:mm"
                    readOnly
                  />
                </>
              ) : null}
              <ValidatedField
                id="api-ingestion-transactionIngestion"
                name="transactionIngestion"
                data-cy="transactionIngestion"
                label={translate('fintrackApp.apiIngestion.transactionIngestion')}
                type="select"
                disabled={!isNew}
                required
              >
                <option value="" key="0" />
                {parentOptions
                  ? parentOptions.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              {!isNew ? (
                <>
                  <ValidatedField
                    label={translate('fintrackApp.apiIngestion.apiTokenIdSnapshot')}
                    id="api-ingestion-apiTokenIdSnapshot"
                    name="apiTokenIdSnapshot"
                    data-cy="apiTokenIdSnapshot"
                    type="text"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiIngestion.apiTokenPrefixSnapshot')}
                    id="api-ingestion-apiTokenPrefixSnapshot"
                    name="apiTokenPrefixSnapshot"
                    data-cy="apiTokenPrefixSnapshot"
                    type="text"
                    readOnly
                  />
                  <ValidatedField
                    label={translate('fintrackApp.apiIngestion.apiTokenNameSnapshot')}
                    id="api-ingestion-apiTokenNameSnapshot"
                    name="apiTokenNameSnapshot"
                    data-cy="apiTokenNameSnapshot"
                    type="text"
                    readOnly
                  />
                </>
              ) : (
                <>
                  <ValidatedField
                    id="api-ingestion-apiAccessToken"
                    name="apiAccessToken"
                    data-cy="apiAccessToken"
                    label={translate('fintrackApp.apiIngestion.apiAccessToken')}
                    type="select"
                    required
                  >
                    <option value="" key="0" />
                    {tokenOptions
                      ? tokenOptions.map(otherEntity => (
                          <option value={otherEntity.id} key={otherEntity.id}>
                            {otherEntity.name}
                          </option>
                        ))
                      : null}
                  </ValidatedField>
                  <FormText>
                    <Translate contentKey="entity.validation.required">This field is required.</Translate>
                  </FormText>
                </>
              )}
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
