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
  // const financialTransactionSample = {"transactionDate":"2026-07-07","description":"lifestyle sequester trusting","amount":11741.24,"flow":"OUT","origin":"MANUAL","createdAt":"2026-07-07T10:51:29.674Z","updatedAt":"2026-07-07T02:46:16.867Z"};

  let financialTransaction;
  // let financialAccount;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {"name":"wash near hoof","institutionName":"yum whoa","accountType":"CREDIT_CARD","currency":"USD","initialBalance":22902.49,"initialBalanceDate":"2026-07-07","lastFourDigits":"1145","description":"ick","color":"#5e1E8a","icon":"woot meatloaf yum","active":false,"createdAt":"2026-07-07T05:12:31.975Z","updatedAt":"2026-07-07T06:02:53.163Z"},
    }).then(({ body }) => {
      financialAccount = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-transactions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/financial-transactions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/financial-transactions/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/financial-accounts', {
      statusCode: 200,
      body: [financialAccount],
    });

    cy.intercept('GET', '/api/categories', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/financial-subscriptions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/transaction-ingestions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/tags', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/internal-transfers', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/ingestion-records', {
      statusCode: 200,
      body: [],
    });

  });
   */

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

  /* Disabled due to incompatibility
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
   */

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
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-transactions',
          body: {
            ...financialTransactionSample,
            account: financialAccount,
          },
        }).then(({ body }) => {
          financialTransaction = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/financial-transactions+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              headers: {
                link: '<http://localhost/api/financial-transactions?page=0&size=20>; rel="last",<http://localhost/api/financial-transactions?page=0&size=20>; rel="first"',
              },
              body: [financialTransaction],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(financialTransactionPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(financialTransactionPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
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

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of FinancialTransaction', () => {
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
      cy.visit(`${financialTransactionPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('FinancialTransaction');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of FinancialTransaction', () => {
      cy.get(`[data-cy="transactionDate"]`).type('2026-07-07');
      cy.get(`[data-cy="transactionDate"]`).blur();
      cy.get(`[data-cy="transactionDate"]`).should('have.value', '2026-07-07');

      cy.get(`[data-cy="postingDate"]`).type('2026-07-07');
      cy.get(`[data-cy="postingDate"]`).blur();
      cy.get(`[data-cy="postingDate"]`).should('have.value', '2026-07-07');

      cy.get(`[data-cy="description"]`).type('sashay continually term');
      cy.get(`[data-cy="description"]`).should('have.value', 'sashay continually term');

      cy.get(`[data-cy="amount"]`).type('22788.63');
      cy.get(`[data-cy="amount"]`).should('have.value', '22788.63');

      cy.get(`[data-cy="flow"]`).select('OUT');

      cy.get(`[data-cy="origin"]`).select('API');

      cy.get(`[data-cy="externalReference"]`).type('by');
      cy.get(`[data-cy="externalReference"]`).should('have.value', 'by');

      cy.get(`[data-cy="notes"]`).type('reclassify transcend underneath');
      cy.get(`[data-cy="notes"]`).should('have.value', 'reclassify transcend underneath');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T00:40');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T00:40');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-06T22:56');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-06T22:56');

      cy.get(`[data-cy="account"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        financialTransaction = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', financialTransactionPageUrlPattern);
    });
  });
});
