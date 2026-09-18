import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './tag.reducer';

const tagDeleteErrorKey = (result: unknown) => {
  const error = (result as { error?: { message?: string; response?: { data?: { detail?: string } } } }).error;
  const detail = `${error?.response?.data?.detail ?? ''} ${error?.message ?? ''}`.toLowerCase();
  return detail.includes('transaction candidates') ? 'fintrackApp.tag.delete.candidateBlockedMessage' : 'fintrackApp.tag.delete.failed';
};

export const TagDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const [loadModal, setLoadModal] = useState(false);
  const [deleteErrorKey, setDeleteErrorKey] = useState<string | null>(null);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  const tagEntity = useAppSelector(state => state.tag.entity);
  const updateSuccess = useAppSelector(state => state.tag.updateSuccess);

  const handleClose = () => {
    navigate('/tag');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = async () => {
    if (!tagEntity.id) {
      return;
    }
    setDeleteErrorKey(null);
    const result = await dispatch(deleteEntity(tagEntity.id));
    if (result.type.endsWith('/rejected')) {
      setDeleteErrorKey(tagDeleteErrorKey(result));
    }
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="tagDeleteDialogHeading">
        <Translate contentKey="fintrackApp.tag.delete.title">Delete tag?</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.tag.delete.question" data-cy="tagDeleteMessage">
        <p>
          <Translate contentKey="fintrackApp.tag.delete.question" interpolate={{ tagName: tagEntity.name }}>
            Are you sure you want to permanently delete this tag?
          </Translate>
        </p>
        <p className="mb-0" data-cy="tagDeleteLeafMessage" data-testid="tagDeleteLeafMessage">
          <Translate contentKey="fintrackApp.tag.delete.leafMessage">
            This action cannot be undone. Deletion may be blocked while this tag is still used by an active workflow.
          </Translate>
        </p>
        {deleteErrorKey ? (
          <Alert color="danger" fade={false} className="mt-3 mb-0" data-cy="tagDeleteError" data-testid="tagDeleteError">
            <Translate contentKey={deleteErrorKey}>
              This tag could not be deleted. Resolve the remaining references and try again.
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
        <Button id="jhi-confirm-delete-tag" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
          <FontAwesomeIcon icon="trash" />
          &nbsp;
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default TagDeleteDialog;
