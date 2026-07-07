import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, FormText, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getApiAccessTokens } from 'app/entities/api-access-token/api-access-token.reducer';
import { ApiPermission } from 'app/shared/model/enumerations/api-permission.model';
import { createEntity, getEntity, reset, updateEntity } from './api-access-token-permission.reducer';

export const ApiAccessTokenPermissionUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const apiAccessTokens = useAppSelector(state => state.apiAccessToken.entities);
  const apiAccessTokenPermissionEntity = useAppSelector(state => state.apiAccessTokenPermission.entity);
  const loading = useAppSelector(state => state.apiAccessTokenPermission.loading);
  const updating = useAppSelector(state => state.apiAccessTokenPermission.updating);
  const updateSuccess = useAppSelector(state => state.apiAccessTokenPermission.updateSuccess);
  const apiPermissionValues = Object.keys(ApiPermission);

  const handleClose = () => {
    navigate('/api-access-token-permission');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

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
    values.createdAt = convertDateTimeToServer(values.createdAt);

    const entity = {
      ...apiAccessTokenPermissionEntity,
      ...values,
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
          createdAt: displayDefaultDateTime(),
        }
      : {
          permission: 'CREATE_TRANSACTIONS',
          ...apiAccessTokenPermissionEntity,
          createdAt: convertDateTimeFromServer(apiAccessTokenPermissionEntity.createdAt),
          apiAccessToken: apiAccessTokenPermissionEntity?.apiAccessToken?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.apiAccessTokenPermission.home.createOrEditLabel" data-cy="ApiAccessTokenPermissionCreateUpdateHeading">
            <Translate contentKey="fintrackApp.apiAccessTokenPermission.home.createOrEditLabel">
              Create or edit a ApiAccessTokenPermission
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
                  id="api-access-token-permission-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('fintrackApp.apiAccessTokenPermission.permission')}
                id="api-access-token-permission-permission"
                name="permission"
                data-cy="permission"
                type="select"
              >
                {apiPermissionValues.map(apiPermission => (
                  <option value={apiPermission} key={apiPermission}>
                    {translate(`fintrackApp.ApiPermission.${apiPermission}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('fintrackApp.apiAccessTokenPermission.createdAt')}
                id="api-access-token-permission-createdAt"
                name="createdAt"
                data-cy="createdAt"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                id="api-access-token-permission-apiAccessToken"
                name="apiAccessToken"
                data-cy="apiAccessToken"
                label={translate('fintrackApp.apiAccessTokenPermission.apiAccessToken')}
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/api-access-token-permission" replace color="info">
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

export default ApiAccessTokenPermissionUpdate;
