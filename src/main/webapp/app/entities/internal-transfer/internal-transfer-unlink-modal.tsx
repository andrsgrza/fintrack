import React, { useState } from 'react';
import { Alert, Button, Modal, ModalBody, ModalFooter, ModalHeader, Spinner } from 'reactstrap';
import { Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { InternalTransferRead, internalTransferErrorKey, unlinkInternalTransfer } from './internal-transfer-product.service';

type Props = {
  transfer: InternalTransferRead | null;
  onClose: () => void;
  onUnlinked: () => void;
};

export const InternalTransferUnlinkModal = ({ transfer, onClose, onUnlinked }: Props) => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const unlink = async () => {
    if (!transfer) return;
    setSubmitting(true);
    setError(null);
    try {
      await unlinkInternalTransfer(transfer.transferId, transfer.version);
      onUnlinked();
    } catch (requestError) {
      setError(internalTransferErrorKey(requestError));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal isOpen={Boolean(transfer)} toggle={submitting ? undefined : onClose} centered aria-labelledby="internal-transfer-unlink-title">
      <ModalHeader id="internal-transfer-unlink-title" toggle={submitting ? undefined : onClose}>
        <Translate contentKey="fintrackApp.internalTransfer.product.unlink.title">Unlink transfer</Translate>
      </ModalHeader>
      <ModalBody>
        <p className="mb-0">
          <Translate contentKey="fintrackApp.internalTransfer.product.unlink.body">
            The two transactions will be kept and will no longer be linked as an internal transfer.
          </Translate>
        </p>
        {error ? (
          <Alert color="danger" className="mt-3 mb-0" role="alert" data-cy="internalTransferUnlinkError">
            {translate(`fintrackApp.internalTransfer.product.errors.${error}`)}
          </Alert>
        ) : null}
      </ModalBody>
      <ModalFooter>
        <Button color="secondary" outline onClick={onClose} disabled={submitting}>
          <Translate contentKey="entity.action.cancel">Cancel</Translate>
        </Button>
        <Button color="danger" onClick={unlink} disabled={submitting} data-cy="confirmUnlinkInternalTransfer">
          {submitting ? <Spinner size="sm" className="me-2" /> : <FontAwesomeIcon icon="asterisk" className="me-2" />}
          <Translate contentKey="fintrackApp.internalTransfer.product.unlink.action">Unlink</Translate>
        </Button>
      </ModalFooter>
    </Modal>
  );
};

export default InternalTransferUnlinkModal;
