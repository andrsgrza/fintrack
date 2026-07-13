import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './transaction-rule-condition.reducer';

export const TransactionRuleConditionDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const transactionRuleConditionEntity = useAppSelector(state => state.transactionRuleCondition.entity);
  const updateSuccess = useAppSelector(state => state.transactionRuleCondition.updateSuccess);

  const handleClose = () => {
    navigate('/transaction-rule-condition');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = () => {
    dispatch(deleteEntity(transactionRuleConditionEntity.id));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="transactionRuleConditionDeleteDialogHeading">
        <Translate contentKey="fintrackApp.transactionRuleCondition.delete.title">Delete rule condition?</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.transactionRuleCondition.delete.message" data-cy="transactionRuleConditionDeleteMessage">
        <Translate contentKey="fintrackApp.transactionRuleCondition.delete.message">
          This will delete this rule condition. The rule itself will not be deleted. If this was the last condition, the rule will be
          disabled to prevent it from matching every transaction. This action cannot be undone.
        </Translate>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button id="jhi-confirm-delete-transactionRuleCondition" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default TransactionRuleConditionDeleteDialog;
