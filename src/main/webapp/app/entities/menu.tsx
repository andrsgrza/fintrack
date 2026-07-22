import React from 'react';
import { Translate } from 'react-jhipster';
import { Badge } from 'reactstrap';

import MenuItem from 'app/shared/layout/menus/menu-item';

const EntitiesMenu = () => {
  return (
    <>
      {/* prettier-ignore */}
      <MenuItem icon="asterisk" to="/financial-account">
        <Translate contentKey="global.menu.entities.financialAccount" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/credit-account-details">
        <Translate contentKey="global.menu.entities.creditAccountDetails" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/financial-transaction">
        <Translate contentKey="global.menu.entities.financialTransaction" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/category">
        <Translate contentKey="global.menu.entities.category" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/tag">
        <Translate contentKey="global.menu.entities.tag" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/transaction-rule">
        <Translate contentKey="global.menu.entities.transactionRule" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/transaction-rule-condition">
        <Translate contentKey="global.menu.entities.transactionRuleCondition" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/financial-subscription">
        <Translate contentKey="global.menu.entities.financialSubscription" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/budget">
        <Translate contentKey="global.menu.entities.budget" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/internal-transfer">
        <Translate contentKey="global.menu.entities.internalTransfer" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/transaction-ingestion">
        <Translate contentKey="global.menu.entities.transactionIngestion" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/file-ingestion">
        <Translate contentKey="global.menu.entities.fileIngestion" />{' '}
        <Badge color="secondary" pill>
          <Translate contentKey="global.menu.entities.technical">Technical</Translate>
        </Badge>
      </MenuItem>
      <MenuItem icon="asterisk" to="/api-ingestion">
        <Translate contentKey="global.menu.entities.apiIngestion" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/ingestion-record">
        <Translate contentKey="global.menu.entities.ingestionRecord" />{' '}
        <Badge color="secondary" pill>
          <Translate contentKey="global.menu.entities.technical">Technical</Translate>
        </Badge>
      </MenuItem>
      <MenuItem icon="asterisk" to="/api-access-token">
        <Translate contentKey="global.menu.entities.apiAccessToken" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/api-access-token-permission">
        <Translate contentKey="global.menu.entities.apiAccessTokenPermission" />
      </MenuItem>
      <MenuItem icon="asterisk" to="/user-dashboard-preference">
        <Translate contentKey="global.menu.entities.userDashboardPreference" />
      </MenuItem>
      {/* jhipster-needle-add-entity-to-menu - JHipster will add entities to the menu here */}
    </>
  );
};

export default EntitiesMenu;
