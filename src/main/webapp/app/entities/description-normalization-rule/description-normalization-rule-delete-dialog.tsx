import React, { useEffect, useState } from 'react';
import axios from 'axios';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader, Spinner } from 'reactstrap';
import { toast } from 'react-toastify';

import { IDescriptionNormalizationRuleConfigured } from 'app/shared/model/description-normalization-rule-configured.model';
import { getConfiguredDescriptionNormalizationRule } from './description-normalization-rule-configured.service';

export const DescriptionNormalizationRuleDeleteDialog = () => {
  const navigate = useNavigate();
  const { id } = useParams<'id'>();
  const [rule, setRule] = useState<IDescriptionNormalizationRuleConfigured | null>(null);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [deleteError, setDeleteError] = useState(false);

  useEffect(() => {
    if (!id) {
      setLoading(false);
      setLoadError(true);
      return undefined;
    }
    let active = true;
    setLoading(true);
    getConfiguredDescriptionNormalizationRule(id)
      .then(response => {
        if (active) setRule(response.data);
      })
      .catch(() => {
        if (active) setLoadError(true);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [id]);

  const handleClose = () => navigate(id ? `/description-normalization-rule/${id}` : '/description-normalization-rule');

  const confirmDelete = async () => {
    if (!id || deleting || loadError) return;
    setDeleting(true);
    setDeleteError(false);
    try {
      await axios.delete(`api/description-normalization-rules/${id}`);
      toast.success(translate('fintrackApp.descriptionNormalizationRule.deleted', { param: rule?.name ?? id }));
      navigate('/description-normalization-rule', { replace: true });
    } catch {
      setDeleteError(true);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Modal isOpen toggle={handleClose} contentClassName="shadow" labelledBy="description-normalization-rule-delete-title">
      <ModalHeader id="description-normalization-rule-delete-title" toggle={handleClose}>
        <Translate contentKey="fintrackApp.descriptionNormalizationRule.delete.title">Delete description rule</Translate>
      </ModalHeader>
      <ModalBody aria-busy={loading}>
        {loading ? (
          <p className="text-muted mb-0" role="status">
            <Spinner tag="span" size="sm" className="me-2" /> <Translate contentKey="global.messages.info.loading">Loading...</Translate>
          </p>
        ) : null}
        {loadError ? (
          <Alert color="danger" fade={false} className="mb-0" role="alert">
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.delete.loadFailed">
              This rule could not be loaded, so it cannot be deleted safely. Close this dialog and try again.
            </Translate>
          </Alert>
        ) : null}
        {!loading && !loadError ? (
          <>
            <p className="fw-semibold mb-2">{rule?.name}</p>
            <Alert color="warning" fade={false} className="mb-3">
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.delete.warning">
                Deleting this rule removes it from future normalization evaluations. It does not change posted transactions or automatically
                reprocess reviewed imports.
              </Translate>
            </Alert>
            <p className="mb-0 text-muted" data-cy="descriptionNormalizationRuleDeletePrecedence">
              <Translate contentKey="fintrackApp.descriptionNormalizationRule.delete.precedence">
                Removing a rule can change which remaining rule wins for future imported descriptions.
              </Translate>
            </p>
          </>
        ) : null}
        {deleteError ? (
          <Alert
            color="danger"
            fade={false}
            className="mt-3 mb-0"
            role="alert"
            data-cy="descriptionNormalizationRuleDeleteError"
            data-testid="descriptionNormalizationRuleDeleteError"
          >
            <Translate contentKey="fintrackApp.descriptionNormalizationRule.delete.failed">
              The rule could not be deleted. No visible rule order was changed; try again.
            </Translate>
          </Alert>
        ) : null}
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" onClick={handleClose} disabled={deleting}>
          <FontAwesomeIcon icon="ban" /> <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button
          id="jhi-confirm-delete-descriptionNormalizationRule"
          data-cy="entityConfirmDeleteButton"
          data-testid="entityConfirmDeleteButton"
          color="danger"
          onClick={() => void confirmDelete()}
          disabled={loading || loadError || deleting}
        >
          {deleting ? <Spinner size="sm" className="me-2" /> : <FontAwesomeIcon icon="trash" className="me-2" />}
          <Translate contentKey="entity.action.delete">Delete</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default DescriptionNormalizationRuleDeleteDialog;
