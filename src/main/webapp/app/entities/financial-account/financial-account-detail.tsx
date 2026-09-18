import React, { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Badge, Button, Col, DropdownItem, Row } from 'reactstrap';
import { TextFormat, Translate, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { APP_LOCAL_DATE_FORMAT } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { ProductActionsMenu, ProductPage, ProductPageHeader, ProductSection } from 'app/shared/ui/product-page';

import { getEntity } from './financial-account.reducer';
import {
  getEntityByAccountId as getCreditAccountDetailsByAccountId,
  reset as resetCreditAccountDetails,
} from 'app/entities/credit-account-details/credit-account-details.reducer';
import CreditCardDetailsViewSection from './components/credit-card-details-view-section';
import FinancialAccountBalanceSection from './components/financial-account-balance-section';
import AccountRecentTransactionsSection from './components/account-recent-transactions-section';

const DetailValue = ({ labelKey, fallback, children }: { labelKey: string; fallback: string; children: React.ReactNode }) => (
  <div>
    <div className="text-muted small mb-1">
      <Translate contentKey={labelKey}>{fallback}</Translate>
    </div>
    <div className="fw-semibold text-break">{children}</div>
  </div>
);

export const FinancialAccountDetail = () => {
  const dispatch = useAppDispatch();
  const { id } = useParams<'id'>();
  const financialAccountEntity = useAppSelector(state => state.financialAccount.entity);
  const creditAccountDetailsEntity = useAppSelector(state => state.creditAccountDetails.entity);
  const isCreditCard = financialAccountEntity.accountType === 'CREDIT_CARD';
  const isInactive = financialAccountEntity.active === false;

  useEffect(() => {
    dispatch(getEntity(id));
    dispatch(resetCreditAccountDetails());
  }, [dispatch, id]);

  useEffect(() => {
    if (financialAccountEntity.id && isCreditCard) {
      dispatch(getCreditAccountDetailsByAccountId(financialAccountEntity.id));
    }
  }, [dispatch, financialAccountEntity.id, isCreditCard]);

  return (
    <ProductPage wide>
      <ProductPageHeader
        headingId="financial-account-details-heading"
        dataCy="financialAccountDetailsHeading"
        accentColor={financialAccountEntity.color}
        accentDataCy="financialAccountDetailColorAccent"
        title={financialAccountEntity.name ?? <Translate contentKey="fintrackApp.financialAccount.detail.title">Account details</Translate>}
        subtitle={
          financialAccountEntity.id ? (
            <>
              {financialAccountEntity.institutionName ? `${financialAccountEntity.institutionName} · ` : null}
              {financialAccountEntity.accountType ? (
                <Translate contentKey={`fintrackApp.AccountType.${financialAccountEntity.accountType}`} />
              ) : null}
              {financialAccountEntity.currency ? ` · ${financialAccountEntity.currency}` : null}
              {financialAccountEntity.lastFourDigits ? ` · ••••${financialAccountEntity.lastFourDigits}` : null}
            </>
          ) : undefined
        }
        metadata={
          financialAccountEntity.id ? (
            <div data-cy="financialAccountDetailStatusSection" data-testid="financialAccountDetailStatusSection">
              <Badge
                color={isInactive ? 'secondary' : 'success'}
                pill
                data-cy="financialAccountStatus"
                data-testid="financialAccountStatus"
              >
                <Translate
                  contentKey={isInactive ? 'fintrackApp.financialAccount.status.inactive' : 'fintrackApp.financialAccount.status.active'}
                >
                  {isInactive ? 'Inactive' : 'Active'}
                </Translate>
              </Badge>
            </div>
          ) : null
        }
        actions={
          <>
            <Button tag={Link} to="/financial-account" replace color="secondary" outline size="sm" data-cy="entityDetailsBackButton">
              <FontAwesomeIcon icon="arrow-left" /> <Translate contentKey="entity.action.back">Back</Translate>
            </Button>
            {financialAccountEntity.id ? (
              <Button
                tag={Link}
                to={`/financial-account/${financialAccountEntity.id}/edit`}
                replace
                color="primary"
                size="sm"
                data-cy="entityEditButton"
              >
                <FontAwesomeIcon icon="pencil-alt" /> <Translate contentKey="entity.action.edit">Edit</Translate>
              </Button>
            ) : null}
            {financialAccountEntity.id ? (
              <ProductActionsMenu label={translate('fintrackApp.financialAccount.moreActions')} dataCy="financialAccountDetailActionsMenu">
                <DropdownItem
                  tag={Link}
                  to={`/financial-account/${financialAccountEntity.id}/delete`}
                  className="text-danger"
                  data-cy="entityDeleteButton"
                >
                  <Translate contentKey="entity.action.delete">Delete</Translate>
                </DropdownItem>
              </ProductActionsMenu>
            ) : null}
          </>
        }
      />

      {isInactive ? (
        <Alert
          color="secondary"
          fade={false}
          className="py-2 small"
          data-cy="financialAccountInactiveExplanation"
          data-testid="financialAccountInactiveExplanation"
        >
          <Translate contentKey="fintrackApp.financialAccount.status.inactiveExplanation">
            This account is inactive. Its history and balance remain available, and existing work can finish, but it cannot be selected for
            new transactions, imports, or rules until it is reactivated.
          </Translate>
        </Alert>
      ) : null}

      {financialAccountEntity.id ? (
        <div className="vstack gap-3">
          <ProductSection
            title={
              <Translate
                contentKey={
                  isCreditCard ? 'fintrackApp.financialAccount.detail.creditSummary' : 'fintrackApp.financialAccount.detail.balanceSummary'
                }
              >
                {isCreditCard ? 'Credit card summary' : 'Account balance'}
              </Translate>
            }
            dataCy="financialAccountDetailBalanceSection"
          >
            <FinancialAccountBalanceSection accountId={financialAccountEntity.id} />
            {isCreditCard ? <CreditCardDetailsViewSection details={creditAccountDetailsEntity} /> : null}
          </ProductSection>

          <ProductSection
            title={<Translate contentKey="fintrackApp.financialAccount.detail.generalInformation">General information</Translate>}
            dataCy="financialAccountDetailAccountSection"
          >
            <Row className="g-3">
              {financialAccountEntity.institutionName ? (
                <Col sm="6" lg="4">
                  <DetailValue labelKey="fintrackApp.financialAccount.institutionName" fallback="Institution">
                    {financialAccountEntity.institutionName}
                  </DetailValue>
                </Col>
              ) : null}
              <Col sm="6" lg="4">
                <DetailValue labelKey="fintrackApp.financialAccount.currency" fallback="Currency">
                  {financialAccountEntity.currency}
                </DetailValue>
              </Col>
              {financialAccountEntity.lastFourDigits ? (
                <Col sm="6" lg="4">
                  <DetailValue labelKey="fintrackApp.financialAccount.lastFourDigits" fallback="Last four digits">
                    {`••••${financialAccountEntity.lastFourDigits}`}
                  </DetailValue>
                </Col>
              ) : null}
              <Col sm="6" lg="4">
                <DetailValue labelKey="fintrackApp.financialAccount.initialBalanceDate" fallback="Tracking start date">
                  {financialAccountEntity.initialBalanceDate ? (
                    <TextFormat value={financialAccountEntity.initialBalanceDate} type="date" format={APP_LOCAL_DATE_FORMAT} />
                  ) : (
                    '—'
                  )}
                </DetailValue>
              </Col>
              {financialAccountEntity.description ? (
                <Col xs="12">
                  <DetailValue labelKey="fintrackApp.financialAccount.description" fallback="Description">
                    {financialAccountEntity.description}
                  </DetailValue>
                </Col>
              ) : null}
            </Row>
          </ProductSection>

          <ProductSection
            title={<Translate contentKey="fintrackApp.financialAccount.recentTransactions">Recent transactions</Translate>}
            dataCy="financialAccountRecentTransactionsProductSection"
          >
            <AccountRecentTransactionsSection accountId={financialAccountEntity.id} currency={financialAccountEntity.currency} />
          </ProductSection>
        </div>
      ) : null}
    </ProductPage>
  );
};

export default FinancialAccountDetail;
