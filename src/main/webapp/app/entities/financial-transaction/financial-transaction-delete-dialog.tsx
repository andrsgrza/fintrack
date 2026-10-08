import React, { useMemo, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import axios from 'axios';

const deleteFinancialTransaction = (id: string | number) => axios.delete<void>(`api/financial-transactions/${id}`);

export const FinancialTransactionDeleteDialog = () => {
  const pageLocation = useLocation();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const [deleting, setDeleting] = useState(false);
  const [deleteFailed, setDeleteFailed] = useState(false);

  const returnPath = useMemo(() => `/financial-transaction${pageLocation.search}`, [pageLocation.search]);
  const handleClose = () => {
    navigate(returnPath);
  };

  const confirmDelete = async () => {
    if (!id || deleting) {
      return;
    }

    setDeleteFailed(false);
    setDeleting(true);
    try {
      await deleteFinancialTransaction(id);
      navigate(returnPath, { replace: true });
    } catch {
      setDeleteFailed(true);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal isOpen toggle={handleClose} contentClassName="shadow" labelledBy="financial-transaction-delete-title">
      <ModalHeader
        id="financial-transaction-delete-title"
        toggle={handleClose}
        data-cy="financialTransactionDeleteDialogHeading"
        data-testid="financialTransactionDeleteDialogHeading"
      >
        <Translate contentKey="fintrackApp.financialTransaction.product.deleteTitle">Delete transaction</Translate>
      </ModalHeader>
      <ModalBody aria-busy={deleting}>
        <p className="mb-0" data-cy="financialTransactionDeleteConfirmation" data-testid="financialTransactionDeleteConfirmation">
          <Translate contentKey="fintrackApp.financialTransaction.delete.question">
            Are you sure you want to delete this transaction?
          </Translate>
        </p>
        {deleteFailed ? (
          <Alert
            color="danger"
            fade={false}
            className="mt-3 mb-0"
            role="alert"
            data-cy="financialTransactionDeleteError"
            data-testid="financialTransactionDeleteError"
          >
            <Translate contentKey="fintrackApp.financialTransaction.delete.failed">
              We could not delete this transaction. Try again.
            </Translate>
          </Alert>
        ) : null}
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose} disabled={deleting}>
          <FontAwesomeIcon icon="ban" /> <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button
          id="jhi-confirm-delete-financialTransaction"
          data-cy="entityConfirmDeleteButton"
          color="danger"
          onClick={confirmDelete}
          disabled={!id || deleting}
        >
          <FontAwesomeIcon icon="trash" />{' '}
          <Translate
            key={deleting ? 'deleting' : 'delete'}
            contentKey={deleting ? 'fintrackApp.financialTransaction.delete.deleting' : 'entity.action.delete'}
          >
            {deleting ? 'Deleting...' : 'Delete'}
          </Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default FinancialTransactionDeleteDialog;
