import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Alert, Button, Col, Row, Spinner } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_DATE_FORMAT, APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { FinancialAmount } from 'app/shared/ui/financial-amount';
import { ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { InternalTransferRead, getInternalTransfer, internalTransferErrorKey } from './internal-transfer-product.service';
import { TransferLegDetail } from './internal-transfer-presentation';
import InternalTransferUnlinkModal from './internal-transfer-unlink-modal';

export const InternalTransferDetail = () => {
  const { id } = useParams<'id'>();
  const navigate = useNavigate();
  const [transfer, setTransfer] = useState<InternalTransferRead | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [unlinkOpen, setUnlinkOpen] = useState(false);

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    setError(null);
    getInternalTransfer(id)
      .then(setTransfer)
      .catch(requestError => setError(internalTransferErrorKey(requestError)))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="text-center py-5">
        <Spinner />
        <span className="ms-2">
          <Translate contentKey="global.messages.info.loading">Loading...</Translate>
        </span>
      </div>
    );
  }

  if (!transfer) {
    return (
      <ProductPage>
        <Alert color="danger" role="alert">
          {translate(`fintrackApp.internalTransfer.product.errors.${error ?? 'notFound'}`)}
        </Alert>
        <Button tag={Link} to="/internal-transfers" color="secondary" outline>
          <Translate contentKey="entity.action.back">Back</Translate>
        </Button>
      </ProductPage>
    );
  }

  const historicalDateMismatch = Boolean(
    transfer.source.transactionDate &&
      transfer.destination.transactionDate &&
      transfer.source.transactionDate !== transfer.destination.transactionDate,
  );

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="internal-transfer-details-heading"
        dataCy="internalTransferDetailsHeading"
        title={<Translate contentKey="fintrackApp.internalTransfer.product.detailTitle">Internal transfer</Translate>}
        metadata={
          <span className="text-muted small">
            <Translate contentKey="fintrackApp.internalTransfer.product.transactionDate">Transfer date</Translate>:{' '}
            {transfer.transactionDate ? <TextFormat value={transfer.transactionDate} type="date" format={APP_LOCAL_DATE_FORMAT} /> : '—'}
          </span>
        }
        actions={
          <>
            <FinancialAmount amount={transfer.amount} currency={transfer.currency} compact dataCy="internalTransferDetailAmount" />
            <Button tag={Link} to="/internal-transfers" color="secondary" outline size="sm">
              <FontAwesomeIcon icon="arrow-left" className="me-1" />
              <Translate contentKey="entity.action.back">Back</Translate>
            </Button>
            <Button
              tag={Link}
              to={`/internal-transfers/${transfer.transferId}/edit`}
              color="primary"
              size="sm"
              data-cy="internalTransferDetailEdit"
            >
              <FontAwesomeIcon icon="pencil-alt" className="me-1" />
              <Translate contentKey="entity.action.edit">Edit</Translate>
            </Button>
            <Button color="danger" outline size="sm" onClick={() => setUnlinkOpen(true)} data-cy="internalTransferDetailUnlink">
              <FontAwesomeIcon icon="asterisk" className="me-1" />
              <Translate contentKey="fintrackApp.internalTransfer.product.unlink.action">Unlink</Translate>
            </Button>
          </>
        }
      />

      {historicalDateMismatch ? (
        <Alert color="warning">
          <Translate contentKey="fintrackApp.internalTransfer.product.historicalDateMismatch">
            This historical transfer has different dates in its movements.
          </Translate>
        </Alert>
      ) : null}

      <Row className="g-3">
        <Col md="6">
          <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.source">From</Translate>}>
            <TransferLegDetail leg={transfer.source} role="source" />
          </ProductSection>
        </Col>
        <Col md="6">
          <ProductSection title={<Translate contentKey="fintrackApp.internalTransfer.product.destination">To</Translate>}>
            <TransferLegDetail leg={transfer.destination} role="destination" />
          </ProductSection>
        </Col>
      </Row>

      {transfer.notes || transfer.createdAt ? (
        <ProductSection
          title={<Translate contentKey="fintrackApp.internalTransfer.product.transferMetadata">Transfer details</Translate>}
          className="mt-3"
        >
          {transfer.notes ? <p className="text-break mb-3">{transfer.notes}</p> : null}
          {transfer.createdAt ? (
            <div className="small text-muted">
              <Translate contentKey="fintrackApp.internalTransfer.product.createdAt">Created</Translate>:{' '}
              <TextFormat value={transfer.createdAt} type="date" format={APP_DATE_FORMAT} />
            </div>
          ) : null}
        </ProductSection>
      ) : null}

      <p className="small text-muted mt-3 mb-0">
        <Translate contentKey="fintrackApp.internalTransfer.product.deleteLegNote">
          If you delete one transaction, the other one remains as a normal transaction.
        </Translate>
      </p>
      <InternalTransferUnlinkModal
        transfer={unlinkOpen ? transfer : null}
        onClose={() => setUnlinkOpen(false)}
        onUnlinked={() => navigate('/internal-transfers', { replace: true })}
      />
    </ProductPage>
  );
};

export default InternalTransferDetail;
