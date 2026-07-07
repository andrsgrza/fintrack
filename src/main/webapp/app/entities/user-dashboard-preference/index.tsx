import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import UserDashboardPreference from './user-dashboard-preference';
import UserDashboardPreferenceDetail from './user-dashboard-preference-detail';
import UserDashboardPreferenceUpdate from './user-dashboard-preference-update';
import UserDashboardPreferenceDeleteDialog from './user-dashboard-preference-delete-dialog';

const UserDashboardPreferenceRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<UserDashboardPreference />} />
    <Route path="new" element={<UserDashboardPreferenceUpdate />} />
    <Route path=":id">
      <Route index element={<UserDashboardPreferenceDetail />} />
      <Route path="edit" element={<UserDashboardPreferenceUpdate />} />
      <Route path="delete" element={<UserDashboardPreferenceDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default UserDashboardPreferenceRoutes;
