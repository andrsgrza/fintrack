import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import ApiIngestion from './api-ingestion';
import ApiIngestionDetail from './api-ingestion-detail';
import ApiIngestionUpdate from './api-ingestion-update';
import ApiIngestionDeleteDialog from './api-ingestion-delete-dialog';

const ApiIngestionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<ApiIngestion />} />
    <Route path="new" element={<ApiIngestionUpdate />} />
    <Route path=":id">
      <Route index element={<ApiIngestionDetail />} />
      <Route path="edit" element={<ApiIngestionUpdate />} />
      <Route path="delete" element={<ApiIngestionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default ApiIngestionRoutes;
