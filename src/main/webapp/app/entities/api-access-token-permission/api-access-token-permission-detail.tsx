import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './api-access-token-permission.reducer';

export const ApiAccessTokenPermissionDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const apiAccessTokenPermissionEntity = useAppSelector(state => state.apiAccessTokenPermission.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="apiAccessTokenPermissionDetailsHeading">
          <Translate contentKey="fintrackApp.apiAccessTokenPermission.detail.title">ApiAccessTokenPermission</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{apiAccessTokenPermissionEntity.id}</dd>
          <dt>
            <span id="permission">
              <Translate contentKey="fintrackApp.apiAccessTokenPermission.permission">Permission</Translate>
            </span>
          </dt>
          <dd>{apiAccessTokenPermissionEntity.permission}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.apiAccessTokenPermission.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {apiAccessTokenPermissionEntity.createdAt ? (
              <TextFormat value={apiAccessTokenPermissionEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.apiAccessTokenPermission.apiAccessToken">Api Access Token</Translate>
          </dt>
          <dd>{apiAccessTokenPermissionEntity.apiAccessToken ? apiAccessTokenPermissionEntity.apiAccessToken.name : ''}</dd>
        </dl>
        <Button tag={Link} to="/api-access-token-permission" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/api-access-token-permission/${apiAccessTokenPermissionEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default ApiAccessTokenPermissionDetail;
