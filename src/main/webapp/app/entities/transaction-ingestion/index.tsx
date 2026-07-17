import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import TransactionIngestion from './transaction-ingestion';
import TransactionIngestionDetail from './transaction-ingestion-detail';
import TransactionIngestionUpdate from './transaction-ingestion-update';
import TransactionIngestionDeleteDialog from './transaction-ingestion-delete-dialog';
import TransactionIngestionFilePreview from './transaction-ingestion-file-preview';

const TransactionIngestionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<TransactionIngestion />} />
    <Route path="new" element={<TransactionIngestionUpdate />} />
    <Route path="file-preview/new" element={<TransactionIngestionFilePreview />} />
    <Route path=":id">
      <Route index element={<TransactionIngestionDetail />} />
      <Route path="edit" element={<TransactionIngestionUpdate />} />
      <Route path="delete" element={<TransactionIngestionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default TransactionIngestionRoutes;
