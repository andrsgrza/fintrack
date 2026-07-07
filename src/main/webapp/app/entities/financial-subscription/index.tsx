import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import FinancialSubscription from './financial-subscription';
import FinancialSubscriptionDetail from './financial-subscription-detail';
import FinancialSubscriptionUpdate from './financial-subscription-update';
import FinancialSubscriptionDeleteDialog from './financial-subscription-delete-dialog';

const FinancialSubscriptionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<FinancialSubscription />} />
    <Route path="new" element={<FinancialSubscriptionUpdate />} />
    <Route path=":id">
      <Route index element={<FinancialSubscriptionDetail />} />
      <Route path="edit" element={<FinancialSubscriptionUpdate />} />
      <Route path="delete" element={<FinancialSubscriptionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default FinancialSubscriptionRoutes;
