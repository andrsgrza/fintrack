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

describe('TransactionIngestion e2e test', () => {
  const transactionIngestionPageUrl = '/transaction-ingestion';
  const transactionIngestionPageUrlPattern = new RegExp('/transaction-ingestion(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const transactionIngestionSample = {"ingestionType":"API","status":"FAILED","startedAt":"2026-07-06T20:10:21.746Z","recordsReceived":22141,"recordsCreated":6190,"recordsSkipped":9783,"recordsRejected":28945,"createdAt":"2026-07-07T12:39:51.701Z"};

  let transactionIngestion;
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
      body: {"name":"modulo","institutionName":"howl aw rival","accountType":"DEBIT","currency":"MHI","initialBalance":8870.7,"initialBalanceDate":"2026-07-06","lastFourDigits":"5090","description":"pish within","color":"#2C5CFc","icon":"oof bitterly","active":true,"includeInNetWorth":false,"createdAt":"2026-07-07T11:49:23.258Z","updatedAt":"2026-07-07T06:30:58.022Z"},
    }).then(({ body }) => {
      financialAccount = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/transaction-ingestions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/transaction-ingestions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/transaction-ingestions/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/financial-accounts', {
      statusCode: 200,
      body: [financialAccount],
    });

    cy.intercept('GET', '/api/file-ingestions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/api-ingestions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/financial-transactions', {
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
    if (transactionIngestion) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-ingestions/${transactionIngestion.id}`,
      }).then(() => {
        transactionIngestion = undefined;
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

  it('TransactionIngestions menu should load TransactionIngestions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('transaction-ingestion');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('TransactionIngestion').should('exist');
    cy.url().should('match', transactionIngestionPageUrlPattern);
  });

  describe('TransactionIngestion page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(transactionIngestionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create TransactionIngestion page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/transaction-ingestion/new$'));
        cy.getEntityCreateUpdateHeading('TransactionIngestion');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionIngestionPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/transaction-ingestions',
          body: {
            ...transactionIngestionSample,
            accounts: financialAccount,
          },
        }).then(({ body }) => {
          transactionIngestion = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/transaction-ingestions+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              headers: {
                link: '<http://localhost/api/transaction-ingestions?page=0&size=20>; rel="last",<http://localhost/api/transaction-ingestions?page=0&size=20>; rel="first"',
              },
              body: [transactionIngestion],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(transactionIngestionPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(transactionIngestionPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details TransactionIngestion page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('transactionIngestion');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionIngestionPageUrlPattern);
      });

      it('edit button click should load edit TransactionIngestion page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionIngestion');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionIngestionPageUrlPattern);
      });

      it('edit button click should load edit TransactionIngestion page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('TransactionIngestion');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionIngestionPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of TransactionIngestion', () => {
        cy.intercept('GET', '/api/transaction-ingestions/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('transactionIngestion').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', transactionIngestionPageUrlPattern);

        transactionIngestion = undefined;
      });
    });
  });

  describe('new TransactionIngestion page', () => {
    beforeEach(() => {
      cy.visit(`${transactionIngestionPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('TransactionIngestion');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of TransactionIngestion', () => {
      cy.get(`[data-cy="ingestionType"]`).select('API');

      cy.get(`[data-cy="status"]`).select('PARTIALLY_COMPLETED');

      cy.get(`[data-cy="sourceLabel"]`).type('reassuringly stool justly');
      cy.get(`[data-cy="sourceLabel"]`).should('have.value', 'reassuringly stool justly');

      cy.get(`[data-cy="startedAt"]`).type('2026-07-06T20:01');
      cy.get(`[data-cy="startedAt"]`).blur();
      cy.get(`[data-cy="startedAt"]`).should('have.value', '2026-07-06T20:01');

      cy.get(`[data-cy="completedAt"]`).type('2026-07-06T18:54');
      cy.get(`[data-cy="completedAt"]`).blur();
      cy.get(`[data-cy="completedAt"]`).should('have.value', '2026-07-06T18:54');

      cy.get(`[data-cy="recordsReceived"]`).type('2060');
      cy.get(`[data-cy="recordsReceived"]`).should('have.value', '2060');

      cy.get(`[data-cy="recordsCreated"]`).type('16551');
      cy.get(`[data-cy="recordsCreated"]`).should('have.value', '16551');

      cy.get(`[data-cy="recordsSkipped"]`).type('4193');
      cy.get(`[data-cy="recordsSkipped"]`).should('have.value', '4193');

      cy.get(`[data-cy="recordsRejected"]`).type('27629');
      cy.get(`[data-cy="recordsRejected"]`).should('have.value', '27629');

      cy.get(`[data-cy="errorMessage"]`).type('boo chops');
      cy.get(`[data-cy="errorMessage"]`).should('have.value', 'boo chops');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T03:39');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T03:39');

      cy.get(`[data-cy="accounts"]`).select([0]);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        transactionIngestion = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', transactionIngestionPageUrlPattern);
    });
  });
});
