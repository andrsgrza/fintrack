import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import DescriptionNormalizationRuleConfiguredForm from './components/description-normalization-rule-configured-form';
import { getConfiguredDescriptionNormalizationRule } from './description-normalization-rule-configured.service';
import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';
import { IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';
import { ProductPage, ProductPageHeader } from 'app/shared/ui/product-page';

const standaloneSaveActions: IContextualRuleSaveAction[] = [
  { action: 'SAVE', labelKey: 'entity.action.save', label: 'Save', dataCy: 'entityCreateSaveButton' },
];

/** Standalone configured create/edit route used by the description-rules product manager. */
export const DescriptionNormalizationRuleUpdate = () => {
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const isNew = id === undefined;
  const [configuredRule, setConfiguredRule] = useState<IDescriptionNormalizationRuleConfigured | null>(null);
  const [loading, setLoading] = useState(!isNew);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    if (isNew || !id) return;

    let active = true;
    setLoading(true);
    setLoadError(false);
    getConfiguredDescriptionNormalizationRule(id)
      .then(response => {
        if (active) setConfiguredRule(response.data);
      })
      .catch(() => {
        if (active) setLoadError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [id, isNew]);

  const headingKey = isNew ? 'fintrackApp.descriptionNormalizationRule.createTitle' : 'fintrackApp.descriptionNormalizationRule.editTitle';

  const returnPath = !isNew && id ? `/description-normalization-rule/${id}` : '/description-normalization-rule';

  return (
    <ProductPage>
      <ProductPageHeader
        headingId="description-normalization-rule-create-update-heading"
        dataCy="DescriptionNormalizationRuleCreateUpdateHeading"
        title={<Translate contentKey={headingKey}>Create or edit Description Normalization Rule</Translate>}
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
      {loadError ? (
        <Alert color="danger" role="alert">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.configuredLoadFailed">
            The rule could not be loaded. Return to the rule manager and try again.
          </Translate>
        </Alert>
      ) : null}
      {!loading && !loadError ? (
        <DescriptionNormalizationRuleConfiguredForm
          key={configuredRule?.id ?? 'new'}
          initialValue={configuredRule ?? undefined}
          saveActions={standaloneSaveActions}
          onSaved={savedRule => navigate(isNew ? '/description-normalization-rule' : `/description-normalization-rule/${savedRule.id}`)}
          onCancel={() => navigate(returnPath)}
        />
      ) : null}
    </ProductPage>
  );
};

export default DescriptionNormalizationRuleUpdate;
