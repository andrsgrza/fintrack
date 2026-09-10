import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import TechnicalWriteUnavailable from 'app/entities/technical-write-unavailable';

import FileIngestion from './file-ingestion';
import FileIngestionDetail from './file-ingestion-detail';

const FileIngestionWriteUnavailable = () => (
  <TechnicalWriteUnavailable
    backTo="/file-ingestion"
    dataCyPrefix="fileIngestion"
    technicalViewContentKey="fintrackApp.fileIngestion.technicalView"
    technicalViewDefault="Technical view — File metadata is managed by the Transaction Ingestion workflow."
    titleContentKey="fintrackApp.fileIngestion.writeUnavailableTitle"
    titleDefault="File Ingestion technical view"
    messageContentKey="fintrackApp.fileIngestion.writeUnavailable"
    messageDefault="File Ingestion create, edit, and delete are not product actions. File metadata is created and managed by the Transaction Ingestion workflow."
  />
);

const FileIngestionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<FileIngestion />} />
    <Route path="new" element={<FileIngestionWriteUnavailable />} />
    <Route path=":id">
      <Route index element={<FileIngestionDetail />} />
      <Route path="edit" element={<FileIngestionWriteUnavailable />} />
      <Route path="delete" element={<FileIngestionWriteUnavailable />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default FileIngestionRoutes;
