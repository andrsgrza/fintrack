import React from 'react';
import { Route } from 'react-router';
import ErrorBoundaryRoutes from 'app/shared/error/error-boundary-routes';
import DescriptionNormalizationRule from './description-normalization-rule';
import DescriptionNormalizationRuleDetail from './description-normalization-rule-detail';
import DescriptionNormalizationRuleUpdate from './description-normalization-rule-update';
import DescriptionNormalizationRuleDeleteDialog from './description-normalization-rule-delete-dialog';

const DescriptionNormalizationRuleRoutes = () => (
  <ErrorBoundaryRoutes>
    <Route index element={<DescriptionNormalizationRule />} />
    <Route path="new" element={<DescriptionNormalizationRuleUpdate />} />
    <Route path=":id">
      <Route index element={<DescriptionNormalizationRuleDetail />} />
      <Route path="edit" element={<DescriptionNormalizationRuleUpdate />} />
      <Route path="delete" element={<DescriptionNormalizationRuleDeleteDialog />} />
    </Route>
  </ErrorBoundaryRoutes>
);

export default DescriptionNormalizationRuleRoutes;
