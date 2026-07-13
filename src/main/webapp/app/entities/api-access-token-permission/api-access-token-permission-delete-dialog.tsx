import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './api-access-token-permission.reducer';

export const ApiAccessTokenPermissionDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const apiAccessTokenPermissionEntity = useAppSelector(state => state.apiAccessTokenPermission.entity);
  const updateSuccess = useAppSelector(state => state.apiAccessTokenPermission.updateSuccess);

  const handleClose = () => {
    navigate('/api-access-token-permission');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = () => {
    dispatch(deleteEntity(apiAccessTokenPermissionEntity.id));
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="apiAccessTokenPermissionDeleteDialogHeading">
        <Translate contentKey="fintrackApp.apiAccessTokenPermission.delete.title">Remove API token permission?</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.apiAccessTokenPermission.delete.message" data-cy="apiAccessTokenPermissionDeleteMessage">
        <Translate contentKey="fintrackApp.apiAccessTokenPermission.delete.message">
          This will remove this permission from the API token. The token will no longer be allowed to perform this action. The API token
          itself will not be deleted, and existing imported data or history will not be affected.
        </Translate>
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose}>
          <FontAwesomeIcon icon="ban" />
          &nbsp;
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button id="jhi-confirm-delete-apiAccessTokenPermission" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default ApiAccessTokenPermissionDeleteDialog;
