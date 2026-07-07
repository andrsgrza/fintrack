import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import FileIngestion from './file-ingestion';
import FileIngestionDetail from './file-ingestion-detail';
import FileIngestionUpdate from './file-ingestion-update';
import FileIngestionDeleteDialog from './file-ingestion-delete-dialog';

const FileIngestionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<FileIngestion />} />
    <Route path="new" element={<FileIngestionUpdate />} />
    <Route path=":id">
      <Route index element={<FileIngestionDetail />} />
      <Route path="edit" element={<FileIngestionUpdate />} />
      <Route path="delete" element={<FileIngestionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default FileIngestionRoutes;
