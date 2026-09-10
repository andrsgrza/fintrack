import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import TechnicalWriteUnavailable from 'app/entities/technical-write-unavailable';

import IngestionRecord from './ingestion-record';
import IngestionRecordDetail from './ingestion-record-detail';

const IngestionRecordWriteUnavailable = () => (
  <TechnicalWriteUnavailable
    backTo="/ingestion-record"
    dataCyPrefix="ingestionRecord"
    technicalViewContentKey="fintrackApp.ingestionRecord.technicalView"
    technicalViewDefault="Technical view — ingestion rows are managed from the Transaction Ingestion workflow."
    titleContentKey="fintrackApp.ingestionRecord.writeUnavailableTitle"
    titleDefault="Ingestion Record technical view"
    messageContentKey="fintrackApp.ingestionRecord.writeUnavailable"
    messageDefault="Ingestion Record create, edit, and delete are not product actions. Use the Transaction Ingestion workflow row review instead."
  />
);

const IngestionRecordRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<IngestionRecord />} />
    <Route path="new" element={<IngestionRecordWriteUnavailable />} />
    <Route path=":id">
      <Route index element={<IngestionRecordDetail />} />
      <Route path="edit" element={<IngestionRecordWriteUnavailable />} />
      <Route path="delete" element={<IngestionRecordWriteUnavailable />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default IngestionRecordRoutes;
