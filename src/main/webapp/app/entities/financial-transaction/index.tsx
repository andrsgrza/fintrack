import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import FinancialTransaction from './financial-transaction';
import FinancialTransactionDetail from './financial-transaction-detail';
import FinancialTransactionUpdate from './financial-transaction-update';
import FinancialTransactionDeleteDialog from './financial-transaction-delete-dialog';

const FinancialTransactionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<FinancialTransaction />} />
    <Route path="new" element={<FinancialTransactionUpdate />} />
    <Route path=":id">
      <Route index element={<FinancialTransactionDetail />} />
      <Route path="edit" element={<FinancialTransactionUpdate />} />
      <Route path="delete" element={<FinancialTransactionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default FinancialTransactionRoutes;
