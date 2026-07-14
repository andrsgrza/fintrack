import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntity } from './transaction-rule.reducer';
import TransactionRuleConditionsRelatedList from './components/transaction-rule-conditions-related-list';

export const TransactionRuleDetail = () => {
  const dispatch = useAppDispatch();

  const { id } = useParams<'id'>();

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const transactionRuleEntity = useAppSelector(state => state.transactionRule.entity);
  return (
    <Row>
      <Col md="8">
        <h2 data-cy="transactionRuleDetailsHeading">
          <Translate contentKey="fintrackApp.transactionRule.detail.title">TransactionRule</Translate>
        </h2>
        <dl className="jh-entity-details">
          <dt>
            <span id="id">
              <Translate contentKey="global.field.id">ID</Translate>
            </span>
          </dt>
          <dd>{transactionRuleEntity.id}</dd>
          <dt>
            <span id="name">
              <Translate contentKey="fintrackApp.transactionRule.name">Name</Translate>
            </span>
          </dt>
          <dd>{transactionRuleEntity.name}</dd>
          <dt>
            <span id="description">
              <Translate contentKey="fintrackApp.transactionRule.description">Description</Translate>
            </span>
          </dt>
          <dd>{transactionRuleEntity.description}</dd>
          <dt>
            <span id="priority">
              <Translate contentKey="fintrackApp.transactionRule.priority">Priority</Translate>
            </span>
          </dt>
          <dd>{transactionRuleEntity.priority}</dd>
          <dt>
            <span id="conditionLogic">
              <Translate contentKey="fintrackApp.transactionRule.conditionLogic">Condition Logic</Translate>
            </span>
          </dt>
          <dd>{transactionRuleEntity.conditionLogic}</dd>
          <dt>
            <span id="resultingDescription">
              <Translate contentKey="fintrackApp.transactionRule.resultingDescription">Resulting Description</Translate>
            </span>
          </dt>
          <dd>{transactionRuleEntity.resultingDescription}</dd>
          <dt>
            <span id="active">
              <Translate contentKey="fintrackApp.transactionRule.active">Active</Translate>
            </span>
          </dt>
          <dd>{transactionRuleEntity.active ? 'true' : 'false'}</dd>
          <dt>
            <span id="createdAt">
              <Translate contentKey="fintrackApp.transactionRule.createdAt">Created At</Translate>
            </span>
          </dt>
          <dd>
            {transactionRuleEntity.createdAt ? (
              <TextFormat value={transactionRuleEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <span id="updatedAt">
              <Translate contentKey="fintrackApp.transactionRule.updatedAt">Updated At</Translate>
            </span>
          </dt>
          <dd>
            {transactionRuleEntity.updatedAt ? (
              <TextFormat value={transactionRuleEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : null}
          </dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionRule.resultingCategory">Resulting Category</Translate>
          </dt>
          <dd>{transactionRuleEntity.resultingCategory ? transactionRuleEntity.resultingCategory.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionRule.resultingFinancialSubscription">Resulting Financial Subscription</Translate>
          </dt>
          <dd>{transactionRuleEntity.resultingFinancialSubscription ? transactionRuleEntity.resultingFinancialSubscription.name : ''}</dd>
          <dt>
            <Translate contentKey="fintrackApp.transactionRule.resultingTags">Resulting Tags</Translate>
          </dt>
          <dd>
            {transactionRuleEntity.resultingTags
              ? transactionRuleEntity.resultingTags.map((val, i) => (
                  <span key={val.id}>
                    <a>{val.name}</a>
                    {transactionRuleEntity.resultingTags && i === transactionRuleEntity.resultingTags.length - 1 ? '' : ', '}
                  </span>
                ))
              : null}
          </dd>
        </dl>
        <TransactionRuleConditionsRelatedList transactionRuleId={transactionRuleEntity.id} />
        <Button tag={Link} to="/transaction-rule" replace color="info" data-cy="entityDetailsBackButton">
          <FontAwesomeIcon icon="arrow-left" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.back">Back</Translate>
          </span>
        </Button>
        &nbsp;
        <Button tag={Link} to={`/transaction-rule/${transactionRuleEntity.id}/edit`} replace color="primary">
          <FontAwesomeIcon icon="pencil-alt" />{' '}
          <span className="d-none d-md-inline">
            <Translate contentKey="entity.action.edit">Edit</Translate>
          </span>
        </Button>
      </Col>
    </Row>
  );
};

export default TransactionRuleDetail;
