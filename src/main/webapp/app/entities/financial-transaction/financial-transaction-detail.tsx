import React, { useEffect } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { Button, Col, DropdownItem, Row } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { getEntity } from './financial-transaction.reducer';
import { TransactionAccountLabel, TransactionAmount, TransactionClassification, TransactionFlowBadge } from './transaction-presentation';

const DetailValue = ({ label, children }: { label: React.ReactNode; children: React.ReactNode }) => (
  <div>
    <div className="text-muted small mb-1">{label}</div>
    <div className="fw-semibold text-break">{children}</div>
  </div>
);

const originLabelKey = (origin?: string | null) => {
  switch (origin) {
    case 'MANUAL':
      return 'fintrackApp.financialTransaction.product.origins.manual';
    case 'FILE_IMPORT':
      return 'fintrackApp.financialTransaction.product.origins.fileImport';
    case 'API':
      return 'fintrackApp.financialTransaction.product.origins.api';
    default:
      return '';
  }
};

export const FinancialTransactionDetail = () => {
  const dispatch = useAppDispatch();
  const location = useLocation();
  const { id } = useParams<'id'>();
  const financialTransactionEntity = useAppSelector(state => state.financialTransaction.entity);
  const originKey = originLabelKey(financialTransactionEntity.origin);

  useEffect(() => {
    dispatch(getEntity(id));
  }, [dispatch, id]);

  const transactionContext = (
    <>
      <TransactionAccountLabel account={financialTransactionEntity.account} />
      <span className="text-muted">·</span>
      <TransactionFlowBadge flow={financialTransactionEntity.flow} />
      {financialTransactionEntity.transactionDate ? (
        <>
          <span className="text-muted">·</span>
          <span className="text-muted small">
            <TextFormat value={financialTransactionEntity.transactionDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
          </span>
        </>
      ) : null}
    </>
  );

  return (
    <ProductPage>
      <ProductPageHeader
        headingId="financial-transaction-details-heading"
        dataCy="financialTransactionDetailsHeading"
        accentColor={financialTransactionEntity.account?.color}
        accentDataCy="financialTransactionDetailColorAccent"
        title={
          financialTransactionEntity.description ?? (
            <Translate contentKey="fintrackApp.financialTransaction.detail.title">Transaction</Translate>
          )
        }
        metadata={transactionContext}
        actions={
          <>
            <TransactionAmount
              amount={financialTransactionEntity.amount}
              currency={financialTransactionEntity.account?.currency}
              flow={financialTransactionEntity.flow}
              compact
            />
            <Button
              tag={Link}
              to={`/financial-transaction${location.search}`}
              replace
              color="secondary"
              outline
              size="sm"
              data-cy="entityDetailsBackButton"
            >
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
            </Button>
            {financialTransactionEntity.id ? (
              <Button
                tag={Link}
                to={`/financial-transaction/${financialTransactionEntity.id}/edit${location.search}`}
                replace
                color="primary"
                size="sm"
                data-cy="entityEditButton"
              >
                <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
              </Button>
            ) : null}
            {financialTransactionEntity.id ? (
              <ProductActionsMenu
                label={translate('fintrackApp.financialTransaction.product.moreActions')}
                dataCy="financialTransactionDetailActionsMenu"
              >
                <DropdownItem
                  tag={Link}
                  to={`/financial-transaction/${financialTransactionEntity.id}/delete${location.search}`}
                  className="text-danger"
                  data-cy="entityDeleteButton"
                >
                  <FontAwesomeIcon icon="trash" className="me-2" />
                  <Translate contentKey="entity.action.delete">Delete</Translate>
                </DropdownItem>
              </ProductActionsMenu>
            ) : null}
          </>
        }
      />

      <div className="vstack gap-3">
        <ProductSection
          title={<Translate contentKey="fintrackApp.financialTransaction.product.classification">Classification</Translate>}
          dataCy="financialTransactionDetailClassification"
        >
          <TransactionClassification category={financialTransactionEntity.category} tags={financialTransactionEntity.tags} showEmptyTags />
        </ProductSection>

        <ProductSection
          title={<Translate contentKey="fintrackApp.financialTransaction.product.details">Details</Translate>}
          dataCy="financialTransactionDetailMetadata"
        >
          <Row className="g-3">
            {financialTransactionEntity.transactionDate ? (
              <Col sm="6" md="4">
                <DetailValue label={<Translate contentKey="fintrackApp.financialTransaction.transactionDate">Transaction date</Translate>}>
                  <TextFormat value={financialTransactionEntity.transactionDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
                </DetailValue>
              </Col>
            ) : null}
            {financialTransactionEntity.postingDate ? (
              <Col sm="6" md="4">
                <DetailValue label={<Translate contentKey="fintrackApp.financialTransaction.postingDate">Posting date</Translate>}>
                  <TextFormat value={financialTransactionEntity.postingDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
                </DetailValue>
              </Col>
            ) : null}
            {financialTransactionEntity.account ? (
              <Col sm="6" md="4">
                <DetailValue label={<Translate contentKey="fintrackApp.financialTransaction.account">Account</Translate>}>
                  <TransactionAccountLabel account={financialTransactionEntity.account} />
                </DetailValue>
              </Col>
            ) : null}
            {financialTransactionEntity.externalReference ? (
              <Col sm="6" md="4">
                <DetailValue
                  label={<Translate contentKey="fintrackApp.financialTransaction.externalReference">External reference</Translate>}
                >
                  {financialTransactionEntity.externalReference}
                </DetailValue>
              </Col>
            ) : null}
            {originKey ? (
              <Col sm="6" md="4">
                <DetailValue label={<Translate contentKey="fintrackApp.financialTransaction.origin">Origin</Translate>}>
                  <Translate contentKey={originKey} />
                </DetailValue>
              </Col>
            ) : null}
          </Row>
        </ProductSection>

        {financialTransactionEntity.notes ? (
          <ProductSection
            title={<Translate contentKey="fintrackApp.financialTransaction.notes">Notes</Translate>}
            dataCy="financialTransactionDetailNotes"
          >
            <p className="mb-0 text-break">{financialTransactionEntity.notes}</p>
          </ProductSection>
        ) : null}
      </div>
    </ProductPage>
  );
};

export default FinancialTransactionDetail;
