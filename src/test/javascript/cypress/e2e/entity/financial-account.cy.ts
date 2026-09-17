import {
  entityConfirmDeleteButtonSelector,
  entityCreateButtonSelector,
  entityCreateCancelButtonSelector,
  entityCreateSaveButtonSelector,
  entityDeleteButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
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
  };

  const fillCreditCardForm = (name: string) => {
    fillCreateForm(name);
    cy.get('[data-cy="accountType"]').select('CREDIT_CARD');
    cy.get('[data-cy="initialBalance"]').clear().type('1000');
    cy.get('[data-cy="initialBalanceDate"]').type('2026-07-08');
    cy.get('[data-cy="creditCardCreditLimit"]').clear().type('5000');
    cy.get('[data-cy="creditCardStatementDay"]').clear().type('15');
    cy.get('[data-cy="creditCardPaymentDueDay"]').clear().type('5');
    cy.get('[data-cy="creditCardAnnualInterestRate"]').clear().type('65');
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-accounts/overview').as('overviewRequest');
    cy.intercept('POST', '/api/financial-accounts/configured').as('postConfiguredRequest');
    cy.intercept('PUT', '/api/financial-accounts/*/configured').as('putConfiguredRequest');
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
    cy.get('[data-cy="entity"]').click();
    cy.get('[data-cy="entity"]').find('[href="/credit-account-details"]').should('not.exist');
    cy.get('[data-cy="entity"]').find('[href="/financial-account"]').click();
    cy.wait('@overviewRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get('[data-cy="financialAccountOverviewCard"]').should('not.exist');
      } else {
        cy.get('[data-cy="financialAccountOverviewCard"]').should('exist');
      }
    });
    cy.getEntityHeading('FinancialAccount').should('exist');
    cy.url().should('match', financialAccountPageUrlPattern);
  });

  describe('FinancialAccount page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(financialAccountPageUrl);
        cy.wait('@overviewRequest');
      });

      it('should load create FinancialAccount page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/financial-account/new$'));
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@overviewRequest').then(({ response }) => {
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
          expect(body.active).to.equal(true);
          financialAccount = body;
        });

        cy.visit(financialAccountPageUrl);
        cy.wait('@overviewRequest');
      });

      it('detail button click should load details FinancialAccount page', () => {
        cy.contains('[data-cy="financialAccountOverviewCard"]', financialAccount.name).within(() => {
          cy.get(entityDetailsButtonSelector).click();
        });
        cy.getEntityDetailsHeading('financialAccount');
        cy.get('[data-cy="financialAccountDetailAccountSection"]').should('contain', financialAccount.name).and('contain', 'MXN');
        cy.get('[data-cy="financialAccountStatus"]').should('exist').and('not.be.empty');
        cy.get('[data-cy="financialAccountBalanceSection"]').should('contain', '1000');
        cy.get('[data-cy="financialAccountDetailAccountSection"]').find('#id, #createdAt, #updatedAt').should('not.exist');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@overviewRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      it('renders the product overview card without the generated CRUD table', () => {
        cy.contains('[data-cy="financialAccountOverviewCard"]', financialAccount.name).should('contain', 'MXN').and('contain', '1000');
        cy.get('[data-cy="financialAccountOverview"]').should('exist');
        cy.get('[data-cy="entityTable"]').should('not.exist');
      });

      it('edit button click should load edit FinancialAccount page and go back', () => {
        cy.contains('[data-cy="financialAccountOverviewCard"]', financialAccount.name).within(() => {
          cy.get(entityEditButtonSelector).click();
        });
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@overviewRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      it('edit button click should load edit FinancialAccount page and save', () => {
        cy.contains('[data-cy="financialAccountOverviewCard"]', financialAccount.name).within(() => {
          cy.get(entityEditButtonSelector).click();
        });
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@putConfiguredRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', new RegExp('/financial-account/\\d+$'));
        cy.get('[data-cy="accountType"]').should('not.exist');
      });

      it('last delete button click should delete instance of FinancialAccount', () => {
        cy.intercept('GET', '/api/financial-accounts/*').as('dialogDeleteRequest');
        cy.contains('[data-cy="financialAccountOverviewCard"]', financialAccount.name).within(() => {
          cy.get(entityDeleteButtonSelector).click();
        });
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('financialAccount').should('exist');
        cy.get('#fintrackApp\\.financialAccount\\.delete\\.question').should('contain', financialAccount.name);
        cy.get('#fintrackApp\\.financialAccount\\.delete\\.question').should('not.contain', `Financial Account ${financialAccount.id}`);
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@overviewRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);

        financialAccount = undefined;
      });
    });

    it('shows the historical-only explanation for an inactive account', () => {
      const inactiveAccountName = `inactive-${Date.now()}`;
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: { ...buildFinancialAccountPayload(inactiveAccountName), active: false },
      }).then(({ body }) => {
        financialAccount = body;
        cy.visit(`/financial-account/${financialAccount.id}`);
      });

      cy.get('[data-cy="financialAccountStatus"]').should('contain', /Inactive|Inactiva/);
      cy.get('[data-cy="financialAccountInactiveExplanation"]').should('exist');
      cy.get('[data-cy="financialAccountBalanceSection"]').should('exist');
    });
  });

  describe('new FinancialAccount page', () => {
    beforeEach(() => {
      cy.visit(`${financialAccountPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('FinancialAccount');
    });

    it('creates a debit account through one configured product request', () => {
      const accountName = `create-${Date.now()}`;
      fillCreateForm(accountName);
      cy.get('[data-cy="user"]').should('not.exist');
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postConfiguredRequest').then(({ request, response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(request.body).to.have.all.keys('financialAccount');
        expect(request.body.financialAccount).not.to.have.any.keys('id', 'user', 'createdAt', 'updatedAt');
        financialAccount = response.body.financialAccount;
      });
      cy.url().should('match', new RegExp('/financial-account/\\d+$'));
    });

    it('creates and edits a credit card with its contextual details atomically', () => {
      const accountName = `card-${Date.now()}`;
      fillCreditCardForm(accountName);
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postConfiguredRequest').then(({ request, response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(request.body.financialAccount.accountType).to.equal('CREDIT_CARD');
        expect(request.body.creditAccountDetails).to.deep.include({ creditLimit: 5000, statementDay: 15, paymentDueDay: 5 });
        financialAccount = response.body.financialAccount;
      });
      cy.get('[data-cy="creditCardDetailsViewSection"]').should('contain', '5000').and('contain', '15').and('contain', '5');
      cy.get('[data-cy="financialAccountBalanceSection"]').should('contain', '1000').and('contain', '5000');
      cy.get(entityEditButtonSelector).click();
      cy.get('[data-cy="accountType"]').should('be.disabled');
      cy.get('[data-cy="currency"]').should('be.disabled');
      cy.get('[data-cy="creditCardCreditLimit"]').clear().type('6500');
      cy.get(entityCreateSaveButtonSelector).click();
      cy.wait('@putConfiguredRequest').then(({ request, response }) => {
        expect(response?.statusCode).to.equal(200);
        expect(request.body.creditAccountDetails.creditLimit).to.equal(6500);
      });
      cy.get('[data-cy="creditCardDetailsViewSection"]').should('contain', '6500');
    });

    it('rejects an invalid credit-card command without leaving a partial account', () => {
      const accountName = `invalid-card-${Date.now()}`;
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts/configured',
        failOnStatusCode: false,
        body: {
          financialAccount: {
            name: accountName,
            accountType: 'CREDIT_CARD',
            currency: 'MXN',
            initialBalance: 1000,
            initialBalanceDate: '2026-07-08',
            active: true,
          },
          creditAccountDetails: { creditLimit: -1, statementDay: 15, paymentDueDay: 5 },
        },
      }).then(response => {
        expect(response.status).to.equal(400);
        cy.authenticatedRequest({ method: 'GET', url: '/api/financial-accounts/overview' }).then(({ body }) => {
          expect(body.some(account => account.name === accountName)).to.equal(false);
        });
      });
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
          url: '/api/financial-accounts/overview',
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
          url: '/api/financial-accounts/overview',
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
