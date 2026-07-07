import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import InternalTransfer from './internal-transfer';
import InternalTransferDetail from './internal-transfer-detail';
import InternalTransferUpdate from './internal-transfer-update';
import InternalTransferDeleteDialog from './internal-transfer-delete-dialog';

const InternalTransferRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<InternalTransfer />} />
    <Route path="new" element={<InternalTransferUpdate />} />
    <Route path=":id">
      <Route index element={<InternalTransferDetail />} />
      <Route path="edit" element={<InternalTransferUpdate />} />
      <Route path="delete" element={<InternalTransferDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default InternalTransferRoutes;
