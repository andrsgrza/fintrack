import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import FinancialAccount from './financial-account';
import CreditAccountDetails from './credit-account-details';
import FinancialTransaction from './financial-transaction';
import Category from './category';
import Tag from './tag';
import TransactionRule from './transaction-rule';
import TransactionRuleCondition from './transaction-rule-condition';
import FinancialSubscription from './financial-subscription';
import Budget from './budget';
import InternalTransfer from './internal-transfer';
import TransactionIngestion from './transaction-ingestion';
import FileIngestion from './file-ingestion';
import ApiIngestion from './api-ingestion';
import IngestionRecord from './ingestion-record';
import ApiAccessToken from './api-access-token';
import ApiAccessTokenPermission from './api-access-token-permission';
import UserDashboardPreference from './user-dashboard-preference';
/* jhipster-needle-add-route-import - JHipster will add routes here */

export default () => {
  return (
    <div>
      <ErrorBoundaryRoutes>
        {/* prettier-ignore */}
        <Route path="financial-account/*" element={<FinancialAccount />} />
        <Route path="credit-account-details/*" element={<CreditAccountDetails />} />
        <Route path="financial-transaction/*" element={<FinancialTransaction />} />
        <Route path="category/*" element={<Category />} />
        <Route path="tag/*" element={<Tag />} />
        <Route path="transaction-rule/*" element={<TransactionRule />} />
        <Route path="transaction-rule-condition/*" element={<TransactionRuleCondition />} />
        <Route path="financial-subscription/*" element={<FinancialSubscription />} />
        <Route path="budget/*" element={<Budget />} />
        <Route path="internal-transfer/*" element={<InternalTransfer />} />
        <Route path="transaction-ingestion/*" element={<TransactionIngestion />} />
        <Route path="file-ingestion/*" element={<FileIngestion />} />
        <Route path="api-ingestion/*" element={<ApiIngestion />} />
        <Route path="ingestion-record/*" element={<IngestionRecord />} />
        <Route path="api-access-token/*" element={<ApiAccessToken />} />
        <Route path="api-access-token-permission/*" element={<ApiAccessTokenPermission />} />
        <Route path="user-dashboard-preference/*" element={<UserDashboardPreference />} />
        {/* jhipster-needle-add-route-path - JHipster will add routes here */}
      </ErrorBoundaryRoutes>
    </div>
  );
};
