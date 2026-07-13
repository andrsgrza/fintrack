import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './budget.reducer';

export const BudgetDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const budgetEntity = useAppSelector(state => state.budget.entity);
  const updateSuccess = useAppSelector(state => state.budget.updateSuccess);

  const handleClose = () => {
    navigate('/budget');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = () => {
    dispatch(deleteEntity(budgetEntity.id));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="budgetDeleteDialogHeading">
        <Translate contentKey="fintrackApp.budget.delete.title">Delete budget?</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.budget.delete.message" data-cy="budgetDeleteMessage">
        <Translate contentKey="fintrackApp.budget.delete.message">
          This will delete the budget. Your accounts, categories, tags, transactions, and subscriptions will not be deleted. This budget
          will simply be removed from your reports and tracking. This action cannot be undone.
        </Translate>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button id="jhi-confirm-delete-budget" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default BudgetDeleteDialog;
