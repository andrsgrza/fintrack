import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import { ProductPage, ProductPageHeader } from 'app/shared/ui/product-page';
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

  const returnPath = !isNew && id ? `/transaction-rule/${id}` : '/transaction-rule';

  return (
    <ProductPage>
      <ProductPageHeader
        headingId="fintrackApp.transactionRule.home.createOrEditLabel"
        dataCy="TransactionRuleCreateUpdateHeading"
        title={
          <Translate contentKey={isNew ? 'fintrackApp.transactionRule.createTitle' : 'fintrackApp.transactionRule.editTitle'}>
            {isNew ? 'Create Transaction Rule' : 'Edit Transaction Rule'}
          </Translate>
        }
        subtitle={
          <Translate contentKey="fintrackApp.transactionRule.configuredCreateEditHelp">
            Configure the complete rule before saving.
          </Translate>
        }
        actions={
          <Button color="secondary" outline size="sm" onClick={() => navigate(returnPath)}>
            <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.cancel">Cancel</Translate>
          </Button>
        }
      />
      {loading ? (
        <p>
          <Translate contentKey="global.messages.info.loading">Loading...</Translate>
        </p>
      ) : null}
      {error ? (
        <Alert color="danger" fade={false} role="alert">
          {error}
        </Alert>
      ) : null}
      {!loading && !error ? (
        <TransactionRuleConfiguredForm
          key={configuredRule?.id ?? 'new'}
          initialValue={configuredRule ?? undefined}
          saveActions={standaloneSaveActions}
          onSaved={savedRule => navigate(isNew ? '/transaction-rule' : `/transaction-rule/${savedRule.id}`)}
          onCancel={() => navigate(returnPath)}
        />
      ) : null}
    </ProductPage>
  );
};

export default TransactionRuleUpdate;
