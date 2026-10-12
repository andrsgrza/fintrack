import React, { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, Spinner } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { getConfiguredDescriptionNormalizationRule } from './description-normalization-rule-configured.service';
import {
  formatDescriptionNormalizationCondition,
  formatDescriptionNormalizationLogic,
  formatDescriptionNormalizationRuleStatus,
} from './description-normalization-rule-display';

export const DescriptionNormalizationRuleDetail = () => {
  const { id } = useParams<'id'>();
  const [rule, setRule] = useState<IDescriptionNormalizationRuleConfigured | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      setLoadError(true);
      return undefined;
    }
    let active = true;
    setLoading(true);
    setLoadError(false);
    getConfiguredDescriptionNormalizationRule(id)
      .then(response => {
        if (active) setRule(response.data);
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
  }, [id]);

  if (loading) {
    return (
      <ProductPage>
        <p className="text-muted" role="status">
          <Spinner tag="span" size="sm" className="me-2" /> <Translate contentKey="global.messages.info.loading">Loading...</Translate>
        </p>
      </ProductPage>
    );
  }

  if (loadError || !rule) {
    return (
      <ProductPage>
        <Alert color="danger" fade={false} role="alert">
          <Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.loadFailed">
            This description rule could not be loaded. Return to the rule manager and try again.
          </Translate>
        </Alert>
        <Button tag={Link} to="/description-normalization-rule" color="secondary" outline>
          <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
        </Button>
      </ProductPage>
    );
  }

  return (
    <ProductPage>
      <ProductPageHeader
        headingId="description-normalization-rule-detail-heading"
        dataCy="descriptionNormalizationRuleDetailsHeading"
        title={rule.name}
        metadata={
          <>
            <span className={`badge rounded-pill ${rule.active ? 'text-bg-success' : 'text-bg-secondary'}`}>
              {formatDescriptionNormalizationRuleStatus(rule.active, translate)}
            </span>
            <span className="small text-muted" data-cy="descriptionNormalizationRuleDetailPosition">
              {translate('fintrackApp.descriptionNormalizationRule.reorder.position', { position: (rule.priority ?? 0) + 1 })}
            </span>
          </>
        }
        subtitle={rule.description ?? undefined}
        actions={
          <>
            <Button tag={Link} to="/description-normalization-rule" color="secondary" outline size="sm">
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
            </Button>
            <Button
              tag={Link}
              to={`/description-normalization-rule/${rule.id}/edit`}
              color="primary"
              size="sm"
              data-cy="descriptionNormalizationRuleDetailEdit"
              data-testid="descriptionNormalizationRuleDetailEdit"
            >
              <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
            </Button>
            <Button tag={Link} to={`/description-normalization-rule/${rule.id}/delete`} color="danger" outline size="sm">
              <FontAwesomeIcon icon="trash" /> <Translate contentKey="entity.action.delete">Delete</Translate>
            </Button>
          </>
        }
      />

      <div className="d-grid gap-3">
        <ProductSection title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.matching">Matching</Translate>}>
          <p className="mb-2">
            <span className="fw-semibold">{formatDescriptionNormalizationLogic(rule.conditionOperator, translate)}. </span>
            <Translate
              contentKey={
                rule.conditionOperator === 'ANY'
                  ? 'fintrackApp.descriptionNormalizationRule.detail.anyExplanation'
                  : 'fintrackApp.descriptionNormalizationRule.detail.allExplanation'
              }
            >
              Every condition below must match the original description.
            </Translate>
          </p>
          <ol className="mb-0 ps-3" data-cy="descriptionNormalizationRuleDetailConditions">
            {(rule.conditions ?? []).map((condition, index) => (
              <li key={`${condition.position ?? index}-${condition.operator}-${condition.value}`} className="mb-2">
                {formatDescriptionNormalizationCondition(condition, translate)}
              </li>
            ))}
          </ol>
        </ProductSection>

        <ProductSection title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.result">Result</Translate>}>
          <p className="mb-0">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.summary.then">Then replace the description with</Translate>{' '}
            <span className="fw-semibold">“{rule.resultingDescription}”</span>
          </p>
        </ProductSection>

        <ProductSection title={<Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.behavior">Behavior</Translate>}>
          <ul className="mb-0 ps-3 text-muted">
            <li>
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.fileImportScope">
                Applies only to original descriptions in file imports, not manual transaction drafts.
              </Translate>
            </li>
            <li>
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.firstMatch">
                Active rules are evaluated from top to bottom. The first matching rule determines the normalized description.
              </Translate>
            </li>
            <li>
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.detail.history">
                Changes affect future evaluations or explicitly reevaluated import rows; previously posted transactions are not changed.
              </Translate>
            </li>
          </ul>
        </ProductSection>
      </div>
    </ProductPage>
  );
};

export default DescriptionNormalizationRuleDetail;
