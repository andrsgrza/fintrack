import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import ApiAccessTokenPermission from './api-access-token-permission';
import ApiAccessTokenPermissionDetail from './api-access-token-permission-detail';
import ApiAccessTokenPermissionUpdate from './api-access-token-permission-update';
import ApiAccessTokenPermissionDeleteDialog from './api-access-token-permission-delete-dialog';

const ApiAccessTokenPermissionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<ApiAccessTokenPermission />} />
    <Route path="new" element={<ApiAccessTokenPermissionUpdate />} />
    <Route path=":id">
      <Route index element={<ApiAccessTokenPermissionDetail />} />
      <Route path="edit" element={<ApiAccessTokenPermissionUpdate />} />
      <Route path="delete" element={<ApiAccessTokenPermissionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default ApiAccessTokenPermissionRoutes;
