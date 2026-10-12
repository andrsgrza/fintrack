import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import { Link, useParams } from 'react-router-dom';
import { Alert, Button, Col, Row, Spinner } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import { ITransactionRuleConfigured } from 'app/shared/model/transaction-rule-configured.model';
import { useAppSelector } from 'app/config/store';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { TransactionCategory, TransactionTagChips } from 'app/entities/financial-transaction/transaction-presentation';
import { getConfiguredTransactionRule } from './transaction-rule-configured.service';
import {
  buildTransactionRuleConditionSummaryWithContext,
  formatTransactionRuleConditionLogic,
  formatTransactionRuleStatus,
} from './transaction-rule-display';

type DetailLoadError = 'forbidden' | 'notFound' | 'unknown' | null;

const outputIsUnavailable = (output?: { id?: number; name?: string | null } | null) => Boolean(output?.id !== undefined && !output?.name);

const DetailValue = ({ label, children }: { label: React.ReactNode; children: React.ReactNode }) => (
  <div>
    <div className="small text-uppercase text-muted fw-semibold mb-1">{label}</div>
    <div>{children}</div>
  </div>
);

/** Read-only product explanation of a configured classification rule. It intentionally does not evaluate transactions. */
export const TransactionRuleDetail = () => {
  const { id } = useParams<'id'>();
  const [rule, setRule] = useState<ITransactionRuleConfigured | null>(null);
  const [accounts, setAccounts] = useState<IFinancialAccount[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<DetailLoadError>(null);
  const [accountsUnavailable, setAccountsUnavailable] = useState(false);
  const locale = useAppSelector(state => state.locale.currentLocale);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      setLoadError('notFound');
      return undefined;
    }

    let active = true;
    setLoading(true);
    setLoadError(null);
    setAccountsUnavailable(false);

    getConfiguredTransactionRule(id)
      .then(response => {
        if (active) setRule(response.data);
      })
      .catch(error => {
        if (!active) return;
        const status = error?.response?.status;
        setLoadError(status === 404 ? 'notFound' : status === 401 || status === 403 ? 'forbidden' : 'unknown');
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    axios
      .get<IFinancialAccount[]>('api/financial-accounts?sort=name,ASC')
      .then(response => {
        if (active) setAccounts(response.data);
      })
      .catch(() => {
        if (active) setAccountsUnavailable(true);
      });

    return () => {
      active = false;
    };
  }, [id]);

  const accountsById = useMemo(
    () => new Map(accounts.flatMap(account => (account.id === undefined ? [] : [[account.id, account] as const]))),
    [accounts],
  );
  if (loading) {
    return (
      <ProductPage>
        <p className="text-muted" role="status" data-cy="transactionRuleDetailLoading" data-testid="transactionRuleDetailLoading">
          <Spinner tag="span" size="sm" className="me-2" /> <Translate contentKey="global.messages.info.loading">Loading...</Translate>
        </p>
      </ProductPage>
    );
  }

  if (loadError || !rule) {
    const errorKey =
      loadError === 'notFound'
        ? 'fintrackApp.transactionRule.detail.notFound'
        : loadError === 'forbidden'
          ? 'fintrackApp.transactionRule.detail.forbidden'
          : 'fintrackApp.transactionRule.detail.loadFailed';
    return (
      <ProductPage>
        <Alert
          color="danger"
          fade={false}
          role="alert"
          data-cy="transactionRuleDetailLoadError"
          data-testid="transactionRuleDetailLoadError"
        >
          <Translate contentKey={errorKey}>This classification rule could not be loaded.</Translate>
        </Alert>
        <Button tag={Link} to="/transaction-rule" color="secondary" outline>
          <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
        </Button>
      </ProductPage>
    );
  }

  const hasInactiveOutputs = rule.resultingCategory?.active === false || (rule.resultingTags ?? []).some(tag => tag.active === false);
  const missingOutputIdentity = outputIsUnavailable(rule.resultingCategory) || (rule.resultingTags ?? []).some(outputIsUnavailable);
  const position = (rule.priority ?? 0) + 1;
  const logicKey = rule.conditionLogic === 'ANY' ? 'anyExplanation' : 'allExplanation';

  return (
    <ProductPage>
      <ProductPageHeader
        headingId="transaction-rule-detail-heading"
        dataCy="transactionRuleDetailsHeading"
        title={rule.name || <Translate contentKey="fintrackApp.transactionRule.detail.title">Classification rule</Translate>}
        subtitle={rule.description ?? undefined}
        metadata={
          <>
            <span
              className={`badge rounded-pill ${rule.active ? 'text-bg-success' : 'text-bg-secondary'}`}
              data-cy="transactionRuleDetailStatus"
              data-testid="transactionRuleDetailStatus"
            >
              {formatTransactionRuleStatus(rule, translate)}
            </span>
            <span className="small text-muted" data-cy="transactionRuleDetailPosition" data-testid="transactionRuleDetailPosition">
              {translate('fintrackApp.transactionRule.reorder.position', { position })}
            </span>
          </>
        }
        actions={
          <>
            <Button tag={Link} to="/transaction-rule" color="secondary" outline size="sm" data-cy="entityDetailsBackButton">
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
            </Button>
            <Button
              tag={Link}
              to={`/transaction-rule/${rule.id}/edit`}
              color="primary"
              size="sm"
              data-cy="transactionRuleDetailEdit"
              data-testid="transactionRuleDetailEdit"
            >
              <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
            </Button>
            <Button tag={Link} to={`/transaction-rule/${rule.id}/delete`} color="danger" outline size="sm" data-cy="entityDeleteButton">
              <FontAwesomeIcon icon="trash" /> <Translate contentKey="entity.action.delete">Delete</Translate>
            </Button>
          </>
        }
      />

      <div className="vstack gap-3">
        {rule.active === false ? (
          <Alert
            color="secondary"
            fade={false}
            className="py-2 small mb-0"
            data-cy="transactionRuleInactiveExplanation"
            data-testid="transactionRuleInactiveExplanation"
          >
            <Translate contentKey="fintrackApp.transactionRule.detail.inactiveExplanation">
              This rule remains configured but does not participate in future classification evaluations. Existing history is unchanged.
            </Translate>
          </Alert>
        ) : null}

        <ProductSection
          title={<Translate contentKey="fintrackApp.transactionRule.detail.matching">Matching</Translate>}
          dataCy="transactionRuleDetailMatching"
        >
          <p className="mb-3" data-cy="transactionRuleDetailLogicExplanation" data-testid="transactionRuleDetailLogicExplanation">
            <span className="fw-semibold">{formatTransactionRuleConditionLogic(rule.conditionLogic, translate)}. </span>
            <Translate contentKey={`fintrackApp.transactionRule.detail.${logicKey}`}>
              Every condition below must match for this rule to suggest its outputs.
            </Translate>
          </p>
          {accountsUnavailable ? (
            <Alert
              color="warning"
              fade={false}
              className="small py-2"
              data-cy="transactionRuleDetailAccountsUnavailable"
              data-testid="transactionRuleDetailAccountsUnavailable"
            >
              <Translate contentKey="fintrackApp.transactionRule.detail.accountsUnavailable">
                Account names are temporarily unavailable. Account conditions are shown without raw account identifiers.
              </Translate>
            </Alert>
          ) : null}
          {(rule.conditions ?? []).length ? (
            <ol className="mb-0 ps-3 text-break" data-cy="transactionRuleDetailConditions" data-testid="transactionRuleDetailConditions">
              {(rule.conditions ?? []).map((condition, index) => (
                <li key={condition.id ?? `${condition.position ?? index}-${condition.field}-${condition.operator}`} className="mb-2">
                  {buildTransactionRuleConditionSummaryWithContext(condition, translate, accountsById, locale)}
                </li>
              ))}
            </ol>
          ) : (
            <p className="mb-0 text-muted" data-cy="transactionRuleDetailNoConditions" data-testid="transactionRuleDetailNoConditions">
              <Translate contentKey="fintrackApp.transactionRule.summary.noConditions">No conditions configured.</Translate>
            </p>
          )}
        </ProductSection>

        <ProductSection
          title={<Translate contentKey="fintrackApp.transactionRule.detail.results">Suggested results</Translate>}
          dataCy="transactionRuleDetailResults"
        >
          {missingOutputIdentity ? (
            <Alert
              color="warning"
              fade={false}
              className="small py-2"
              data-cy="transactionRuleDetailOutputUnavailable"
              data-testid="transactionRuleDetailOutputUnavailable"
            >
              <Translate contentKey="fintrackApp.transactionRule.detail.outputUnavailable">
                Some configured output details are temporarily unavailable. They have not been removed from this rule.
              </Translate>
            </Alert>
          ) : null}
          {rule.resultingCategory || rule.resultingTags?.length ? (
            <Row className="g-4">
              {rule.resultingCategory ? (
                <Col md="6">
                  <DetailValue label={<Translate contentKey="fintrackApp.transactionRule.result.category">Category</Translate>}>
                    {outputIsUnavailable(rule.resultingCategory) ? (
                      <span className="text-muted">—</span>
                    ) : (
                      <TransactionCategory category={rule.resultingCategory} />
                    )}
                  </DetailValue>
                </Col>
              ) : null}
              {rule.resultingTags?.length ? (
                <Col md="6">
                  <DetailValue label={<Translate contentKey="fintrackApp.transactionRule.result.tags">Tags</Translate>}>
                    <TransactionTagChips tags={rule.resultingTags} maxVisible={rule.resultingTags.length} showInactiveLabel />
                  </DetailValue>
                </Col>
              ) : null}
            </Row>
          ) : (
            <p className="mb-0 text-muted" data-cy="transactionRuleDetailNoResults" data-testid="transactionRuleDetailNoResults">
              <Translate contentKey="fintrackApp.transactionRule.result.empty">No result configured</Translate>
            </p>
          )}
          {hasInactiveOutputs ? (
            <p
              className="small text-muted mb-0 mt-3"
              data-cy="transactionRuleDetailInactiveOutputExplanation"
              data-testid="transactionRuleDetailInactiveOutputExplanation"
            >
              <Translate contentKey="fintrackApp.transactionRule.detail.inactiveOutputExplanation">
                Inactive category or tag outputs remain visible as historical configuration, but are not eligible for new suggestions until
                reactivated.
              </Translate>
            </p>
          ) : null}
        </ProductSection>

        <ProductSection
          title={<Translate contentKey="fintrackApp.transactionRule.detail.behavior">Evaluation behavior</Translate>}
          dataCy="transactionRuleDetailBehavior"
        >
          <div className="vstack gap-3 text-muted">
            <p className="mb-0" data-cy="transactionRuleDetailPriorityExplanation" data-testid="transactionRuleDetailPriorityExplanation">
              <Translate contentKey="fintrackApp.transactionRule.detail.priorityExplanation" interpolate={{ position }}>
                This rule has a position in the evaluation order. Active classification rules are evaluated in that order.
              </Translate>
            </p>
            <p className="mb-0" data-cy="transactionRuleDetailMultiRuleExplanation" data-testid="transactionRuleDetailMultiRuleExplanation">
              <Translate contentKey="fintrackApp.transactionRule.detail.multiRuleExplanation">
                Multiple rules can match. The first eligible category from matching rules wins; eligible tags from matching rules accumulate
                without duplicates. Inactive rules do not contribute.
              </Translate>
            </p>
            <p
              className="mb-0"
              data-cy="transactionRuleDetailConfigurationExplanation"
              data-testid="transactionRuleDetailConfigurationExplanation"
            >
              <Translate contentKey="fintrackApp.transactionRule.detail.configurationExplanation">
                This page explains the stored configuration. It does not show whether a particular transaction matched or which suggestions
                were applied.
              </Translate>
            </p>
            <p className="mb-0" data-cy="transactionRuleDetailWorkflowExplanation" data-testid="transactionRuleDetailWorkflowExplanation">
              <Translate contentKey="fintrackApp.transactionRule.detail.workflowExplanation">
                Rules generate category and tag suggestions in supported transaction-ingestion and manual-draft review workflows.
                Suggestions are applied through those workflows; editing a rule does not change posted history.
              </Translate>
            </p>
          </div>
        </ProductSection>
      </div>
    </ProductPage>
  );
};

export default TransactionRuleDetail;
