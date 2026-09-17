import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import TechnicalWriteUnavailable from 'app/entities/technical-write-unavailable';

import TransactionIngestion from './transaction-ingestion';
import TransactionIngestionUpdate from './transaction-ingestion-update';
import TransactionIngestionDeleteDialog from './transaction-ingestion-delete-dialog';
import TransactionIngestionWorkflowDetail from './transaction-ingestion-workflow-detail';

const TransactionIngestionWriteUnavailable = () => (
  <TechnicalWriteUnavailable
    backTo="/transaction-ingestion"
    dataCyPrefix="transactionIngestion"
    technicalViewContentKey="fintrackApp.transactionIngestion.technicalView"
    technicalViewDefault="Transaction Ingestion is managed by workflow commands."
    titleContentKey="fintrackApp.transactionIngestion.writeUnavailableTitle"
    titleDefault="Transaction Ingestion technical edit unavailable"
    messageContentKey="fintrackApp.transactionIngestion.writeUnavailable"
    messageDefault="Generated Transaction Ingestion edit is not a product action. Use the ingestion workflow instead."
  />
);

const TransactionIngestionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<TransactionIngestion />} />
    <Route path="new" element={<TransactionIngestionUpdate />} />
    <Route path=":id">
      <Route index element={<TransactionIngestionWorkflowDetail />} />
      <Route path="edit" element={<TransactionIngestionWriteUnavailable />} />
      <Route path="delete" element={<TransactionIngestionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default TransactionIngestionRoutes;
