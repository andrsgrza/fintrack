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

describe('IngestionRecord e2e test', () => {
  const ingestionRecordPageUrl = '/ingestion-record';
  const ingestionRecordPageUrlPattern = new RegExp('/ingestion-record(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const ingestionRecordSample = {"recordIndex":8439,"status":"CREATED","createdAt":"2026-07-07T10:56:59.034Z"};

  let ingestionRecord;
  // let transactionIngestion;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/transaction-ingestions',
      body: {"ingestionType":"FILE","status":"PROCESSING","sourceLabel":"unbearably when","startedAt":"2026-07-07T09:12:41.316Z","completedAt":"2026-07-07T03:14:13.453Z","recordsReceived":9374,"recordsCreated":29248,"recordsSkipped":299,"recordsRejected":4799,"errorMessage":"instead jet reluctantly","createdAt":"2026-07-07T05:43:57.488Z"},
    }).then(({ body }) => {
      transactionIngestion = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/ingestion-records+(?*|)').as('entitiesRequest');
    cy.intercept('GET', '/api/transaction-ingestions').as('transactionIngestionCandidatesRequest');
    cy.intercept('GET', '/api/financial-transactions/ingestion-record-is-null').as('financialTransactionCandidatesRequest');
    cy.intercept('POST', '/api/ingestion-records').as('postEntityRequest');
    cy.intercept('DELETE', '/api/ingestion-records/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/financial-transactions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/transaction-ingestions', {
      statusCode: 200,
      body: [transactionIngestion],
    });

  });
   */

  afterEach(() => {
    if (ingestionRecord) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/ingestion-records/${ingestionRecord.id}`,
      }).then(() => {
        ingestionRecord = undefined;
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
  });
   */

  it('IngestionRecords menu should load IngestionRecords page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('ingestion-record');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('IngestionRecord').should('exist');
    cy.url().should('match', ingestionRecordPageUrlPattern);
  });

  describe('IngestionRecord page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(ingestionRecordPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create IngestionRecord page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/ingestion-record/new$'));
        cy.getEntityCreateUpdateHeading('IngestionRecord');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', ingestionRecordPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/ingestion-records',
          body: {
            ...ingestionRecordSample,
            transactionIngestion: transactionIngestion,
          },
        }).then(({ body }) => {
          ingestionRecord = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/ingestion-records+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              headers: {
                link: '<http://localhost/api/ingestion-records?page=0&size=20>; rel="last",<http://localhost/api/ingestion-records?page=0&size=20>; rel="first"',
              },
              body: [ingestionRecord],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(ingestionRecordPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(ingestionRecordPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details IngestionRecord page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('ingestionRecord');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', ingestionRecordPageUrlPattern);
      });

      it('edit button click should load edit IngestionRecord page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('IngestionRecord');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', ingestionRecordPageUrlPattern);
      });

      it('edit button click should load edit IngestionRecord page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('IngestionRecord');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', ingestionRecordPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of IngestionRecord', () => {
        cy.intercept('GET', '/api/ingestion-records/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('ingestionRecord').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', ingestionRecordPageUrlPattern);

        ingestionRecord = undefined;
      });
    });
  });

  describe('new IngestionRecord page', () => {
    beforeEach(() => {
      cy.visit(`${ingestionRecordPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('IngestionRecord');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of IngestionRecord', () => {
      cy.get(`[data-cy="recordIndex"]`).type('9448');
      cy.get(`[data-cy="recordIndex"]`).should('have.value', '9448');

      cy.get(`[data-cy="externalRecordId"]`).type('duh meh');
      cy.get(`[data-cy="externalRecordId"]`).should('have.value', 'duh meh');

      cy.get(`[data-cy="status"]`).select('CREATED');

      cy.get(`[data-cy="rawData"]`).type('../fake-data/blob/hipster.txt');
      cy.get(`[data-cy="rawData"]`).invoke('val').should('match', new RegExp('../fake-data/blob/hipster.txt'));

      cy.get(`[data-cy="errorCode"]`).type('catalyze incidentally er');
      cy.get(`[data-cy="errorCode"]`).should('have.value', 'catalyze incidentally er');

      cy.get(`[data-cy="errorMessage"]`).type('lawmaker information entomb');
      cy.get(`[data-cy="errorMessage"]`).should('have.value', 'lawmaker information entomb');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T05:04');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T05:04');

      cy.get(`[data-cy="transactionIngestion"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        ingestionRecord = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', ingestionRecordPageUrlPattern);
    });
  });
});
