import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import CreditAccountDetails from './credit-account-details';
import CreditAccountDetailsDetail from './credit-account-details-detail';
import CreditAccountDetailsUpdate from './credit-account-details-update';
import CreditAccountDetailsDeleteDialog from './credit-account-details-delete-dialog';

const CreditAccountDetailsRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<CreditAccountDetails />} />
    <Route path="new" element={<CreditAccountDetailsUpdate />} />
    <Route path=":id">
      <Route index element={<CreditAccountDetailsDetail />} />
      <Route path="edit" element={<CreditAccountDetailsUpdate />} />
      <Route path="delete" element={<CreditAccountDetailsDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default CreditAccountDetailsRoutes;
