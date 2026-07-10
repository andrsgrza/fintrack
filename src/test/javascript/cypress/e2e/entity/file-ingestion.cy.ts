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

describe('FileIngestion e2e test', () => {
  const fileIngestionPageUrl = '/file-ingestion';
  const fileIngestionPageUrlPattern = new RegExp('/file-ingestion(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const fileIngestionSample = {"originalFilename":"besmirch ouch steep","fileType":"CSV","createdAt":"2026-07-06T23:32:46.184Z"};

  let fileIngestion;
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
      body: {"ingestionType":"FILE","status":"COMPLETED","sourceLabel":"tapioca now if","startedAt":"2026-07-07T02:51:48.748Z","completedAt":"2026-07-06T21:02:43.556Z","recordsReceived":2898,"recordsCreated":15299,"recordsSkipped":27861,"recordsRejected":19981,"errorMessage":"truthfully following","createdAt":"2026-07-06T23:09:37.793Z"},
    }).then(({ body }) => {
      transactionIngestion = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/file-ingestions+(?*|)').as('entitiesRequest');
    cy.intercept('GET', '/api/transaction-ingestions/file-ingestion-is-null').as('fileIngestionParentCandidatesRequest');
    cy.intercept('POST', '/api/file-ingestions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/file-ingestions/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/transaction-ingestions', {
      statusCode: 200,
      body: [transactionIngestion],
    });

  });
   */

  afterEach(() => {
    if (fileIngestion) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/file-ingestions/${fileIngestion.id}`,
      }).then(() => {
        fileIngestion = undefined;
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

  it('FileIngestions menu should load FileIngestions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('file-ingestion');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('FileIngestion').should('exist');
    cy.url().should('match', fileIngestionPageUrlPattern);
  });

  describe('FileIngestion page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(fileIngestionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create FileIngestion page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/file-ingestion/new$'));
        cy.getEntityCreateUpdateHeading('FileIngestion');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', fileIngestionPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/file-ingestions',
          body: {
            ...fileIngestionSample,
            transactionIngestion: transactionIngestion,
          },
        }).then(({ body }) => {
          fileIngestion = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/file-ingestions+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [fileIngestion],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(fileIngestionPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(fileIngestionPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details FileIngestion page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('fileIngestion');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', fileIngestionPageUrlPattern);
      });

      it('edit button click should load edit FileIngestion page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FileIngestion');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', fileIngestionPageUrlPattern);
      });

      it('edit button click should load edit FileIngestion page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FileIngestion');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', fileIngestionPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of FileIngestion', () => {
        cy.intercept('GET', '/api/file-ingestions/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('fileIngestion').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', fileIngestionPageUrlPattern);

        fileIngestion = undefined;
      });
    });
  });

  describe('new FileIngestion page', () => {
    beforeEach(() => {
      cy.visit(`${fileIngestionPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('FileIngestion');
      cy.wait('@fileIngestionParentCandidatesRequest');
    });

    it('should load parent candidates from backend helper endpoint', () => {
      cy.get('[data-cy="transactionIngestion"]').should('exist');
      cy.get('@fileIngestionParentCandidatesRequest').its('response.statusCode').should('eq', 200);
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of FileIngestion', () => {
      cy.get(`[data-cy="originalFilename"]`).type('behind yum');
      cy.get(`[data-cy="originalFilename"]`).should('have.value', 'behind yum');

      cy.get(`[data-cy="fileType"]`).select('XLSX');

      cy.get(`[data-cy="contentType"]`).type('pinstripe tough');
      cy.get(`[data-cy="contentType"]`).should('have.value', 'pinstripe tough');

      cy.get(`[data-cy="fileSizeBytes"]`).type('13358');
      cy.get(`[data-cy="fileSizeBytes"]`).should('have.value', '13358');

      cy.get(`[data-cy="checksum"]`).type('towards heavily');
      cy.get(`[data-cy="checksum"]`).should('have.value', 'towards heavily');

      cy.get(`[data-cy="storageKey"]`).type('altruistic');
      cy.get(`[data-cy="storageKey"]`).should('have.value', 'altruistic');

      cy.get(`[data-cy="parserName"]`).type('to concentration about');
      cy.get(`[data-cy="parserName"]`).should('have.value', 'to concentration about');

      cy.get(`[data-cy="parserVersion"]`).type('funny');
      cy.get(`[data-cy="parserVersion"]`).should('have.value', 'funny');

      cy.get(`[data-cy="statementStartDate"]`).type('2026-07-07');
      cy.get(`[data-cy="statementStartDate"]`).blur();
      cy.get(`[data-cy="statementStartDate"]`).should('have.value', '2026-07-07');

      cy.get(`[data-cy="statementEndDate"]`).type('2026-07-07');
      cy.get(`[data-cy="statementEndDate"]`).blur();
      cy.get(`[data-cy="statementEndDate"]`).should('have.value', '2026-07-07');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T14:51');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T14:51');

      cy.get(`[data-cy="transactionIngestion"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        fileIngestion = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', fileIngestionPageUrlPattern);
    });
  });
});
