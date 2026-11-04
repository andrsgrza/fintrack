import React from 'react';
import { Route } from 'react-router';
import { Navigate } from 'react-router-dom';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import FinancialAccount from './financial-account';
import CreditAccountDetails from './credit-account-details';
import FinancialTransaction from './financial-transaction';
import Category from './category';
import Tag from './tag';
import Rules from './rules';
import TransactionRule from './transaction-rule';
import TransactionRuleCondition from './transaction-rule-condition';
import DescriptionNormalizationRule from './description-normalization-rule';
import FinancialSubscription from './financial-subscription';
import Budget from './budget';
import InternalTransfer from './internal-transfer';
import ApiIngestion from './api-ingestion';
import ApiAccessToken from './api-access-token';
import ApiAccessTokenPermission from './api-access-token-permission';
import TransactionBatch from './transaction-batch';
import AnalyticsExplorer from './analytics-explorer/analytics-explorer';
import PersonalDashboardGallery from './personal-dashboards/personal-dashboard-gallery';
import PersonalDashboardViewer from './personal-dashboards/personal-dashboard-viewer';
import RecommendedDashboards from './recommended-dashboards/recommended-dashboards';
/* jhipster-needle-add-route-import - JHipster will add routes here */

export default () => {
  return (
    <div>
      <ErrorBoundaryRoutes>
        {/* prettier-ignore */}
        <Route path="financial-account/*" element={<FinancialAccount />} />
        <Route path="credit-account-details/*" element={<CreditAccountDetails />} />
        <Route path="financial-transaction/*" element={<FinancialTransaction />} />
        <Route path="transaction-batches/*" element={<TransactionBatch />} />
        <Route path="analytics/explorer" element={<AnalyticsExplorer />} />
        <Route path="analytics/dashboards" element={<PersonalDashboardGallery />} />
        <Route path="analytics/dashboards/personal/:dashboardId" element={<PersonalDashboardViewer />} />
        <Route path="analytics/dashboards/:dashboardId" element={<RecommendedDashboards />} />
        <Route path="category/*" element={<Category />} />
        <Route path="tag/*" element={<Tag />} />
        <Route path="rules/*" element={<Rules />} />
        <Route path="transaction-rule/*" element={<TransactionRule />} />
        <Route path="transaction-rule-condition/*" element={<TransactionRuleCondition />} />
        <Route path="description-normalization-rule/*" element={<DescriptionNormalizationRule />} />
        <Route path="financial-subscription/*" element={<FinancialSubscription />} />
        <Route path="budget/*" element={<Budget />} />
        <Route path="internal-transfers/*" element={<InternalTransfer />} />
        <Route path="internal-transfer/*" element={<Navigate to="/internal-transfers" replace />} />
        <Route path="api-ingestion/*" element={<ApiIngestion />} />
        <Route path="api-access-token/*" element={<ApiAccessToken />} />
        <Route path="api-access-token-permission/*" element={<ApiAccessTokenPermission />} />
        {/* jhipster-needle-add-route-path - JHipster will add routes here */}
      </ErrorBoundaryRoutes>
    </div>
  );
};
