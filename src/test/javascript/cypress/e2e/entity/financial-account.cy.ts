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

describe('FinancialAccount e2e test', () => {
  const financialAccountPageUrl = '/financial-account';
  const financialAccountPageUrlPattern = new RegExp('/financial-account(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const adminUsername = Cypress.env('E2E_ADMIN_USERNAME') ?? 'admin';
  const adminPassword = Cypress.env('E2E_ADMIN_PASSWORD') ?? 'admin';

  let financialAccount;

  const buildFinancialAccountPayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      institutionName: 'E2E Bank',
      accountType: 'DEBIT',
      currency: 'MXN',
      initialBalance: 1000,
      initialBalanceDate: '2026-07-08',
      active: true,
      createdAt: now,
      updatedAt: now,
    };
  };

  const fillCreateForm = (name: string) => {
    cy.get('[data-cy="name"]').clear().type(name);
    cy.get('[data-cy="institutionName"]').clear().type('E2E Bank');
    cy.get('[data-cy="accountType"]').select('DEBIT');
    cy.get('[data-cy="currency"]').select('MXN');
    cy.get('[data-cy="initialBalance"]').clear().type('1000');
    cy.get('[data-cy="initialBalanceDate"]').type('2026-07-08');
    cy.get('[data-cy="active"]').check();
    cy.get('[data-cy="createdAt"]').type('2026-07-08T10:00');
    cy.get('[data-cy="updatedAt"]').type('2026-07-08T10:00');
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-accounts+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/financial-accounts').as('postEntityRequest');
    cy.intercept('DELETE', '/api/financial-accounts/*').as('deleteEntityRequest');
  });

  afterEach(() => {
    if (financialAccount) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${financialAccount.id}`,
      }).then(() => {
        financialAccount = undefined;
      });
    }
  });

  it('FinancialAccounts menu should load FinancialAccounts page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('financial-account');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('FinancialAccount').should('exist');
    cy.url().should('match', financialAccountPageUrlPattern);
  });

  describe('FinancialAccount page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(financialAccountPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create FinancialAccount page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/financial-account/new$'));
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      beforeEach(function () {
        const accountName = `existing-${Date.now()}`;
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-accounts',
          body: buildFinancialAccountPayload(accountName),
        }).then(({ body }) => {
          financialAccount = body;
        });

        cy.visit(financialAccountPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('detail button click should load details FinancialAccount page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('financialAccount');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      it('edit button click should load edit FinancialAccount page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      it('edit button click should load edit FinancialAccount page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      it('last delete button click should delete instance of FinancialAccount', () => {
        cy.intercept('GET', '/api/financial-accounts/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('financialAccount').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);

        financialAccount = undefined;
      });
    });
  });

  describe('new FinancialAccount page', () => {
    beforeEach(() => {
      cy.visit(`${financialAccountPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('FinancialAccount');
    });

    it('should create an instance of FinancialAccount', () => {
      const accountName = `create-${Date.now()}`;
      fillCreateForm(accountName);
      cy.get('[data-cy="user"]').should('not.exist');
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.user.login).to.equal(username);
        financialAccount = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', financialAccountPageUrlPattern);
    });
  });

  describe('FinancialAccount ownership', () => {
    it('should not render user selector on create form', () => {
      cy.visit(`${financialAccountPageUrl}/new`);
      cy.get('[data-cy="user"]').should('not.exist');
    });

    it('regular user should not see accounts created by admin', () => {
      const adminAccountName = `admin-only-${Date.now()}`;

      cy.login(adminUsername, adminPassword);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: buildFinancialAccountPayload(adminAccountName),
      }).then(({ body: adminAccount }) => {
        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/financial-accounts',
        }).then(({ body: userAccounts }) => {
          expect(userAccounts.some(account => account.id === adminAccount.id)).to.equal(false);
        });

        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/financial-accounts/${adminAccount.id}`,
        });
      });
    });

    it('admin should see accounts created by another user', () => {
      const userAccountName = `user-owned-${Date.now()}`;

      cy.login(username, password);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: buildFinancialAccountPayload(userAccountName),
      }).then(({ body: userAccount }) => {
        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/financial-accounts',
        }).then(({ body: adminAccounts }) => {
          expect(adminAccounts.some(account => account.id === userAccount.id)).to.equal(true);
        });

        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/financial-accounts/${userAccount.id}`,
        });
      });
    });
  });
});
