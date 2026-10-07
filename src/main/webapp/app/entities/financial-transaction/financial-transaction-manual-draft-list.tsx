import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Badge, Button, DropdownItem, Spinner } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';
import { TransactionAccountLabel, TransactionAmount, TransactionClassification } from './transaction-presentation';
import { cancelManualDraft, getManualDrafts, IManualTransactionDraftSummary } from './services/manual-transaction-candidate.service';

const statusLabelKey = (status?: string | null) => {
  if (status === 'DRAFT') {
    return 'fintrackApp.financialTransaction.manualDraftList.status.draft';
  }
  if (status === 'READY_TO_POST') {
    return 'fintrackApp.financialTransaction.manualDraftList.status.readyToPost';
  }
  return null;
};

const classificationLabelKey = (status?: string | null) => {
  if (status === 'NOT_EVALUATED') {
    return 'fintrackApp.financialTransaction.manualDraftList.classification.notEvaluated';
  }
  if (status === 'STALE') {
    return 'fintrackApp.financialTransaction.manualDraftList.classification.stale';
  }
  if (status === 'SUGGESTED') {
    return 'fintrackApp.financialTransaction.manualDraftList.classification.suggested';
  }
  if (status === 'USER_SELECTED') {
    return 'fintrackApp.financialTransaction.manualDraftList.classification.userSelected';
  }
  if (status === 'NOT_APPLICABLE') {
    return 'fintrackApp.financialTransaction.manualDraftList.classification.notApplicable';
  }
  return null;
};

const renderTranslatedStatus = (contentKey: string | null) => {
  if (contentKey) {
    return <Translate contentKey={contentKey}>Unknown</Translate>;
  }
  return <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.unknown">Unknown</Translate>;
};

const hasAmount = (draft: IManualTransactionDraftSummary) =>
  draft.amount !== undefined && draft.amount !== null && !!draft.flow && !!draft.currencySnapshot;

const DraftAccount = ({ draft }: { draft: IManualTransactionDraftSummary }) => {
  if (!draft.accountName) {
    return (
      <span className="text-muted">
        <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noAccount">No account</Translate>
      </span>
    );
  }

  return (
    <TransactionAccountLabel
      account={{
        id: draft.accountId ?? undefined,
        name: draft.accountName,
        accountType: draft.accountType,
        currency: draft.currencySnapshot,
        lastFourDigits: draft.accountLastFourDigits,
        active: draft.accountActive,
      }}
      className="text-muted"
    />
  );
};

export const FinancialTransactionManualDraftList = () => {
  const [drafts, setDrafts] = useState<IManualTransactionDraftSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [cancelError, setCancelError] = useState(false);
  const [cancellingId, setCancellingId] = useState<number | null>(null);

  const loadDrafts = () => {
    setLoading(true);
    setLoadError(false);
    getManualDrafts()
      .then(response => {
        setDrafts(response.data ?? []);
      })
      .catch(() => {
        setLoadError(true);
      })
      .finally(() => {
        setLoading(false);
      });
  };

  useEffect(() => {
    loadDrafts();
  }, []);

  const handleCancel = (draft: IManualTransactionDraftSummary) => {
    if (!draft.id) {
      return;
    }
    const confirmed = window.confirm(translate('fintrackApp.financialTransaction.manualDraftList.cancelConfirm'));
    if (!confirmed) {
      return;
    }

    setCancelError(false);
    setCancellingId(draft.id);
    cancelManualDraft(draft.id)
      .then(() => {
        setDrafts(currentDrafts => currentDrafts.filter(currentDraft => currentDraft.id !== draft.id));
      })
      .catch(() => {
        setCancelError(true);
      })
      .finally(() => {
        setCancellingId(null);
      });
  };

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="manual-transaction-drafts-heading"
        dataCy="ManualTransactionDraftsHeading"
        title={<Translate contentKey="fintrackApp.financialTransaction.manualDraftList.title">Transaction drafts</Translate>}
        subtitle={
          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.subtitle">
            Continue a saved transaction whenever you are ready.
          </Translate>
        }
        actions={
          <>
            <Button tag={Link} to="/financial-transaction" color="secondary" outline size="sm" data-cy="manualDraftBackToTransactions">
              <FontAwesomeIcon icon="arrow-left" />{' '}
              <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.viewTransactions">View transactions</Translate>
            </Button>
            <Button tag={Link} to="/financial-transaction/new" color="primary" size="sm" data-cy="manualDraftCreateButton">
              <FontAwesomeIcon icon="plus" />{' '}
              <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.createManualTransaction">New transaction</Translate>
            </Button>
          </>
        }
      />

      {loading ? (
        <div className="d-flex align-items-center gap-2 text-muted py-4" data-cy="manualDraftsLoading">
          <Spinner size="sm" />
          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.loading">Loading drafts…</Translate>
        </div>
      ) : null}

      {loadError ? (
        <Alert color="danger" fade={false} data-cy="manualDraftsLoadError">
          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.loadFailed">Could not load drafts.</Translate>
        </Alert>
      ) : null}

      {cancelError ? (
        <Alert color="danger" fade={false} data-cy="manualDraftCancelError">
          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.cancelFailed">Could not cancel the draft.</Translate>
        </Alert>
      ) : null}

      {!loading && !loadError && drafts.length === 0 ? (
        <ProductSection
          title={<Translate contentKey="fintrackApp.financialTransaction.manualDraftList.emptyTitle">No saved drafts</Translate>}
        >
          <p className="text-muted mb-3">
            <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.empty">
              No transaction drafts are waiting for you.
            </Translate>
          </p>
          <Button tag={Link} to="/financial-transaction/new" color="primary" size="sm" data-cy="manualDraftEmptyCreateButton">
            <FontAwesomeIcon icon="plus" />{' '}
            <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.createManualTransaction">New transaction</Translate>
          </Button>
        </ProductSection>
      ) : null}

      {!loading && !loadError && drafts.length > 0 ? (
        <div className="vstack gap-2" data-cy="manualDraftProductList">
          {drafts.map(draft => (
            <article key={draft.id} className="border rounded-3 bg-white p-3 p-md-4" data-cy="manualDraftRow">
              <div className="d-flex flex-column flex-md-row align-items-md-start gap-3">
                <div className="flex-grow-1 min-w-0">
                  <Link
                    to={`/financial-transaction/drafts/${draft.id}`}
                    className="h5 d-inline-block mb-2 text-decoration-none text-body"
                    data-cy="manualDraftResumeLink"
                  >
                    {draft.description?.trim() || (
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noDescription">No description</Translate>
                    )}
                  </Link>
                  <div className="d-flex flex-wrap gap-2 small text-muted mb-3">
                    <DraftAccount draft={draft} />
                    {draft.accountName && draft.transactionDate ? <span>·</span> : null}
                    {draft.transactionDate ? (
                      <span>
                        <TextFormat type="date" value={draft.transactionDate} format={APP_LOCAL_DATE_FORMAT} />
                      </span>
                    ) : (
                      <span>
                        <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noDate">No date</Translate>
                      </span>
                    )}
                  </div>
                  <TransactionClassification
                    draftCategory={{
                      id: draft.categoryId ?? undefined,
                      name: draft.categoryName,
                      parentName: draft.categoryParentName,
                      color: draft.categoryColor,
                      active: draft.categoryActive,
                    }}
                    tags={draft.tags}
                    tagNames={draft.tagNames}
                    showEmptyTags
                  />
                  <div className="d-flex flex-wrap gap-2 align-items-center mt-3 small">
                    <Badge color={draft.status === 'READY_TO_POST' ? 'success' : 'secondary'} pill data-cy="manualDraftStatus">
                      {renderTranslatedStatus(statusLabelKey(draft.status))}
                    </Badge>
                    <Badge color="light" className="border text-dark fw-normal" pill data-cy="manualDraftClassification">
                      {renderTranslatedStatus(classificationLabelKey(draft.classificationReviewStatus))}
                    </Badge>
                    {draft.updatedAt ? (
                      <span className="text-muted">
                        <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.updated">Updated</Translate>{' '}
                        <TextFormat type="date" value={draft.updatedAt} format={APP_DATE_FORMAT} />
                      </span>
                    ) : null}
                  </div>
                </div>

                <div className="d-flex align-items-start justify-content-between gap-3 flex-shrink-0">
                  {hasAmount(draft) ? (
                    <TransactionAmount amount={draft.amount} currency={draft.currencySnapshot} flow={draft.flow} />
                  ) : (
                    <span className="text-muted small">
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noAmount">No amount</Translate>
                    </span>
                  )}
                  <div className="d-flex align-items-center gap-1">
                    <Button
                      tag={Link}
                      to={`/financial-transaction/drafts/${draft.id}`}
                      color="primary"
                      size="sm"
                      data-cy="manualDraftResumeButton"
                    >
                      <FontAwesomeIcon icon="pencil-alt" />{' '}
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.resume">Continue</Translate>
                    </Button>
                    <ProductActionsMenu
                      label={translate('fintrackApp.financialTransaction.manualDraftList.moreActions')}
                      dataCy="manualDraftActionsMenu"
                    >
                      <DropdownItem
                        className="text-danger"
                        onClick={() => handleCancel(draft)}
                        disabled={cancellingId === draft.id}
                        data-cy="manualDraftCancelListButton"
                      >
                        <FontAwesomeIcon icon="ban" className="me-2" />
                        <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.cancelDraft">Cancel draft</Translate>
                      </DropdownItem>
                    </ProductActionsMenu>
                  </div>
                </div>
              </div>
            </article>
          ))}
        </div>
      ) : null}
    </ProductPage>
  );
};

export default FinancialTransactionManualDraftList;
