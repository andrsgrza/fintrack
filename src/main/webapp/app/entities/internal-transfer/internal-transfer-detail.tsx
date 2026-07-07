import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './internal-transfer.reducer';

export const InternalTransferDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const internalTransferEntity = useAppSelector(state => state.internalTransfer.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="internalTransferDetailsHeading">
          <Translate contentKey="fintrackApp.internalTransfer.detail.title">InternalTransfer</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{internalTransferEntity.id}</dd>
          <dt>
            <span id="notes">
              <Translate contentKey="fintrackApp.internalTransfer.notes">Notes</Translate>
            </span>
          </dt>
          <dd>{internalTransferEntity.notes}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.internalTransfer.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {internalTransferEntity.createdAt ? (
              <TextFormat value={internalTransferEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.internalTransfer.outgoingTransaction">Outgoing Transaction</Translate>
          </dt>
          <dd>{internalTransferEntity.outgoingTransaction ? internalTransferEntity.outgoingTransaction.id : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.internalTransfer.incomingTransaction">Incoming Transaction</Translate>
          </dt>
          <dd>{internalTransferEntity.incomingTransaction ? internalTransferEntity.incomingTransaction.id : ''}</dd>
        </dl>
        <Button tag={Link} to="/internal-transfer" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/internal-transfer/${internalTransferEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default InternalTransferDetail;
