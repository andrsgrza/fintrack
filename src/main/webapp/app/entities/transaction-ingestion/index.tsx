import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import TransactionIngestion from './transaction-ingestion';
import TransactionIngestionUpdate from './transaction-ingestion-update';
import TransactionIngestionDeleteDialog from './transaction-ingestion-delete-dialog';
import TransactionIngestionWorkflowDetail from './transaction-ingestion-workflow-detail';

const TransactionIngestionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<TransactionIngestion />} />
    <Route path="new" element={<TransactionIngestionUpdate />} />
    <Route path=":id">
      <Route index element={<TransactionIngestionWorkflowDetail />} />
      <Route path="edit" element={<TransactionIngestionUpdate />} />
      <Route path="delete" element={<TransactionIngestionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default TransactionIngestionRoutes;
