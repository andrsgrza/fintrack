import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import ApiIngestion from './api-ingestion';
import ApiIngestionDetail from './api-ingestion-detail';
import ApiIngestionWriteUnavailable from './api-ingestion-write-unavailable';

const ApiIngestionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<ApiIngestion />} />
    <Route path="new" element={<ApiIngestionWriteUnavailable />} />
    <Route path=":id">
      <Route index element={<ApiIngestionDetail />} />
      <Route path="edit" element={<ApiIngestionWriteUnavailable />} />
      <Route path="delete" element={<ApiIngestionWriteUnavailable />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default ApiIngestionRoutes;
