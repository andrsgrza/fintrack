import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import ApiAccessToken from './api-access-token';
import ApiAccessTokenDetail from './api-access-token-detail';
import ApiAccessTokenUpdate from './api-access-token-update';
import ApiAccessTokenDeleteDialog from './api-access-token-delete-dialog';

const ApiAccessTokenRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<ApiAccessToken />} />
    <Route path="new" element={<ApiAccessTokenUpdate />} />
    <Route path=":id">
      <Route index element={<ApiAccessTokenDetail />} />
      <Route path="edit" element={<ApiAccessTokenUpdate />} />
      <Route path="delete" element={<ApiAccessTokenDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default ApiAccessTokenRoutes;
