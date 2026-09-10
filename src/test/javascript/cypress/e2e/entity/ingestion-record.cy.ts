import {
  entityCreateButtonSelector,
  entityDeleteButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('IngestionRecord technical/debug e2e smoke test', () => {
  const ingestionRecordPageUrl = '/ingestion-record';
  const ingestionRecordPageUrlPattern = new RegExp('/ingestion-record(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  interface E2EEntity {
    id: number;
    name?: string;
    [key: string]: unknown;
  }

  let account: E2EEntity | undefined;
  let transactionIngestionId: number | undefined;
  let ingestionRecordId: number | undefined;
  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  beforeEach(() => {
    cy.login(username, password);

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: uniqueName('ingestion-record-technical-account'),
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
          sourceLabel: uniqueName('technical-ingestion-record'),
          startedAt: '2026-01-01T00:00:00Z',
          recordsReceived: 1,
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
      const row = {
        transactionDate: '2026-01-16',
        postingDate: null,
        description: 'Technical ingestion record row',
        signedAmount: '-10.00',
        amount: '10.00',
        flow: 'OUT',
        currency: 'MXN',
        externalReference: null,
        notes: null,
      };

      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/ingestion-records',
        body: {
          recordIndex: 1,
          externalRecordId: uniqueName('technical-record'),
          status: 'VALID',
          rawData: JSON.stringify({ raw: row, normalized: row, errors: [], warnings: [] }),
          createdAt: '2026-01-01T00:00:00Z',
          transactionIngestion: { id: transactionIngestionId },
        },
      }).then(({ body }) => {
        ingestionRecordId = body.id;
      });
    });

    cy.intercept('GET', '/api/ingestion-records+(?*|)').as('entitiesRequest');
    cy.intercept('GET', '/api/ingestion-records/*').as('entityRequest');
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
    ingestionRecordId = undefined;

    if (account?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${account.id}`,
        failOnStatusCode: false,
      });
      account = undefined;
    }
  });

  it('menu should load IngestionRecord technical list page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('ingestion-record');
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);

    cy.getEntityHeading('IngestionRecord').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('be.visible');
    cy.get(entityTableSelector).should('exist');
    cy.get(entityCreateButtonSelector).should('not.exist');
    cy.url().should('match', ingestionRecordPageUrlPattern);
  });

  it('detail page should be technical/read-only from the product workflow perspective', () => {
    cy.visit(ingestionRecordPageUrl);
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);

    cy.get(entityDetailsButtonSelector).first().click();
    cy.wait('@entityRequest').its('response.statusCode').should('eq', 200);
    cy.getEntityDetailsHeading('ingestionRecord').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('be.visible');
    cy.get(entityEditButtonSelector).should('not.exist');
    cy.get(entityDeleteButtonSelector).should('not.exist');
    cy.get(entityDetailsBackButtonSelector).click();
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);
    cy.url().should('match', ingestionRecordPageUrlPattern);
  });

  it('direct generated write routes should be unavailable', () => {
    cy.intercept('POST', '/api/ingestion-records').as('createIngestionRecordRequest');
    cy.intercept('PUT', '/api/ingestion-records/*').as('updateIngestionRecordRequest');
    cy.intercept('DELETE', '/api/ingestion-records/*').as('deleteIngestionRecordRequest');

    cy.visit('/ingestion-record/new');

    cy.get('[data-cy="ingestionRecordWriteUnavailableHeading"]').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('be.visible');
    cy.get('[data-cy="rawData"]').should('not.exist');

    cy.visit(`/ingestion-record/${ingestionRecordId}/edit`);
    cy.get('[data-cy="ingestionRecordWriteUnavailableHeading"]').should('exist');
    cy.get('[data-cy="status"]').should('not.exist');

    cy.visit(`/ingestion-record/${ingestionRecordId}/delete`);
    cy.get('[data-cy="ingestionRecordWriteUnavailableHeading"]').should('exist');
    cy.get(entityDeleteButtonSelector).should('not.exist');
    cy.get('@createIngestionRecordRequest.all').should('have.length', 0);
    cy.get('@updateIngestionRecordRequest.all').should('have.length', 0);
    cy.get('@deleteIngestionRecordRequest.all').should('have.length', 0);
  });
});
