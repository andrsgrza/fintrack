import {
  entityCreateButtonSelector,
  entityCreateSaveButtonSelector,
  entityDeleteButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('ApiIngestion technical read-only e2e test', () => {
  const apiIngestionPageUrl = '/api-ingestion';
  const apiIngestionPageUrlPattern = new RegExp('/api-ingestion(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  interface E2EEntity {
    id: number;
    name?: string;
    sourceLabel?: string;
    [key: string]: unknown;
  }

  let account: E2EEntity | undefined;
  let transactionIngestion: E2EEntity | undefined;
  let apiAccessToken: E2EEntity | undefined;
  let apiIngestion: E2EEntity | undefined;

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  beforeEach(() => {
    cy.login(username, password);

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: uniqueName('api-ingestion-account'),
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
      expect(account?.id).to.be.a('number');

      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/api-access-tokens',
        body: {
          name: uniqueName('api-ingestion-token'),
        },
      }).then(({ body }) => {
        apiAccessToken = body;
      });
    });

    cy.then(() => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-ingestions',
        body: {
          ingestionType: 'API',
          status: 'PENDING',
          sourceLabel: uniqueName('api-ingestion-parent'),
          startedAt: '2026-01-01T00:00:00Z',
          recordsReceived: 0,
          recordsCreated: 0,
          recordsSkipped: 0,
          recordsRejected: 0,
          createdAt: '2026-01-01T00:00:00Z',
          account: { id: account?.id },
        },
      }).then(({ body }) => {
        transactionIngestion = body;
      });
    });

    cy.then(() => {
      expect(transactionIngestion?.id).to.be.a('number');
      expect(apiAccessToken?.id).to.be.a('number');

      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/api-ingestions',
        body: {
          requestId: uniqueName('api-request'),
          idempotencyKey: uniqueName('idem'),
          sourceSystem: 'cypress',
          apiVersion: 'v1',
          endpoint: '/transactions',
          clientReference: uniqueName('client-ref'),
          transactionIngestion: { id: transactionIngestion?.id },
          apiAccessTokenId: apiAccessToken?.id,
        },
      }).then(({ body }) => {
        apiIngestion = body;
      });
    });
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/api-ingestions+(?*|)').as('entitiesRequest');
    cy.intercept('GET', '/api/api-ingestions/*').as('entityRequest');
    cy.intercept('GET', '/api/transaction-ingestions/api-ingestion-is-null').as('apiIngestionParentCandidatesRequest');
    cy.intercept('POST', '/api/api-ingestions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/api-ingestions/*').as('deleteEntityRequest');
  });

  afterEach(() => {
    if (transactionIngestion?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/transaction-ingestions/${transactionIngestion.id}`,
        failOnStatusCode: false,
      });
      transactionIngestion = undefined;
    }

    if (apiAccessToken?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/api-access-tokens/${apiAccessToken.id}`,
        failOnStatusCode: false,
      });
      apiAccessToken = undefined;
    }

    if (account?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${account.id}`,
        failOnStatusCode: false,
      });
      account = undefined;
    }

    apiIngestion = undefined;
  });

  it('ApiIngestions menu should load the technical read-only page', () => {
    cy.intercept('GET', '/api/api-ingestions+(?*|)', {
      body: [apiIngestion],
      headers: { 'x-total-count': '1' },
    }).as('currentApiIngestionEntitiesRequest');

    cy.visit('/');
    cy.clickOnEntityMenuItem('api-ingestion');

    cy.wait('@currentApiIngestionEntitiesRequest').its('response.statusCode').should('eq', 200);
    cy.getEntityHeading('ApiIngestion').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('exist');
    cy.get(entityCreateButtonSelector).should('not.exist');
    cy.get(entityTableSelector).should('exist');
    cy.contains(entityTableSelector, apiIngestion?.requestId as string).within(() => {
      cy.get(entityDetailsButtonSelector).should('exist');
      cy.get(entityEditButtonSelector).should('not.exist');
      cy.get(entityDeleteButtonSelector).should('not.exist');
    });
    cy.url().should('match', apiIngestionPageUrlPattern);
  });

  it('view button should load technical read-only details', () => {
    cy.intercept('GET', '/api/api-ingestions+(?*|)', {
      body: [apiIngestion],
      headers: { 'x-total-count': '1' },
    }).as('currentApiIngestionEntitiesRequest');

    cy.visit(apiIngestionPageUrl);
    cy.wait('@currentApiIngestionEntitiesRequest').its('response.statusCode').should('eq', 200);

    cy.contains(entityTableSelector, apiIngestion?.requestId as string).within(() => {
      cy.get(entityDetailsButtonSelector).click();
    });

    cy.wait('@entityRequest').its('response.statusCode').should('eq', 200);
    cy.getEntityDetailsHeading('apiIngestion').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('exist');
    cy.get(entityEditButtonSelector).should('not.exist');
    cy.get(entityDeleteButtonSelector).should('not.exist');
    cy.get(entityDetailsBackButtonSelector).click();
    cy.wait('@currentApiIngestionEntitiesRequest').its('response.statusCode').should('eq', 200);
    cy.url().should('match', apiIngestionPageUrlPattern);
  });

  it('direct write routes show a safe unavailable state', () => {
    cy.visit('/api-ingestion/new');
    cy.get('[data-cy="apiIngestionWriteUnavailableHeading"]').should('exist');
    cy.get('[data-cy="technicalViewBanner"]').should('exist');
    cy.get('[data-cy="writeUnavailableBanner"]').should('exist');
    cy.get(entityCreateSaveButtonSelector).should('not.exist');
    cy.get('@apiIngestionParentCandidatesRequest.all').should('have.length', 0);
    cy.get('@postEntityRequest.all').should('have.length', 0);

    cy.visit(`/api-ingestion/${apiIngestion?.id}/edit`);
    cy.get('[data-cy="apiIngestionWriteUnavailableHeading"]').should('exist');
    cy.get(entityCreateSaveButtonSelector).should('not.exist');
    cy.get(entityEditButtonSelector).should('not.exist');

    cy.visit(`/api-ingestion/${apiIngestion?.id}/delete`);
    cy.get('[data-cy="apiIngestionWriteUnavailableHeading"]').should('exist');
    cy.get(entityDeleteButtonSelector).should('not.exist');
    cy.get('@deleteEntityRequest.all').should('have.length', 0);
  });
});
