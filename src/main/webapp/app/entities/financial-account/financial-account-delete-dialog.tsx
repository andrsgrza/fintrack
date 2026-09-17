import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './financial-account.reducer';

export const FinancialAccountDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);
  const [deleteErrorKey, setDeleteErrorKey] = useState<string | null>(null);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const financialAccountEntity = useAppSelector(state => state.financialAccount.entity);
  const updateSuccess = useAppSelector(state => state.financialAccount.updateSuccess);

  const handleClose = () => {
    navigate('/financial-account');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = async () => {
    if (!financialAccountEntity.id) {
      return;
    }

    setDeleteErrorKey(null);
    const result = await dispatch(deleteEntity(financialAccountEntity.id));
    if (deleteEntity.rejected.match(result)) {
      const status = (result.error as { response?: { status?: number } }).response?.status;
      setDeleteErrorKey(
        status === 400 ? 'fintrackApp.financialAccount.delete.protectedReferences' : 'fintrackApp.financialAccount.delete.failed',
      );
    }
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="financialAccountDeleteDialogHeading">
        <Translate contentKey="fintrackApp.financialAccount.delete.title">Delete account</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.financialAccount.delete.question">
        <p>
          <Translate contentKey="fintrackApp.financialAccount.delete.question" interpolate={{ accountName: financialAccountEntity.name }}>
            Are you sure you want to permanently delete this account?
          </Translate>
        </p>
        <p className="mb-0">
          <Translate contentKey="fintrackApp.financialAccount.delete.consequences">
            Related imported or workflow data is removed only when it can be cleaned up safely. Deletion can be blocked by protected
            transaction data.
          </Translate>
        </p>
        {deleteErrorKey ? (
          <Alert
            color="danger"
            fade={false}
            className="mt-3 mb-0"
            data-cy="financialAccountDeleteError"
            data-testid="financialAccountDeleteError"
          >
            <Translate contentKey={deleteErrorKey}>
              This account could not be deleted. It may still be referenced by protected transaction data. Resolve those references and try
              again.
            </Translate>
          </Alert>
        ) : null}
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button id="jhi-confirm-delete-financialAccount" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default FinancialAccountDeleteDialog;
