import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './financial-subscription.reducer';

export const FinancialSubscriptionDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const financialSubscriptionEntity = useAppSelector(state => state.financialSubscription.entity);
  const updateSuccess = useAppSelector(state => state.financialSubscription.updateSuccess);

  const handleClose = () => {
    navigate('/financial-subscription');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = () => {
    dispatch(deleteEntity(financialSubscriptionEntity.id));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="financialSubscriptionDeleteDialogHeading">
        <Translate contentKey="fintrackApp.financialSubscription.delete.title">Delete subscription?</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.financialSubscription.delete.message" data-cy="financialSubscriptionDeleteMessage">
        <Translate contentKey="fintrackApp.financialSubscription.delete.message">
          This will delete the subscription. Transactions that were linked to this subscription will not be deleted. They will simply no
          longer be associated with it. Any rules that use this subscription as their target will be disabled. This action cannot be undone.
        </Translate>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button id="jhi-confirm-delete-financialSubscription" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default FinancialSubscriptionDeleteDialog;
