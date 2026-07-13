import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './api-ingestion.reducer';

export const ApiIngestionDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const apiIngestionEntity = useAppSelector(state => state.apiIngestion.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="apiIngestionDetailsHeading">
          <Translate contentKey="fintrackApp.apiIngestion.detail.title">ApiIngestion</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{apiIngestionEntity.id}</dd>
          <dt>
            <span id="requestId">
              <Translate contentKey="fintrackApp.apiIngestion.requestId">Request Id</Translate>
            </span>
          </dt>
          <dd>{apiIngestionEntity.requestId}</dd>
          <dt>
            <span id="idempotencyKey">
              <Translate contentKey="fintrackApp.apiIngestion.idempotencyKey">Idempotency Key</Translate>
            </span>
          </dt>
          <dd>{apiIngestionEntity.idempotencyKey}</dd>
          <dt>
            <span id="sourceSystem">
              <Translate contentKey="fintrackApp.apiIngestion.sourceSystem">Source System</Translate>
            </span>
          </dt>
          <dd>{apiIngestionEntity.sourceSystem}</dd>
          <dt>
            <span id="apiVersion">
              <Translate contentKey="fintrackApp.apiIngestion.apiVersion">Api Version</Translate>
            </span>
          </dt>
          <dd>{apiIngestionEntity.apiVersion}</dd>
          <dt>
            <span id="endpoint">
              <Translate contentKey="fintrackApp.apiIngestion.endpoint">Endpoint</Translate>
            </span>
          </dt>
          <dd>{apiIngestionEntity.endpoint}</dd>
          <dt>
            <span id="clientReference">
              <Translate contentKey="fintrackApp.apiIngestion.clientReference">Client Reference</Translate>
            </span>
          </dt>
          <dd>{apiIngestionEntity.clientReference}</dd>
          <dt>
            <span id="receivedAt">
              <Translate contentKey="fintrackApp.apiIngestion.receivedAt">Received At</Translate>
            </span>
          </dt>
          <dd>
            {apiIngestionEntity.receivedAt ? (
              <TextFormat value={apiIngestionEntity.receivedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.apiIngestion.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {apiIngestionEntity.createdAt ? <TextFormat value={apiIngestionEntity.createdAt} type="date" format={APP_DATE_FORMAT} /> : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.apiIngestion.transactionIngestion">Transaction Ingestion</Translate>
          </dt>
          <dd>{apiIngestionEntity.transactionIngestion ? apiIngestionEntity.transactionIngestion.id : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.apiIngestion.apiTokenNameSnapshot">Token Name (snapshot)</Translate>
          </dt>
          <dd>{apiIngestionEntity.apiTokenNameSnapshot}</dd>
          <dt>
            <Translate contentKey="fintrackApp.apiIngestion.apiTokenPrefixSnapshot">Token Prefix (snapshot)</Translate>
          </dt>
          <dd>{apiIngestionEntity.apiTokenPrefixSnapshot}</dd>
          <dt>
            <Translate contentKey="fintrackApp.apiIngestion.apiTokenIdSnapshot">Token Id (snapshot)</Translate>
          </dt>
          <dd>{apiIngestionEntity.apiTokenIdSnapshot}</dd>
        </dl>
        <Button tag={Link} to="/api-ingestion" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/api-ingestion/${apiIngestionEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default ApiIngestionDetail;
