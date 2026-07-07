import React from 'react';
import { Route } from 'react-router';

import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';

import TransactionRuleCondition from './transaction-rule-condition';
import TransactionRuleConditionDetail from './transaction-rule-condition-detail';
import TransactionRuleConditionUpdate from './transaction-rule-condition-update';
import TransactionRuleConditionDeleteDialog from './transaction-rule-condition-delete-dialog';

const TransactionRuleConditionRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<TransactionRuleCondition />} />
    <Route path="new" element={<TransactionRuleConditionUpdate />} />
    <Route path=":id">
      <Route index element={<TransactionRuleConditionDetail />} />
      <Route path="edit" element={<TransactionRuleConditionUpdate />} />
      <Route path="delete" element={<TransactionRuleConditionDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default TransactionRuleConditionRoutes;
