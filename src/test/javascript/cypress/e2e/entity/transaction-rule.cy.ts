import {
  entityConfirmDeleteButtonSelector,
  entityCreateButtonSelector,
  entityCreateCancelButtonSelector,
  entityCreateSaveButtonSelector,
  entityDeleteButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('TransactionRule e2e test', () => {
  const transactionRulePageUrl = '/transaction-rule';
  const transactionRulePageUrlPattern = new RegExp('/transaction-rule(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const adminUsername = Cypress.env('E2E_ADMIN_USERNAME') ?? 'admin';
  const adminPassword = Cypress.env('E2E_ADMIN_PASSWORD') ?? 'admin';

  let transactionRule;

  const buildTransactionRulePayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      priority: 1,
      conditionLogic: 'ALL',
      active: true,
      createdAt: now,
      updatedAt: now,
    };
  };

  const fillCreateForm = (name: string) => {
    cy.get('[data-cy="name"]').clear().type(name);
    cy.get('[data-cy="priority"]').clear().type('1');
    cy.get('[data-cy="conditionLogic"]').select('ALL');
    cy.get('[data-cy="createdAt"]').type('2026-07-08T10:00');
    cy.get('[data-cy="updatedAt"]').type('2026-07-08T10:00');
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/transaction-rules+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/transaction-rules').as('postEntityRequest');
    cy.intercept('DELETE', '/api/transaction-rules/*').as('deleteEntityRequest');
  });

  afterEach(() => {
    if (transactionRule) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-rules/${transactionRule.id}`,
      }).then(() => {
        transactionRule = undefined;
      });
    }
  });

  it('TransactionRules menu should load TransactionRules page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('transaction-rule');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('TransactionRule').should('exist');
    cy.url().should('match', transactionRulePageUrlPattern);
  });

  describe('TransactionRule page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(transactionRulePageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create TransactionRule page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/transaction-rule/new$'));
        cy.getEntityCreateUpdateHeading('TransactionRule');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/transaction-rules',
          body: buildTransactionRulePayload(`existing-${Date.now()}`),
        }).then(({ body }) => {
          transactionRule = body;
        });

        cy.visit(transactionRulePageUrl);
        cy.wait('@entitiesRequest');
      });

      it('detail button click should load details TransactionRule page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('transactionRule');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });

      it('edit button click should load edit TransactionRule page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionRule');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });

      it('edit button click should load edit TransactionRule page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionRule');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);
      });

      it('last delete button click should delete instance of TransactionRule', () => {
        cy.intercept('GET', '/api/transaction-rules/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('transactionRule').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionRulePageUrlPattern);

        transactionRule = undefined;
      });
    });
  });

  describe('new TransactionRule page', () => {
    beforeEach(() => {
      cy.visit(`${transactionRulePageUrl}/new`);
      cy.getEntityCreateUpdateHeading('TransactionRule');
    });

    it('should create an instance of TransactionRule', () => {
      const ruleName = `create-${Date.now()}`;
      fillCreateForm(ruleName);
      cy.get('[data-cy="user"]').should('not.exist');
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.user.login).to.equal(username);
        transactionRule = response.body;
      });
      cy.url().should('match', transactionRulePageUrlPattern);
    });
  });

  describe('TransactionRule ownership', () => {
    it('should not render user selector on create form', () => {
      cy.visit(`${transactionRulePageUrl}/new`);
      cy.get('[data-cy="user"]').should('not.exist');
    });

    it('regular user should not see transaction rules created by admin', () => {
      const adminRuleName = `admin-only-${Date.now()}`;

      cy.login(adminUsername, adminPassword);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-rules',
        body: buildTransactionRulePayload(adminRuleName),
      }).then(({ body: adminRule }) => {
        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/transaction-rules',
        }).then(({ body: userRules }) => {
          expect(userRules.some(r => r.id === adminRule.id)).to.equal(false);
        });

        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/transaction-rules/${adminRule.id}`,
        });
      });
    });

    it('admin should see transaction rules created by another user', () => {
      const userRuleName = `user-owned-${Date.now()}`;

      cy.login(username, password);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-rules',
        body: buildTransactionRulePayload(userRuleName),
      }).then(({ body: userRule }) => {
        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/transaction-rules',
        }).then(({ body: adminRules }) => {
          expect(adminRules.some(r => r.id === userRule.id)).to.equal(true);
        });

        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/transaction-rules/${userRule.id}`,
        });
      });
    });
  });
});
