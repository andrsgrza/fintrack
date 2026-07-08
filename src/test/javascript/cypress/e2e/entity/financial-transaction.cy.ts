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

describe('FinancialTransaction e2e test', () => {
  const financialTransactionPageUrl = '/financial-transaction';
  const financialTransactionPageUrlPattern = new RegExp('/financial-transaction(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  const adminUsername = Cypress.env('E2E_ADMIN_USERNAME') ?? 'admin';
  const adminPassword = Cypress.env('E2E_ADMIN_PASSWORD') ?? 'admin';

  let financialTransaction;
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

  const buildFinancialTransactionPayload = (accountId: number) => {
    const now = new Date().toISOString();
    return {
      transactionDate: '2026-07-08',
      description: 'E2E transaction',
      amount: 100.5,
      flow: 'OUT',
      origin: 'MANUAL',
      createdAt: now,
      updatedAt: now,
      account: { id: accountId },
    };
  };

  const fillCreateForm = () => {
    cy.get('[data-cy="transactionDate"]').type('2026-07-08');
    cy.get('[data-cy="description"]').clear().type('E2E transaction');
    cy.get('[data-cy="amount"]').clear().type('100.5');
    cy.get('[data-cy="flow"]').select('OUT');
    cy.get('[data-cy="createdAt"]').type('2026-07-08T10:00');
    cy.get('[data-cy="updatedAt"]').type('2026-07-08T10:00');
    cy.get('[data-cy="account"]').select(1);
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: buildFinancialAccountPayload(`tx-account-${Date.now()}`),
    }).then(({ body }) => {
      financialAccount = body;
    });
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-transactions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/financial-transactions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/financial-transactions/*').as('deleteEntityRequest');
    cy.intercept('GET', '/api/financial-accounts+(?*|)').as('accountsRequest');
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/financial-subscriptions+(?*|)').as('subscriptionsRequest');
    cy.intercept('GET', '/api/transaction-ingestions+(?*|)').as('ingestionsRequest');
    cy.intercept('GET', '/api/tags+(?*|)').as('tagsRequest');
  });

  afterEach(() => {
    if (financialTransaction) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-transactions/${financialTransaction.id}`,
      }).then(() => {
        financialTransaction = undefined;
      });
    }
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

  it('FinancialTransactions menu should load FinancialTransactions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('financial-transaction');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('FinancialTransaction').should('exist');
    cy.url().should('match', financialTransactionPageUrlPattern);
  });

  describe('FinancialTransaction page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(financialTransactionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create FinancialTransaction page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/financial-transaction/new$'));
        cy.getEntityCreateUpdateHeading('FinancialTransaction');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: buildFinancialTransactionPayload(financialAccount.id),
        }).then(({ body }) => {
          financialTransaction = body;
        });

        cy.visit(financialTransactionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('detail button click should load details FinancialTransaction page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('financialTransaction');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('edit button click should load edit FinancialTransaction page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialTransaction');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('edit button click should load edit FinancialTransaction page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialTransaction');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);
      });

      it('last delete button click should delete instance of FinancialTransaction', () => {
        cy.intercept('GET', '/api/financial-transactions/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('financialTransaction').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialTransactionPageUrlPattern);

        financialTransaction = undefined;
      });
    });
  });

  describe('new FinancialTransaction page', () => {
    beforeEach(() => {
      cy.visit(`${financialTransactionPageUrl}/new`);
      cy.wait('@accountsRequest');
      cy.wait('@categoriesRequest');
      cy.wait('@subscriptionsRequest');
      cy.wait('@ingestionsRequest');
      cy.wait('@tagsRequest');
      cy.getEntityCreateUpdateHeading('FinancialTransaction');
    });

    it('should create an instance of FinancialTransaction', () => {
      cy.get('[data-cy="origin"]').should('be.disabled');
      cy.get('[data-cy="transactionIngestion"]').should('not.exist');
      fillCreateForm();
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.origin).to.equal('MANUAL');
        expect(response?.body.transactionIngestion).to.equal(null);
        financialTransaction = response.body;
      });
      cy.url().should('match', financialTransactionPageUrlPattern);
    });
  });

  describe('FinancialTransaction ownership', () => {
    it('should not render transaction ingestion on create form', () => {
      cy.visit(`${financialTransactionPageUrl}/new`);
      cy.get('[data-cy="transactionIngestion"]').should('not.exist');
    });

    it('regular user should not see transactions on another users account', () => {
      const adminAccountName = `admin-tx-account-${Date.now()}`;

      cy.login(adminUsername, adminPassword);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: buildFinancialAccountPayload(adminAccountName),
      }).then(({ body: adminAccount }) => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: buildFinancialTransactionPayload(adminAccount.id),
        }).then(({ body: adminTransaction }) => {
          cy.login(username, password);
          cy.authenticatedRequest({
            method: 'GET',
            url: '/api/financial-transactions',
          }).then(({ body: userTransactions }) => {
            expect(userTransactions.some(transaction => transaction.id === adminTransaction.id)).to.equal(false);
          });

          cy.login(adminUsername, adminPassword);
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-transactions/${adminTransaction.id}`,
          });
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-accounts/${adminAccount.id}`,
          });
        });
      });
    });

    it('admin should see transactions on another users account', () => {
      const userAccountName = `user-tx-account-${Date.now()}`;

      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-accounts',
        body: buildFinancialAccountPayload(userAccountName),
      }).then(({ body: userAccount }) => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: buildFinancialTransactionPayload(userAccount.id),
        }).then(({ body: userTransaction }) => {
          cy.login(adminUsername, adminPassword);
          cy.authenticatedRequest({
            method: 'GET',
            url: '/api/financial-transactions',
          }).then(({ body: adminTransactions }) => {
            expect(adminTransactions.some(transaction => transaction.id === userTransaction.id)).to.equal(true);
          });

          cy.login(username, password);
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-transactions/${userTransaction.id}`,
          });
          cy.authenticatedRequest({
            method: 'DELETE',
            url: `/api/financial-accounts/${userAccount.id}`,
          });
        });
      });
    });
  });
});
