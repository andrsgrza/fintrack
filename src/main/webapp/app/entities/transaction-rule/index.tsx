import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import TransactionRule from './transaction-rule';
import TransactionRuleDetail from './transaction-rule-detail';
import TransactionRuleUpdate from './transaction-rule-update';
import TransactionRuleDeleteDialog from './transaction-rule-delete-dialog';

const TransactionRuleRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<TransactionRule />} />
    <Route path="new" element={<TransactionRuleUpdate />} />
    <Route path=":id">
      <Route index element={<TransactionRuleDetail />} />
      <Route path="edit" element={<TransactionRuleUpdate />} />
      <Route path="delete" element={<TransactionRuleDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default TransactionRuleRoutes;
