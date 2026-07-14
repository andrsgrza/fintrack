import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { ITransactionRule } from 'app/shared/model/transaction-rule.model';
import { getEntity } from './transaction-rule.reducer';
import TransactionRuleConditionsCollectionEditor from './components/transaction-rule-conditions-collection-editor';
import { formatTransactionRuleConditionLogic, formatTransactionRuleStatus } from './transaction-rule-display';

const emptyValue = () => translate('fintrackApp.transactionRule.emptyValue');

const displayText = (value?: string | number | null) => {
  if (value === undefined || value === null || value === '') {
    return emptyValue();
  }
  return value;
};

const displayOrder = (priority?: number | null) => (priority === undefined || priority === null ? emptyValue() : `#${priority + 1}`);

const tagNames = (rule: ITransactionRule) =>
  rule.resultingTags
    ?.map(tag => tag.name)
    .filter(Boolean)
    .join(', ') || emptyValue();

const FieldRow = ({ label, children }: { label: string; children: React.ReactNode }) => (
  <p className="mb-1">
    <strong>{label}:</strong> {children}
  </p>
);

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
          {transactionRuleEntity.name || <Translate contentKey="fintrackApp.transactionRule.detail.title">Transaction Rule</Translate>}
        </h2>
        <section className="mb-4" aria-labelledby="transaction-rule-identity-heading">
          <h3 id="transaction-rule-identity-heading">
            <Translate contentKey="fintrackApp.transactionRule.sections.identity">Identity</Translate>
          </h3>
          <FieldRow label={translate('fintrackApp.transactionRule.name')}>{displayText(transactionRuleEntity.name)}</FieldRow>
          <FieldRow label={translate('fintrackApp.transactionRule.description')}>{displayText(transactionRuleEntity.description)}</FieldRow>
          <FieldRow label={translate('fintrackApp.transactionRule.evaluationOrder')}>
            {displayOrder(transactionRuleEntity.priority)}
          </FieldRow>
        </section>
        <section className="mb-4" aria-labelledby="transaction-rule-matching-heading">
          <h3 id="transaction-rule-matching-heading">
            <Translate contentKey="fintrackApp.transactionRule.sections.matching">Matching logic</Translate>
          </h3>
          <FieldRow label={translate('fintrackApp.transactionRule.conditionLogic')}>
            {formatTransactionRuleConditionLogic(transactionRuleEntity.conditionLogic, translate)}
          </FieldRow>
        </section>
        <section className="mb-4" aria-labelledby="transaction-rule-result-heading">
          <h3 id="transaction-rule-result-heading">
            <Translate contentKey="fintrackApp.transactionRule.sections.result">Result</Translate>
          </h3>
          <FieldRow label={translate('fintrackApp.transactionRule.resultingCategory')}>
            {displayText(transactionRuleEntity.resultingCategory?.name)}
          </FieldRow>
          <FieldRow label={translate('fintrackApp.transactionRule.resultingTags')}>{tagNames(transactionRuleEntity)}</FieldRow>
        </section>
        <section className="mb-4" aria-labelledby="transaction-rule-status-metadata-heading">
          <h3 id="transaction-rule-status-metadata-heading">
            <Translate contentKey="fintrackApp.transactionRule.sections.statusMetadata">Status / Metadata</Translate>
          </h3>
          <FieldRow label={translate('fintrackApp.transactionRule.status.label')}>
            {formatTransactionRuleStatus(transactionRuleEntity, translate)}
          </FieldRow>
          <FieldRow label={translate('fintrackApp.transactionRule.createdAt')}>
            {transactionRuleEntity.createdAt ? (
              <TextFormat value={transactionRuleEntity.createdAt} type="date" format={APP_DATE_FORMAT} />
            ) : (
              emptyValue()
            )}
          </FieldRow>
          <FieldRow label={translate('fintrackApp.transactionRule.updatedAt')}>
            {transactionRuleEntity.updatedAt ? (
              <TextFormat value={transactionRuleEntity.updatedAt} type="date" format={APP_DATE_FORMAT} />
            ) : (
              emptyValue()
            )}
          </FieldRow>
        </section>
        <TransactionRuleConditionsCollectionEditor
          transactionRuleId={transactionRuleEntity.id}
          onConditionMutation={() => dispatch(getEntity(id))}
        />
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
