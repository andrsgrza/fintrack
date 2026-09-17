import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Col, Row } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';

import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import TransactionRuleConfiguredForm from './components/transaction-rule-configured-form';
import { getConfiguredTransactionRule } from './transaction-rule-configured.service';

const standaloneSaveActions: IContextualRuleSaveAction[] = [
  { action: 'SAVE', labelKey: 'entity.action.save', label: 'Save', dataCy: 'entityCreateSaveButton' },
];

/** Standalone composition of the same configured rule form used by Transaction Ingestion. */
export const TransactionRuleUpdate = () => {
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isNew = id === undefined;
  const [configuredRule, setConfiguredRule] = useState<ITransactionRuleConfigured | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isNew || !id) {
      return;
    }
    setLoading(true);
    getConfiguredTransactionRule(id)
      .then(response => setConfiguredRule(response.data))
      .catch(() => setError(translate('fintrackApp.transactionRule.configuredLoadFailed')))
      .finally(() => setLoading(false));
  }, [id, isNew]);

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="fintrackApp.transactionRule.home.createOrEditLabel" data-cy="TransactionRuleCreateUpdateHeading">
            <Translate contentKey={isNew ? 'fintrackApp.transactionRule.createTitle' : 'fintrackApp.transactionRule.editTitle'}>
              {isNew ? 'Create Transaction Rule' : 'Edit Transaction Rule'}
            </Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? <p>Loading...</p> : null}
          {error ? (
            <Alert color="danger" fade={false}>
              {error}
            </Alert>
          ) : null}
          {!loading && !error ? (
            <TransactionRuleConfiguredForm
              key={configuredRule?.id ?? 'new'}
              initialValue={configuredRule ?? undefined}
              saveActions={standaloneSaveActions}
              onSaved={() => navigate('/transaction-rule')}
              onCancel={() => navigate('/transaction-rule')}
            />
          ) : null}
        </Col>
      </Row>
    </div>
  );
};

export default TransactionRuleUpdate;
