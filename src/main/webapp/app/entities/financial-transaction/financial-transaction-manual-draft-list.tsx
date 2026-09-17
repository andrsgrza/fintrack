import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Badge, Button, Spinner, Table } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
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

const renderTranslatedOrFallback = (contentKey: string | null, fallback?: string | null) => {
  if (contentKey) {
    return <Translate contentKey={contentKey}>{fallback}</Translate>;
  }
  return fallback || <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.unknown">Unknown</Translate>;
};

const hasAmount = (draft: IManualTransactionDraftSummary) =>
  draft.amount !== undefined && draft.amount !== null && !!draft.flow && !!draft.currencySnapshot;

const renderAmount = (draft: IManualTransactionDraftSummary) => {
  if (!hasAmount(draft)) {
    return <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noAmount">No amount</Translate>;
  }
  const knownFlow = draft.flow === 'IN' || draft.flow === 'OUT';
  return (
    <>
      {draft.amount} {knownFlow ? <Translate contentKey={`fintrackApp.TransactionFlow.${draft.flow}`}>{draft.flow}</Translate> : draft.flow}{' '}
      {draft.currencySnapshot}
    </>
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
    <div>
      <h2 id="manual-transaction-drafts-heading" data-cy="ManualTransactionDraftsHeading">
        <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.title">Manual transaction drafts</Translate>
        <div className="d-flex justify-content-end">
          <Button tag={Link} to="/financial-transaction/new" color="primary" data-cy="manualDraftCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.createManualTransaction">
              Create manual transaction
            </Translate>
          </Button>
        </div>
      </h2>

      {loading ? (
        <div data-cy="manualDraftsLoading">
          <Spinner size="sm" />{' '}
          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.loading">Loading manual drafts…</Translate>
        </div>
      ) : null}

      {loadError ? (
        <Alert color="danger" fade={false} data-cy="manualDraftsLoadError">
          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.loadFailed">Could not load manual drafts.</Translate>
        </Alert>
      ) : null}

      {cancelError ? (
        <Alert color="danger" fade={false} data-cy="manualDraftCancelError">
          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.cancelFailed">Could not cancel the draft.</Translate>
        </Alert>
      ) : null}

      {!loading && !loadError && drafts.length === 0 ? (
        <Alert color="warning" fade={false} data-cy="manualDraftsEmpty">
          <p className="mb-2">
            <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.empty">No manual drafts.</Translate>
          </p>
          <Button tag={Link} to="/financial-transaction/new" color="primary" size="sm" data-cy="manualDraftEmptyCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.createManualTransaction">
              Create manual transaction
            </Translate>
          </Button>
        </Alert>
      ) : null}

      {!loading && !loadError && drafts.length > 0 ? (
        <div className="table-responsive">
          <Table responsive>
            <thead>
              <tr>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.description">Description</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.account">Account</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction date</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.amount">Amount</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.draftStatus">Draft status</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.classificationStatus">Classification</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.category">Category</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.tags">Tags</Translate>
                </th>
                <th>
                  <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.lastUpdated">Last updated</Translate>
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {drafts.map(draft => (
                <tr key={draft.id} data-cy="manualDraftRow">
                  <td>
                    {draft.description?.trim() || (
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.untitledDraft">Untitled draft</Translate>
                    )}
                  </td>
                  <td>
                    {draft.accountName || (
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noAccount">No account</Translate>
                    )}
                  </td>
                  <td>
                    {draft.transactionDate ? (
                      <TextFormat type="date" value={draft.transactionDate} format={APP_LOCAL_DATE_FORMAT} />
                    ) : (
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noDate">No date</Translate>
                    )}
                  </td>
                  <td>{renderAmount(draft)}</td>
                  <td>
                    <Badge color={draft.status === 'READY_TO_POST' ? 'success' : 'secondary'}>
                      {renderTranslatedOrFallback(statusLabelKey(draft.status), draft.status)}
                    </Badge>
                  </td>
                  <td>
                    {renderTranslatedOrFallback(classificationLabelKey(draft.classificationReviewStatus), draft.classificationReviewStatus)}
                  </td>
                  <td>
                    {draft.categoryName || (
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noCategory">No category</Translate>
                    )}
                  </td>
                  <td>
                    {draft.tagNames?.length ? (
                      draft.tagNames.join(', ')
                    ) : (
                      <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.noTags">No tags</Translate>
                    )}
                  </td>
                  <td>{draft.updatedAt ? <TextFormat type="date" value={draft.updatedAt} format={APP_DATE_FORMAT} /> : null}</td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/financial-transaction/drafts/${draft.id}`}
                        color="info"
                        size="sm"
                        data-cy="manualDraftResumeButton"
                      >
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.resume">Resume</Translate>
                        </span>
                      </Button>
                      <Button
                        color="danger"
                        size="sm"
                        onClick={() => handleCancel(draft)}
                        disabled={cancellingId === draft.id}
                        data-cy="manualDraftCancelListButton"
                      >
                        <FontAwesomeIcon icon="ban" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="fintrackApp.financialTransaction.manualDraftList.cancelDraft">Cancel draft</Translate>
                        </span>
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        </div>
      ) : null}
    </div>
  );
};

export default FinancialTransactionManualDraftList;
