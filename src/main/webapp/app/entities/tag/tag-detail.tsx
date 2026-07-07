import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './tag.reducer';

export const TagDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const tagEntity = useAppSelector(state => state.tag.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="tagDetailsHeading">
          <Translate contentKey="fintrackApp.tag.detail.title">Tag</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{tagEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.tag.name">Name</Translate>
            </span>
          </dt>
          <dd>{tagEntity.name}</dd>
          <dt>
            <span id="description">
              <Translate contentKey="fintrackApp.tag.description">Description</Translate>
            </span>
          </dt>
          <dd>{tagEntity.description}</dd>
          <dt>
            <span id="color">
              <Translate contentKey="fintrackApp.tag.color">Color</Translate>
            </span>
          </dt>
          <dd>{tagEntity.color}</dd>
          <dt>
            <span id="active">
              <Translate contentKey="fintrackApp.tag.active">Active</Translate>
            </span>
          </dt>
          <dd>{tagEntity.active ? 'true' : 'false'}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.tag.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>{tagEntity.createdAt ? <TextFormat value={tagEntity.createdAt} type="date" format={APP_DATE_FORMAT} /> : null}</dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.tag.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>{tagEntity.updatedAt ? <TextFormat value={tagEntity.updatedAt} type="date" format={APP_DATE_FORMAT} /> : null}</dd>
          <dt>
            <Translate contentKey="fintrackApp.tag.user">User</Translate>
          </dt>
          <dd>{tagEntity.user ? tagEntity.user.login : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.tag.financialTransactions">Financial Transactions</Translate>
          </dt>
          <dd>
            {tagEntity.financialTransactions
              ? tagEntity.financialTransactions.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {tagEntity.financialTransactions && i === tagEntity.financialTransactions.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.tag.transactionRules">Transaction Rules</Translate>
          </dt>
          <dd>
            {tagEntity.transactionRules
              ? tagEntity.transactionRules.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {tagEntity.transactionRules && i === tagEntity.transactionRules.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.tag.subscriptions">Subscriptions</Translate>
          </dt>
          <dd>
            {tagEntity.subscriptions
              ? tagEntity.subscriptions.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {tagEntity.subscriptions && i === tagEntity.subscriptions.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.tag.budgets">Budgets</Translate>
          </dt>
          <dd>
            {tagEntity.budgets
              ? tagEntity.budgets.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.id}</a>
                    {tagEntity.budgets && i === tagEntity.budgets.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <Button tag={Link} to="/tag" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/tag/${tagEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default TagDetail;
