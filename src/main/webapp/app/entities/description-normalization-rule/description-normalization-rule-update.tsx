import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Col, Row } from 'reactstrap';
import { Translate } from 'react-jhipster';

import DescriptionNormalizationRuleConfiguredForm from './components/description-normalization-rule-configured-form';
import { getConfiguredDescriptionNormalizationRule } from './description-normalization-rule-configured.service';
import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';
import { IContextualRuleSaveAction } from 'app/shared/model/contextual-rule-save-action.model';

const standaloneSaveActions: IContextualRuleSaveAction[] = [
  { action: 'SAVE', labelKey: 'entity.action.save', label: 'Save', dataCy: 'entityCreateSaveButton' },
];

/** Standalone configured create/edit route. The technical detail remains available until DNR-POLISH-1. */
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

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 data-cy="DescriptionNormalizationRuleCreateUpdateHeading">
            <Translate contentKey={headingKey}>Create or edit Description Normalization Rule</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
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
              onSaved={() => navigate('/description-normalization-rule')}
              onCancel={() => navigate('/description-normalization-rule')}
            />
          ) : null}
        </Col>
      </Row>
    </div>
  );
};

export default DescriptionNormalizationRuleUpdate;
