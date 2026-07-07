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

describe('ApiIngestion e2e test', () => {
  const apiIngestionPageUrl = '/api-ingestion';
  const apiIngestionPageUrlPattern = new RegExp('/api-ingestion(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const apiIngestionSample = {"requestId":"unearth gadzooks","apiVersion":"ouch hm swordfish","endpoint":"unto yum lest","receivedAt":"2026-07-06T23:54:45.962Z","createdAt":"2026-07-07T07:37:30.202Z"};

  let apiIngestion;
  // let transactionIngestion;
  // let apiAccessToken;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/transaction-ingestions',
      body: {"ingestionType":"API","status":"PROCESSING","sourceLabel":"pro functional beside","startedAt":"2026-07-07T01:11:39.691Z","completedAt":"2026-07-07T01:25:45.625Z","recordsReceived":28333,"recordsCreated":29796,"recordsSkipped":14520,"recordsRejected":27344,"errorMessage":"esteemed","createdAt":"2026-07-07T12:38:50.043Z"},
    }).then(({ body }) => {
      transactionIngestion = body;
    });
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/api-access-tokens',
      body: {"name":"schnitzel","tokenPrefix":"narrowcast boohoo no","tokenHash":"phooey yowza","status":"REVOKED","createdAt":"2026-07-06T17:52:24.274Z","updatedAt":"2026-07-07T15:24:24.561Z","lastUsedAt":"2026-07-06T18:07:44.699Z","expiresAt":"2026-07-06T18:27:41.137Z","revokedAt":"2026-07-07T16:02:22.600Z"},
    }).then(({ body }) => {
      apiAccessToken = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/api-ingestions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/api-ingestions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/api-ingestions/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/transaction-ingestions', {
      statusCode: 200,
      body: [transactionIngestion],
    });

    cy.intercept('GET', '/api/api-access-tokens', {
      statusCode: 200,
      body: [apiAccessToken],
    });

  });
   */

  afterEach(() => {
    if (apiIngestion) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/api-ingestions/${apiIngestion.id}`,
      }).then(() => {
        apiIngestion = undefined;
      });
    }
  });

  /* Disabled due to incompatibility
  afterEach(() => {
    if (transactionIngestion) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-ingestions/${transactionIngestion.id}`,
      }).then(() => {
        transactionIngestion = undefined;
      });
    }
    if (apiAccessToken) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/api-access-tokens/${apiAccessToken.id}`,
      }).then(() => {
        apiAccessToken = undefined;
      });
    }
  });
   */

  it('ApiIngestions menu should load ApiIngestions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('api-ingestion');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('ApiIngestion').should('exist');
    cy.url().should('match', apiIngestionPageUrlPattern);
  });

  describe('ApiIngestion page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(apiIngestionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create ApiIngestion page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/api-ingestion/new$'));
        cy.getEntityCreateUpdateHeading('ApiIngestion');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiIngestionPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/api-ingestions',
          body: {
            ...apiIngestionSample,
            transactionIngestion: transactionIngestion,
            apiAccessToken: apiAccessToken,
          },
        }).then(({ body }) => {
          apiIngestion = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/api-ingestions+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [apiIngestion],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(apiIngestionPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(apiIngestionPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details ApiIngestion page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('apiIngestion');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiIngestionPageUrlPattern);
      });

      it('edit button click should load edit ApiIngestion page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('ApiIngestion');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiIngestionPageUrlPattern);
      });

      it('edit button click should load edit ApiIngestion page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('ApiIngestion');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiIngestionPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of ApiIngestion', () => {
        cy.intercept('GET', '/api/api-ingestions/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('apiIngestion').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', apiIngestionPageUrlPattern);

        apiIngestion = undefined;
      });
    });
  });

  describe('new ApiIngestion page', () => {
    beforeEach(() => {
      cy.visit(`${apiIngestionPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('ApiIngestion');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of ApiIngestion', () => {
      cy.get(`[data-cy="requestId"]`).type('even besides achieve');
      cy.get(`[data-cy="requestId"]`).should('have.value', 'even besides achieve');

      cy.get(`[data-cy="idempotencyKey"]`).type('defrag unfit');
      cy.get(`[data-cy="idempotencyKey"]`).should('have.value', 'defrag unfit');

      cy.get(`[data-cy="sourceSystem"]`).type('than dual for');
      cy.get(`[data-cy="sourceSystem"]`).should('have.value', 'than dual for');

      cy.get(`[data-cy="apiVersion"]`).type('a into');
      cy.get(`[data-cy="apiVersion"]`).should('have.value', 'a into');

      cy.get(`[data-cy="endpoint"]`).type('successfully aw yuck');
      cy.get(`[data-cy="endpoint"]`).should('have.value', 'successfully aw yuck');

      cy.get(`[data-cy="clientReference"]`).type('waist down');
      cy.get(`[data-cy="clientReference"]`).should('have.value', 'waist down');

      cy.get(`[data-cy="receivedAt"]`).type('2026-07-07T05:48');
      cy.get(`[data-cy="receivedAt"]`).blur();
      cy.get(`[data-cy="receivedAt"]`).should('have.value', '2026-07-07T05:48');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T00:10');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T00:10');

      cy.get(`[data-cy="transactionIngestion"]`).select(1);
      cy.get(`[data-cy="apiAccessToken"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        apiIngestion = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', apiIngestionPageUrlPattern);
    });
  });
});
