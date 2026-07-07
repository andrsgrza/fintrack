import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import FinancialAccount from './financial-account';
import FinancialAccountDetail from './financial-account-detail';
import FinancialAccountUpdate from './financial-account-update';
import FinancialAccountDeleteDialog from './financial-account-delete-dialog';

const FinancialAccountRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<FinancialAccount />} />
    <Route path="new" element={<FinancialAccountUpdate />} />
    <Route path=":id">
      <Route index element={<FinancialAccountDetail />} />
      <Route path="edit" element={<FinancialAccountUpdate />} />
      <Route path="delete" element={<FinancialAccountDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default FinancialAccountRoutes;
