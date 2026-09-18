import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import axios from 'axios';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader } from 'reactstrap';
import { Translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { deleteEntity, getEntity } from './category.reducer';

export const CategoryDeleteDialog = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<'id'>();

  const [loadModal, setLoadModal] = useState(false);
  const [childCount, setChildCount] = useState<number | null>(null);
  const [deleteErrorKey, setDeleteErrorKey] = useState<string | null>(null);

  useEffect(() => {
    dispatch(getEntity(id));
    setLoadModal(true);
  }, []);

  useEffect(() => {
    if (id) {
      axios.get<number>(`api/categories/count?parentCategoryId.equals=${id}`).then(response => {
        setChildCount(response.data);
      });
    }
  }, [id]);

  const categoryEntity = useAppSelector(state => state.category.entity);
  const updateSuccess = useAppSelector(state => state.category.updateSuccess);
  const hasChildren = (childCount ?? 0) > 0;

  const handleClose = () => {
    navigate('/category');
  };

  useEffect(() => {
    if (updateSuccess && loadModal) {
      handleClose();
      setLoadModal(false);
    }
  }, [updateSuccess]);

  const confirmDelete = async () => {
    if (!categoryEntity.id || hasChildren) {
      return;
    }

    setDeleteErrorKey(null);
    const result = await dispatch(deleteEntity(categoryEntity.id));
    if (result.type.endsWith('/rejected')) {
      const error = (result as { error?: { message?: string; response?: { data?: { detail?: string } } } }).error;
      const detail = `${error?.response?.data?.detail ?? ''} ${error?.message ?? ''}`.toLowerCase();
      setDeleteErrorKey(
        detail.includes('child categories')
          ? 'fintrackApp.category.delete.blockedMessage'
          : detail.includes('transaction candidates')
            ? 'fintrackApp.category.delete.candidateBlockedMessage'
            : 'fintrackApp.category.delete.failed',
      );
    }
  };

  return (
    <Modal isOpen toggle={handleClose}>
      <ModalHeader toggle={handleClose} data-cy="categoryDeleteDialogHeading">
        <Translate contentKey="fintrackApp.category.delete.title">Delete category?</Translate>
      </ModalHeader>
      <ModalBody id="fintrackApp.category.delete.question">
        <p>
          <Translate contentKey="fintrackApp.category.delete.question" interpolate={{ categoryName: categoryEntity.name }}>
            Are you sure you want to delete this category?
          </Translate>
        </p>
        {hasChildren ? (
          <Alert
            color="warning"
            fade={false}
            className="mb-0"
            data-cy="categoryDeleteBlockedMessage"
            data-testid="categoryDeleteBlockedMessage"
          >
            <Translate contentKey="fintrackApp.category.delete.blockedMessage">
              This category has subcategories. Delete or move those subcategories before trying again.
            </Translate>
          </Alert>
        ) : (
          <p className="mb-0" data-cy="categoryDeleteLeafMessage" data-testid="categoryDeleteLeafMessage">
            <Translate contentKey="fintrackApp.category.delete.leafMessage">
              This action cannot be undone. Deletion may be blocked while this category is used by an active workflow.
            </Translate>
          </p>
        )}
        {deleteErrorKey ? (
          <Alert color="danger" fade={false} className="mt-3 mb-0" data-cy="categoryDeleteError" data-testid="categoryDeleteError">
            <Translate contentKey={deleteErrorKey}>
              This category could not be deleted. Resolve the remaining references and try again.
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
        {!hasChildren && childCount !== null && (
          <Button id="jhi-confirm-delete-category" data-cy="entityConfirmDeleteButton" color="danger" onClick={confirmDelete}>
            <FontAwesomeIcon icon="trash" />
            &nbsp;
            <Translate contentKey="entity.action.delete">Delete</Translate>
          </Button>
        )}
      </ModalFooter>
    </Modal>
  );
};

export default CategoryDeleteDialog;
