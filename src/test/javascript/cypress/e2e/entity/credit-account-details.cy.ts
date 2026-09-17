import { entityCreateButtonSelector, entityDeleteButtonSelector, entityEditButtonSelector } from '../../support/entity';

describe('CreditAccountDetails technical route e2e test', () => {
  const creditAccountDetailsPageUrl = '/credit-account-details';
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  let financialAccount;
  let creditAccountDetails;

  const buildCreditCardAccountPayload = (name: string) => ({
    name,
    accountType: 'CREDIT_CARD',
    currency: 'MXN',
    initialBalance: 0,
    initialBalanceDate: '2026-07-08',
    active: true,
  });

  beforeEach(() => {
    cy.login(username, password);
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: buildCreditCardAccountPayload(`technical-card-${Date.now()}`),
    }).then(({ body }) => {
      financialAccount = body;
    });
  });

  afterEach(() => {
    if (financialAccount) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-accounts/${financialAccount.id}`,
      });
    }
  });

  it('keeps the direct list as a technical read-only surface without create or delete actions', () => {
    cy.visit(creditAccountDetailsPageUrl);

    cy.get('[data-cy="creditAccountDetailsTechnicalNotice"]').find('.badge').should('be.visible');
    cy.get('[data-cy="creditAccountDetailsTechnicalNotice"]').find('.alert').should('be.visible');
    cy.get(entityCreateButtonSelector).should('not.exist');
    cy.get(entityEditButtonSelector).should('not.exist');
    cy.get(entityDeleteButtonSelector).should('not.exist');
  });

  it('keeps direct detail technical and links to the parent account instead of child editing', () => {
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/credit-account-details',
      body: {
        creditLimit: 5000,
        statementDay: 10,
        paymentDueDay: 20,
        account: { id: financialAccount.id },
      },
    }).then(({ body }) => {
      creditAccountDetails = body;
      cy.visit(`${creditAccountDetailsPageUrl}/${creditAccountDetails.id}`);
    });

    cy.get('[data-cy="creditAccountDetailsTechnicalNotice"]').should('be.visible');
    cy.get(`[href="/financial-account/${financialAccount.id}"]`).should('exist');
    cy.get(`[href="/financial-account/${financialAccount.id}/edit"]`).should('exist');
    cy.get(entityEditButtonSelector).should('not.exist');
  });

  it('keeps direct create and edit URLs resolvable but unavailable for product writes', () => {
    cy.visit(`${creditAccountDetailsPageUrl}/new`);

    cy.get('[data-cy="creditAccountDetailsTechnicalNotice"]').should(
      'contain',
      /Direct creation and editing are unavailable|no están disponibles/,
    );
    cy.get('[data-cy="creditAccountDetailsManageAccountsButton"]').should('have.attr', 'href', '/financial-account');
    cy.get('[data-cy="creditLimit"]').should('not.exist');
    cy.get('[data-cy="entityCreateSaveButton"]').should('not.exist');

    cy.visit(`${creditAccountDetailsPageUrl}/999/edit`);
    cy.get('[data-cy="creditAccountDetailsTechnicalNotice"]').should('be.visible');
    cy.get('[data-cy="entityCreateSaveButton"]').should('not.exist');
  });
});
