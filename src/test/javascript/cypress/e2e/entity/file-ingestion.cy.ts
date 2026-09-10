import {
  entityCreateButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityDeleteButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('FileIngestion technical/debug e2e smoke test', () => {
  const fileIngestionPageUrl = '/file-ingestion';
  const fileIngestionPageUrlPattern = new RegExp('/file-ingestion(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  interface E2EEntity {
    id: number;
    name?: string;
    [key: string]: unknown;
  }

  let account: E2EEntity | undefined;
  let transactionIngestionId: number | undefined;
  let fileIngestionId: number | undefined;
  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const uniqueChecksum = () => Date.now().toString(16).padStart(64, 'a').slice(0, 64);

  beforeEach(() => {
    cy.login(username, password);

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: uniqueName('file-ingestion-technical-account'),
        institutionName: 'E2E Bank',
        accountType: 'DEBIT',
        currency: 'MXN',
        initialBalance: 0,
        initialBalanceDate: '2026-01-01',
        active: true,
      },
    }).then(({ body }) => {
      account = body;
    });

    cy.then(() => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-ingestions',
        body: {
          ingestionType: 'FILE',
          status: 'PENDING',
          sourceLabel: uniqueName('technical-file-ingestion'),
          startedAt: '2026-01-01T00:00:00Z',
          recordsReceived: 0,
          recordsCreated: 0,
          recordsSkipped: 0,
          recordsRejected: 0,
          createdAt: '2026-01-01T00:00:00Z',
          account: { id: account?.id },
        },
      }).then(({ body }) => {
        transactionIngestionId = body.id;
      });
    });

    cy.then(() => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/file-ingestions',
        body: {
          originalFilename: uniqueName('technical-file-ingestion') + '.csv',
          fileType: 'CSV',
          contentType: 'text/csv',
          fileSizeBytes: 123,
          checksum: uniqueChecksum(),
          storageKey: null,
          parserName: 'fintrack-canonical-csv',
          parserVersion: '1.0',
          statementStartDate: '2026-01-16',
          statementEndDate: '2026-01-16',
          transactionIngestion: { id: transactionIngestionId },
        },
      }).then(({ body }) => {
        fileIngestionId = body.id;
      });
    });

    cy.intercept('GET', '/api/file-ingestions+(?*|)').as('entitiesRequest');
    cy.intercept('GET', '/api/file-ingestions/*').as('entityRequest');
    cy.intercept('GET', '/api/transaction-ingestions/file-ingestion-is-null').as('fileIngestionParentCandidatesRequest');
  });

  afterEach(() => {
    if (transactionIngestionId) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-ingestions/${transactionIngestionId}`,
        failOnStatusCode: false,
      });
      transactionIngestionId = undefined;
    }
    fileIngestionId = undefined;

    if (account?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${account.id}`,
        failOnStatusCode: false,
      });
      account = undefined;
    }
  });

  it('menu should load FileIngestion technical list page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('file-ingestion');
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);

    cy.getEntityHeading('FileIngestion').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('be.visible');
    cy.get(entityTableSelector).should('exist');
    cy.get(entityCreateButtonSelector).should('not.exist');
    cy.url().should('match', fileIngestionPageUrlPattern);
  });

  it('detail page should be technical/read-only from the product workflow perspective', () => {
    cy.visit(fileIngestionPageUrl);
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);

    cy.get(entityDetailsButtonSelector).first().click();
    cy.wait('@entityRequest').its('response.statusCode').should('eq', 200);
    cy.getEntityDetailsHeading('fileIngestion').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('be.visible');
    cy.get(entityEditButtonSelector).should('not.exist');
    cy.get(entityDeleteButtonSelector).should('not.exist');
    cy.get(entityDetailsBackButtonSelector).click();
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);
    cy.url().should('match', fileIngestionPageUrlPattern);
  });

  it('direct generated write routes should be unavailable', () => {
    cy.intercept('POST', '/api/transaction-ingestions/*/file-ingestion').as('attachFileRequest');
    cy.intercept('PUT', '/api/file-ingestions/*').as('updateFileIngestionRequest');
    cy.intercept('DELETE', '/api/file-ingestions/*').as('deleteFileIngestionRequest');

    cy.visit(fileIngestionPageUrl);
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);
    cy.get(entityCreateButtonSelector).should('not.exist');

    cy.visit('/file-ingestion/new');
    cy.url().should('match', new RegExp('/file-ingestion/new$'));
    cy.get('[data-cy="fileIngestionWriteUnavailableHeading"]').should('exist');
    cy.get('[data-cy="writeUnavailableBanner"]').should('be.visible');
    cy.get('[data-cy="technicalViewBanner"]').should('be.visible');
    cy.get('[data-cy="csvFile"]').should('not.exist');

    cy.visit(`/file-ingestion/${fileIngestionId}/edit`);
    cy.get('[data-cy="fileIngestionWriteUnavailableHeading"]').should('exist');
    cy.get('[data-cy="originalFilename"]').should('not.exist');

    cy.visit(`/file-ingestion/${fileIngestionId}/delete`);
    cy.get('[data-cy="fileIngestionWriteUnavailableHeading"]').should('exist');
    cy.get(entityDeleteButtonSelector).should('not.exist');
    cy.get('@attachFileRequest.all').should('have.length', 0);
    cy.get('@updateFileIngestionRequest.all').should('have.length', 0);
    cy.get('@deleteFileIngestionRequest.all').should('have.length', 0);
  });
});
