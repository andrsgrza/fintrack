import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import IngestionRecord from './ingestion-record';
import IngestionRecordDetail from './ingestion-record-detail';
import IngestionRecordUpdate from './ingestion-record-update';
import IngestionRecordDeleteDialog from './ingestion-record-delete-dialog';

const IngestionRecordRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<IngestionRecord />} />
    <Route path="new" element={<IngestionRecordUpdate />} />
    <Route path=":id">
      <Route index element={<IngestionRecordDetail />} />
      <Route path="edit" element={<IngestionRecordUpdate />} />
      <Route path="delete" element={<IngestionRecordDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default IngestionRecordRoutes;
