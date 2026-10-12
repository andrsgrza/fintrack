import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import Rules from './rules';

const RulesRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<Rules />} />
  </ErrorBoundaryRoutes>
);

export default RulesRoutes;
