import financialAccount from 'app/entities/financial-account/financial-account.reducer';
import creditAccountDetails from 'app/entities/credit-account-details/credit-account-details.reducer';
import financialTransaction from 'app/entities/financial-transaction/financial-transaction.reducer';
import category from 'app/entities/category/category.reducer';
import tag from 'app/entities/tag/tag.reducer';
import transactionRule from 'app/entities/transaction-rule/transaction-rule.reducer';
import transactionRuleCondition from 'app/entities/transaction-rule-condition/transaction-rule-condition.reducer';
import financialSubscription from 'app/entities/financial-subscription/financial-subscription.reducer';
import budget from 'app/entities/budget/budget.reducer';
import internalTransfer from 'app/entities/internal-transfer/internal-transfer.reducer';
import transactionIngestion from 'app/entities/transaction-ingestion/transaction-ingestion.reducer';
import fileIngestion from 'app/entities/file-ingestion/file-ingestion.reducer';
import apiIngestion from 'app/entities/api-ingestion/api-ingestion.reducer';
import ingestionRecord from 'app/entities/ingestion-record/ingestion-record.reducer';
import apiAccessToken from 'app/entities/api-access-token/api-access-token.reducer';
import apiAccessTokenPermission from 'app/entities/api-access-token-permission/api-access-token-permission.reducer';
import userDashboardPreference from 'app/entities/user-dashboard-preference/user-dashboard-preference.reducer';
/* jhipster-needle-add-reducer-import - JHipster will add reducer here */

const entitiesReducers = {
  financialAccount,
  creditAccountDetails,
  financialTransaction,
  category,
  tag,
  transactionRule,
  transactionRuleCondition,
  financialSubscription,
  budget,
  internalTransfer,
  transactionIngestion,
  fileIngestion,
  apiIngestion,
  ingestionRecord,
  apiAccessToken,
  apiAccessTokenPermission,
  userDashboardPreference,
  /* jhipster-needle-add-reducer-combine - JHipster will add reducer here */
};

export default entitiesReducers;
