import {
  entityCreateButtonSelector,
  entityCreateCancelButtonSelector,
  entityCreateSaveButtonSelector,
  entityDetailsBackButtonSelector,
  entityDetailsButtonSelector,
  entityEditButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('TransactionIngestion workflow smoke e2e test', () => {
  const transactionIngestionPageUrl = '/transaction-ingestion';
  const transactionIngestionPageUrlPattern = new RegExp('/transaction-ingestion(\\?.*)?$');
  const username = Cypress.env('INGESTION_E2E_USERNAME') ?? 'cypress_ingestion';
  const password = Cypress.env('INGESTION_E2E_PASSWORD') ?? 'cypress_ingestion';

  interface E2EEntity {
    id: number;
    name?: string;
    [key: string]: unknown;
  }

  let account: E2EEntity | undefined;
  let transactionIngestion: E2EEntity | undefined;

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  beforeEach(() => {
    cy.login(username, password);

    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: {
        name: uniqueName('csv-smoke-account'),
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
        url: '/api/transaction-ingestions',
        body: {
          ingestionType: 'API',
          status: 'PENDING',
          sourceLabel: uniqueName('workflow-smoke'),
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
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/transaction-ingestions+(?*|)').as('entitiesRequest');
    cy.intercept('GET', '/api/transaction-ingestions/*').as('entityRequest');
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

    if (account?.id) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${account.id}`,
        failOnStatusCode: false,
      });
      account = undefined;
    }
  });

  it('TransactionIngestions menu should load the workflow list page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('transaction-ingestion');

    cy.wait('@entitiesRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.length).to.be.greaterThan(0);
    });

    cy.getEntityHeading('TransactionIngestion').should('exist');
    cy.get(entityTableSelector).should('exist');
    cy.url().should('match', transactionIngestionPageUrlPattern);
  });

  it('create button should load the workflow create/upload page', () => {
    cy.visit(transactionIngestionPageUrl);
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);

    cy.get(entityCreateButtonSelector).click();
    cy.url().should('match', new RegExp('/transaction-ingestion/new$'));
    cy.getEntityCreateUpdateHeading('TransactionIngestion').should('exist');
    cy.get(entityCreateSaveButtonSelector).should('exist');
    cy.get('[data-cy="account"]').should('exist');
    cy.get('[data-cy="ingestionType"]').should('exist');
    cy.get('[data-cy="csvFile"]').should('exist');

    cy.get(entityCreateCancelButtonSelector).click();
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);
    cy.url().should('match', transactionIngestionPageUrlPattern);
  });

  it('view button should load the workflow detail page for an existing ingestion', () => {
    cy.visit(transactionIngestionPageUrl);
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);

    cy.contains(entityTableSelector, transactionIngestion?.sourceLabel as string).within(() => {
      cy.get(entityDetailsButtonSelector).click();
    });

    cy.wait('@entityRequest').its('response.statusCode').should('eq', 200);
    cy.url().should('match', new RegExp(`/transaction-ingestion/${transactionIngestion?.id}$`));
    cy.get('[data-cy="workflowReviewHeading"]').should('exist');
    cy.get('[data-cy="transactionIngestionParentSummary"]').should('exist');
    cy.get('[data-cy="apiIngestionTbd"]').should('exist');
    cy.get(entityDetailsBackButtonSelector).click();
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);
    cy.url().should('match', transactionIngestionPageUrlPattern);
  });

  it('list should not expose old generated edit action as a product action', () => {
    cy.visit(transactionIngestionPageUrl);
    cy.wait('@entitiesRequest').its('response.statusCode').should('eq', 200);

    cy.contains(entityTableSelector, transactionIngestion?.sourceLabel as string).within(() => {
      cy.get(entityDetailsButtonSelector).should('exist');
      cy.get(entityEditButtonSelector).should('not.exist');
    });
  });
});
