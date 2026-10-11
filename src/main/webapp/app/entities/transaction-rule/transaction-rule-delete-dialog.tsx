import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader, Spinner } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './transaction-rule.reducer';

export const TransactionRuleDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [deleteError, setDeleteError] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
  }, []);

  const transactionRuleEntity = useAppSelector(state => state.transactionRule.entity);
  const handleClose = () => {
    navigate('/transaction-rule');
  };

  const confirmDelete = () => {
    if (!transactionRuleEntity.id || deleting) return;
    setDeleteError(false);
    setDeleting(true);
    void dispatch(deleteEntity(transactionRuleEntity.id))
      .unwrap()
      .then(() => {
        handleClose();
      })
      .catch(() => setDeleteError(true))
      .finally(() => setDeleting(false));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="transactionRuleDeleteDialogHeading">
        <Translate contentKey="entity.delete.title">Confirm delete operation</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.transactionRule.delete.question">
        <p>
          <Translate contentKey="fintrackApp.transactionRule.delete.question" interpolate={{ id: transactionRuleEntity.id }}>
            Are you sure you want to delete this TransactionRule?
          </Translate>
        </p>
        {deleteError ? (
          <Alert
            color="danger"
            fade={false}
            className="mb-0"
            role="alert"
            data-cy="transactionRuleDeleteError"
            data-testid="transactionRuleDeleteError"
          >
            <Translate contentKey="fintrackApp.transactionRule.delete.failed">The rule could not be deleted. Try again.</Translate>
          </Alert>
        ) : null}
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose} disabled={deleting}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button
          id="jhi-confirm-delete-transactionRule"
          data-cy="entityConfirmDeleteButton"
          data-testid="entityConfirmDeleteButton"
          color="danger"
          onClick={confirmDelete}
          disabled={deleting}
        >
          {deleting ? <Spinner size="sm" className="me-2" /> : <FontAwesomeIcon icon="trash" className="me-2" />}
          <Translate contentKey={deleting ? 'entity.action.deleting' : 'entity.action.delete'}>
            {deleting ? 'Deleting…' : 'Delete'}
          </Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default TransactionRuleDeleteDialog;
