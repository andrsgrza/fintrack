import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { IFinancialAccount } from 'app/shared/model/financial-account.model';
import {
  IFinancialAccountDeletionBlocker,
  IFinancialAccountDeletionPreview,
  IFinancialAccountHardDeleteBlocked,
} from 'app/shared/model/financial-account-deletion-preview.model';

import AccountDeletionBlockers from './components/account-deletion-blockers';
import {
  getFinancialAccountDeletionPreview,
  getFinancialAccountForDeletion,
  hardDeleteFinancialAccount,
} from './financial-account-hard-delete.service';

type PreviewLoadState = 'loading' | 'ready' | 'error';

const isBlockedHardDelete = (data: unknown): data is IFinancialAccountHardDeleteBlocked => {
  const response = data as IFinancialAccountHardDeleteBlocked | undefined;
  return response?.code === 'ACCOUNT_HARD_DELETE_BLOCKED' && Array.isArray(response.blockers);
};

const DeletionSummary = ({ preview }: { preview: IFinancialAccountDeletionPreview }) => {
  const manualDrafts = preview.counts.manualCandidates;
  const items = [
    { count: preview.counts.financialTransactions, key: 'transactions' },
    { count: manualDrafts, key: 'manualDrafts' },
    { count: preview.counts.transactionIngestions, key: 'imports' },
    { count: preview.counts.ingestionRecords, key: 'importRecords' },
  ].filter(item => item.count > 0);

  return (
    <section
      className="mb-3"
      aria-labelledby="financial-account-delete-removes-heading"
      data-cy="financialAccountDeleteSummary"
      data-testid="financialAccountDeleteSummary"
    >
      <h3 id="financial-account-delete-removes-heading" className="h6 mb-2">
        <Translate contentKey="fintrackApp.financialAccount.delete.removes.title">Will be permanently deleted</Translate>
      </h3>
      {items.length > 0 ? (
        <ul className="mb-0 ps-3">
          {items.map(item => (
            <li key={item.key} data-cy={`financialAccountDeleteCount-${item.key}`} data-testid={`financialAccountDeleteCount-${item.key}`}>
              <Translate
                contentKey={`fintrackApp.financialAccount.delete.removes.${item.key}.${item.count === 1 ? 'one' : 'other'}`}
                interpolate={{ count: item.count }}
              >
                {`${item.count}`}
              </Translate>
            </li>
          ))}
          {preview.counts.creditAccountDetails > 0 ? (
            <li data-cy="financialAccountDeleteCount-creditDetails" data-testid="financialAccountDeleteCount-creditDetails">
              <Translate contentKey="fintrackApp.financialAccount.delete.removes.creditDetails">This account&apos;s card details</Translate>
            </li>
          ) : null}
        </ul>
      ) : (
        <p className="text-muted mb-0">
          <Translate contentKey="fintrackApp.financialAccount.delete.removes.empty">
            This account has no associated financial history.
          </Translate>
        </p>
      )}
    </section>
  );
};

export const FinancialAccountDeleteDialog = () => {
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const [account, setAccount] = useState<IFinancialAccount | null>(null);
  const [preview, setPreview] = useState<IFinancialAccountDeletionPreview | null>(null);
  const [previewState, setPreviewState] = useState<PreviewLoadState>('loading');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState(false);

  useEffect(() => {
    if (!id) {
      setPreviewState('error');
      return undefined;
    }

    let active = true;
    setPreviewState('loading');
    setPreview(null);
    setDeleteError(false);

    getFinancialAccountForDeletion(id)
      .then(response => {
        if (active) {
          setAccount(response.data);
        }
      })
      .catch(() => {
        if (active) {
          setAccount(null);
        }
      });

    getFinancialAccountDeletionPreview(id)
      .then(response => {
        if (active) {
          setPreview(response.data);
          setPreviewState('ready');
        }
      })
      .catch(() => {
        if (active) {
          setPreviewState('error');
        }
      });

    return () => {
      active = false;
    };
  }, [id]);

  const accountIdentity = useMemo(
    () => ({
      name: account?.name ?? preview?.accountName ?? translate('fintrackApp.financialAccount.delete.accountFallback'),
      subtitle: [
        account?.institutionName,
        account?.accountType ? translate(`fintrackApp.AccountType.${account.accountType}`) : undefined,
        account?.currency,
      ]
        .filter(Boolean)
        .join(' · '),
    }),
    [account, preview],
  );

  const blockers: IFinancialAccountDeletionBlocker[] = preview?.blockers ?? [];
  const canHardDelete = previewState === 'ready' && preview?.canHardDelete === true && blockers.length === 0;

  const handleClose = () => {
    navigate('/financial-account');
  };

  const handleDeactivateInstead = () => {
    if (id) {
      navigate(`/financial-account/${id}/edit`);
    }
  };

  const confirmDelete = async () => {
    if (!id || !canHardDelete || deleting) {
      return;
    }

    setDeleting(true);
    setDeleteError(false);
    try {
      await hardDeleteFinancialAccount(id);
      navigate('/financial-account', { replace: true });
    } catch (error) {
      const response = (error as { response?: { status?: number; data?: unknown } }).response;
      if (response?.status === 409 && isBlockedHardDelete(response.data)) {
        const raceBlockers = response.data.blockers;
        setPreview(current =>
          current
            ? {
                ...current,
                canHardDelete: false,
                blockers: raceBlockers,
              }
            : current,
        );
        setPreviewState('ready');
      } else {
        setDeleteError(true);
      }
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal isOpen toggle={handleClose} size="lg" contentClassName="shadow" labelledBy="financial-account-delete-title">
      <ModalHeader
        id="financial-account-delete-title"
        toggle={handleClose}
        data-cy="financialAccountDeleteDialogHeading"
        data-testid="financialAccountDeleteDialogHeading"
      >
        <Translate contentKey="fintrackApp.financialAccount.delete.title">Permanently delete account</Translate>
      </ModalHeader>
      <ModalBody aria-busy={previewState === 'loading'}>
        <div className="border-bottom pb-3 mb-3" data-cy="financialAccountDeleteIdentity" data-testid="financialAccountDeleteIdentity">
          <div className="fw-semibold">{accountIdentity.name}</div>
          {accountIdentity.subtitle ? <div className="small text-muted">{accountIdentity.subtitle}</div> : null}
        </div>

        <Alert color="danger" fade={false} className="mb-3">
          <Translate contentKey="fintrackApp.financialAccount.delete.warning">
            This permanently deletes the financial data associated with this account and cannot be undone.
          </Translate>
        </Alert>

        {previewState === 'loading' ? (
          <p
            className="text-muted mb-3"
            role="status"
            aria-live="polite"
            data-cy="financialAccountDeletePreviewLoading"
            data-testid="financialAccountDeletePreviewLoading"
          >
            <FontAwesomeIcon icon="sync" spin />{' '}
            <Translate contentKey="fintrackApp.financialAccount.delete.loading">Loading deletion information...</Translate>
          </p>
        ) : null}

        {previewState === 'error' ? (
          <Alert
            color="danger"
            fade={false}
            data-cy="financialAccountDeletePreviewError"
            data-testid="financialAccountDeletePreviewError"
            role="alert"
          >
            <Translate contentKey="fintrackApp.financialAccount.delete.previewFailed">
              Could not load deletion information. Close this dialog and try again.
            </Translate>
          </Alert>
        ) : null}

        {previewState === 'ready' && preview ? <DeletionSummary preview={preview} /> : null}

        <section className="mb-3" aria-labelledby="financial-account-delete-preserves-heading">
          <h3 id="financial-account-delete-preserves-heading" className="h6 mb-2">
            <Translate contentKey="fintrackApp.financialAccount.delete.preserves.title">Will be preserved</Translate>
          </h3>
          <p className="text-muted mb-0">
            <Translate contentKey="fintrackApp.financialAccount.delete.preserves.copy">
              Your categories and tags are not deleted. Budgets, subscriptions, and rules are preserved; anything requiring your attention
              before deletion appears below.
            </Translate>
          </p>
        </section>

        {previewState === 'ready' ? <AccountDeletionBlockers blockers={blockers} /> : null}

        {deleteError ? (
          <Alert color="danger" fade={false} data-cy="financialAccountDeleteError" data-testid="financialAccountDeleteError" role="alert">
            <Translate contentKey="fintrackApp.financialAccount.delete.failed">This account could not be deleted. Try again.</Translate>
          </Alert>
        ) : null}

        <section className="border-top pt-3" aria-labelledby="financial-account-delete-deactivate-heading">
          <h3 id="financial-account-delete-deactivate-heading" className="h6 mb-1">
            <Translate contentKey="fintrackApp.financialAccount.delete.deactivate.title">Only want to stop using it?</Translate>
          </h3>
          <p className="text-muted small mb-2">
            <Translate contentKey="fintrackApp.financialAccount.delete.deactivate.copy">
              You can deactivate the account and keep all of its history.
            </Translate>
          </p>
          <Button
            color="secondary"
            outline
            size="sm"
            onClick={handleDeactivateInstead}
            disabled={deleting}
            data-cy="financialAccountDeactivateInstead"
            data-testid="financialAccountDeactivateInstead"
          >
            <Translate contentKey="fintrackApp.financialAccount.delete.deactivate.action">Deactivate instead</Translate>
          </Button>
        </section>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose} disabled={deleting}>
          <FontAwesomeIcon icon="ban" /> <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button
          id="jhi-confirm-delete-financialAccount"
          data-cy="entityConfirmDeleteButton"
          color="danger"
          onClick={confirmDelete}
          disabled={!canHardDelete || deleting}
          aria-describedby={canHardDelete ? undefined : 'financial-account-delete-state'}
        >
          <FontAwesomeIcon icon="trash" />{' '}
          <Translate contentKey={deleting ? 'fintrackApp.financialAccount.delete.deleting' : 'fintrackApp.financialAccount.delete.action'}>
            {deleting ? 'Deleting...' : 'Permanently delete'}
          </Translate>
        </Button>
      </ModalFooter>
      {!canHardDelete ? (
        <span id="financial-account-delete-state" className="visually-hidden">
          <Translate contentKey="fintrackApp.financialAccount.delete.disabledExplanation">
            Permanent deletion is available only after deletion information has loaded and blockers are resolved.
          </Translate>
        </span>
      ) : null}
    </Modal>
  );
};

export default FinancialAccountDeleteDialog;
