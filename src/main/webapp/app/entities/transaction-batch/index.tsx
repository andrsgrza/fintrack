import React from 'react';
import { Route } from 'react-router';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import TransactionBatch from './transaction-batch';
import TransactionBatchNew from './transaction-batch-new';
import TransactionBatchDetail from './transaction-batch-detail';

const TransactionBatchRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<TransactionBatch />} />
    <Route path="new" element={<TransactionBatchNew />} />
    <Route path=":id" element={<TransactionBatchDetail />} />
  </ErrorBoundaryRoutes>
);

export default TransactionBatchRoutes;
