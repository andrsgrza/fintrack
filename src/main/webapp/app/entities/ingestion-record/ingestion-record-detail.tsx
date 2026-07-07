import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './ingestion-record.reducer';

export const IngestionRecordDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const ingestionRecordEntity = useAppSelector(state => state.ingestionRecord.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="ingestionRecordDetailsHeading">
          <Translate contentKey="fintrackApp.ingestionRecord.detail.title">IngestionRecord</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{ingestionRecordEntity.id}</dd>
          <dt>
            <span id="recordIndex">
              <Translate contentKey="fintrackApp.ingestionRecord.recordIndex">Record Index</Translate>
            </span>
          </dt>
          <dd>{ingestionRecordEntity.recordIndex}</dd>
          <dt>
            <span id="externalRecordId">
              <Translate contentKey="fintrackApp.ingestionRecord.externalRecordId">External Record Id</Translate>
            </span>
          </dt>
          <dd>{ingestionRecordEntity.externalRecordId}</dd>
          <dt>
            <span id="status">
              <Translate contentKey="fintrackApp.ingestionRecord.status">Status</Translate>
            </span>
          </dt>
          <dd>{ingestionRecordEntity.status}</dd>
          <dt>
            <span id="rawData">
              <Translate contentKey="fintrackApp.ingestionRecord.rawData">Raw Data</Translate>
            </span>
          </dt>
          <dd>{ingestionRecordEntity.rawData}</dd>
          <dt>
            <span id="errorCode">
              <Translate contentKey="fintrackApp.ingestionRecord.errorCode">Error Code</Translate>
            </span>
          </dt>
          <dd>{ingestionRecordEntity.errorCode}</dd>
          <dt>
            <span id="errorMessage">
              <Translate contentKey="fintrackApp.ingestionRecord.errorMessage">Error Message</Translate>
            </span>
          </dt>
          <dd>{ingestionRecordEntity.errorMessage}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.ingestionRecord.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {ingestionRecordEntity.createdAt ? (
              <TextFormat value={ingestionRecordEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.ingestionRecord.financialTransaction">Financial Transaction</Translate>
          </dt>
          <dd>{ingestionRecordEntity.financialTransaction ? ingestionRecordEntity.financialTransaction.id : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.ingestionRecord.transactionIngestion">Transaction Ingestion</Translate>
          </dt>
          <dd>{ingestionRecordEntity.transactionIngestion ? ingestionRecordEntity.transactionIngestion.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/ingestion-record" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/ingestion-record/${ingestionRecordEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default IngestionRecordDetail;
