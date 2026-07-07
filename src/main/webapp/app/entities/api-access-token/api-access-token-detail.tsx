import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './api-access-token.reducer';

export const ApiAccessTokenDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const apiAccessTokenEntity = useAppSelector(state => state.apiAccessToken.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="apiAccessTokenDetailsHeading">
          <Translate contentKey="fintrackApp.apiAccessToken.detail.title">ApiAccessToken</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{apiAccessTokenEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.apiAccessToken.name">Name</Translate>
            </span>
          </dt>
          <dd>{apiAccessTokenEntity.name}</dd>
          <dt>
            <span id="tokenPrefix">
              <Translate contentKey="fintrackApp.apiAccessToken.tokenPrefix">Token Prefix</Translate>
            </span>
          </dt>
          <dd>{apiAccessTokenEntity.tokenPrefix}</dd>
          <dt>
            <span id="tokenHash">
              <Translate contentKey="fintrackApp.apiAccessToken.tokenHash">Token Hash</Translate>
            </span>
          </dt>
          <dd>{apiAccessTokenEntity.tokenHash}</dd>
          <dt>
            <span id="status">
              <Translate contentKey="fintrackApp.apiAccessToken.status">Status</Translate>
            </span>
          </dt>
          <dd>{apiAccessTokenEntity.status}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.apiAccessToken.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {apiAccessTokenEntity.createdAt ? (
              <TextFormat value={apiAccessTokenEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.apiAccessToken.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>
            {apiAccessTokenEntity.updatedAt ? (
              <TextFormat value={apiAccessTokenEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="lastUsedAt">
              <Translate contentKey="fintrackApp.apiAccessToken.lastUsedAt">Last Used At</Translate>
            </span>
          </dt>
          <dd>
            {apiAccessTokenEntity.lastUsedAt ? (
              <TextFormat value={apiAccessTokenEntity.lastUsedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="expiresAt">
              <Translate contentKey="fintrackApp.apiAccessToken.expiresAt">Expires At</Translate>
            </span>
          </dt>
          <dd>
            {apiAccessTokenEntity.expiresAt ? (
              <TextFormat value={apiAccessTokenEntity.expiresAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="revokedAt">
              <Translate contentKey="fintrackApp.apiAccessToken.revokedAt">Revoked At</Translate>
            </span>
          </dt>
          <dd>
            {apiAccessTokenEntity.revokedAt ? (
              <TextFormat value={apiAccessTokenEntity.revokedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.apiAccessToken.user">User</Translate>
          </dt>
          <dd>{apiAccessTokenEntity.user ? apiAccessTokenEntity.user.login : ''}</dd>
        </dl>
        <Button tag={Link} to="/api-access-token" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/api-access-token/${apiAccessTokenEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default ApiAccessTokenDetail;
