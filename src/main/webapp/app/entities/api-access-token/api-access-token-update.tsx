import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getUsers } from 'app/modules/administration/user-management/user-management.reducer';
import { getEntities as getFinancialAccounts } from 'app/entities/financial-account/financial-account.reducer';
import { ApiTokenStatus } from 'app/shared/model/enumerations/api-token-status.model';
import { createEntity, getEntity, reset, updateEntity } from './api-access-token.reducer';

export const ApiAccessTokenUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const users = useAppSelector(state => state.userManagement.users);
  const financialAccounts = useAppSelector(state => state.financialAccount.entities);
  const apiAccessTokenEntity = useAppSelector(state => state.apiAccessToken.entity);
  const loading = useAppSelector(state => state.apiAccessToken.loading);
  const updating = useAppSelector(state => state.apiAccessToken.updating);
  const updateSuccess = useAppSelector(state => state.apiAccessToken.updateSuccess);
  const apiTokenStatusValues = Object.keys(ApiTokenStatus);

  const handleClose = () => {
    navigate('/api-access-token');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getUsers({}));
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
    values.createdAt = convertDateTimeToServer(values.createdAt);
    values.updatedAt = convertDateTimeToServer(values.updatedAt);
    values.lastUsedAt = convertDateTimeToServer(values.lastUsedAt);
    values.expiresAt = convertDateTimeToServer(values.expiresAt);
    values.revokedAt = convertDateTimeToServer(values.revokedAt);

    const entity = {
      ...apiAccessTokenEntity,
      ...values,
      user: users.find(it => it.id.toString() === values.user?.toString()),
      accounts: mapIdList(values.accounts),
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
          updatedAt: displayDefaultDateTime(),
          lastUsedAt: displayDefaultDateTime(),
          expiresAt: displayDefaultDateTime(),
          revokedAt: displayDefaultDateTime(),
        }
      : {
          status: 'ACTIVE',
          ...apiAccessTokenEntity,
          createdAt: convertDateTimeFromServer(apiAccessTokenEntity.createdAt),
          updatedAt: convertDateTimeFromServer(apiAccessTokenEntity.updatedAt),
          lastUsedAt: convertDateTimeFromServer(apiAccessTokenEntity.lastUsedAt),
          expiresAt: convertDateTimeFromServer(apiAccessTokenEntity.expiresAt),
          revokedAt: convertDateTimeFromServer(apiAccessTokenEntity.revokedAt),
          user: apiAccessTokenEntity?.user?.id,
          accounts: apiAccessTokenEntity?.accounts?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.apiAccessToken.home.createOrEditLabel" data-cy="ApiAccessTokenCreateUpdateHeading">
            <Translate contentKey="fintrackApp.apiAccessToken.home.createOrEditLabel">Create or edit a ApiAccessToken</Translate>
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
                  id="api-access-token-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.name')}
                id="api-access-token-name"
                name="name"
                data-cy="name"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  minLength: { value: 1, message: translate('entity.validation.minlength', { min: 1 }) },
                  maxLength: { value: 100, message: translate('entity.validation.maxlength', { max: 100 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.tokenPrefix')}
                id="api-access-token-tokenPrefix"
                name="tokenPrefix"
                data-cy="tokenPrefix"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                  maxLength: { value: 20, message: translate('entity.validation.maxlength', { max: 20 }) },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.tokenHash')}
                id="api-access-token-tokenHash"
                name="tokenHash"
                data-cy="tokenHash"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.status')}
                id="api-access-token-status"
                name="status"
                data-cy="status"
                type="select"
              >
                {apiTokenStatusValues.map(apiTokenStatus => (
                  <option value={apiTokenStatus} key={apiTokenStatus}>
                    {translate(`fintrackApp.ApiTokenStatus.${apiTokenStatus}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.createdAt')}
                id="api-access-token-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.updatedAt')}
                id="api-access-token-updatedAt"
                name="updatedAt"
                data-cy="updatedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.lastUsedAt')}
                id="api-access-token-lastUsedAt"
                name="lastUsedAt"
                data-cy="lastUsedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.expiresAt')}
                id="api-access-token-expiresAt"
                name="expiresAt"
                data-cy="expiresAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.revokedAt')}
                id="api-access-token-revokedAt"
                name="revokedAt"
                data-cy="revokedAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField
                id="api-access-token-user"
                name="user"
                data-cy="user"
                label={translate('fintrackApp.apiAccessToken.user')}
                type="select"
                required
              >
                <option value="" key="0" />
                {users
                  ? users.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.login}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <FormText>
                <Translate contentKey="entity.validation.required">This field is required.</Translate>
              </FormText>
              <ValidatedField
                label={translate('fintrackApp.apiAccessToken.accounts')}
                id="api-access-token-accounts"
                data-cy="accounts"
                type="select"
                multiple
                name="accounts"
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/api-access-token" replace color="info">
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

export default ApiAccessTokenUpdate;
