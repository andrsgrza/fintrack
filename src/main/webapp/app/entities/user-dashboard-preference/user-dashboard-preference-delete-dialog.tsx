import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './user-dashboard-preference.reducer';

export const UserDashboardPreferenceDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const userDashboardPreferenceEntity = useAppSelector(state => state.userDashboardPreference.entity);
  const updateSuccess = useAppSelector(state => state.userDashboardPreference.updateSuccess);

  const handleClose = () => {
    navigate('/user-dashboard-preference');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = () => {
    dispatch(deleteEntity(userDashboardPreferenceEntity.id));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="userDashboardPreferenceDeleteDialogHeading">
        <Translate contentKey="fintrackApp.userDashboardPreference.delete.title">Reset dashboard preferences?</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.userDashboardPreference.delete.message" data-cy="userDashboardPreferenceDeleteMessage">
        <Translate contentKey="fintrackApp.userDashboardPreference.delete.message">
          This will reset your dashboard preferences. Your dashboard layout and saved display settings will be deleted. Your accounts,
          transactions, categories, tags, budgets, and subscriptions will not be affected. You can configure your dashboard again later.
        </Translate>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button id="jhi-confirm-delete-userDashboardPreference" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default UserDashboardPreferenceDeleteDialog;
