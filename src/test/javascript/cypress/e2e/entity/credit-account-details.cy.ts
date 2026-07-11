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

describe('CreditAccountDetails e2e test', () => {
  const creditAccountDetailsPageUrl = '/credit-account-details';
  const creditAccountDetailsPageUrlPattern = new RegExp('/credit-account-details(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  let creditAccountDetails;
  let financialAccount;

  const buildCreditCardAccountPayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      accountType: 'CREDIT_CARD',
      currency: 'MXN',
      initialBalance: 0,
      initialBalanceDate: '2026-07-08',
      active: true,
      createdAt: now,
      updatedAt: now,
    };
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/financial-accounts',
      body: buildCreditCardAccountPayload(`credit-card-${Date.now()}`),
    }).then(({ body }) => {
      financialAccount = body;
    });
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/credit-account-details+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/credit-account-details').as('postEntityRequest');
    cy.intercept('DELETE', '/api/credit-account-details/*').as('deleteEntityRequest');
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-accounts', {
      statusCode: 200,
      body: [financialAccount],
    });
  });

  afterEach(() => {
    if (creditAccountDetails) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/credit-account-details/${creditAccountDetails.id}`,
      }).then(() => {
        creditAccountDetails = undefined;
      });
    }
  });

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

  it('CreditAccountDetails menu should load CreditAccountDetails page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('credit-account-details');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('CreditAccountDetails').should('exist');
    cy.url().should('match', creditAccountDetailsPageUrlPattern);
  });

  describe('CreditAccountDetails page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(creditAccountDetailsPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create CreditAccountDetails page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/credit-account-details/new$'));
        cy.getEntityCreateUpdateHeading('CreditAccountDetails');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', creditAccountDetailsPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/credit-account-details',
          body: {
            creditLimit: 5000,
            statementDay: 10,
            paymentDueDay: 20,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            account: { id: financialAccount.id },
          },
        }).then(({ body }) => {
          creditAccountDetails = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/credit-account-details+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [creditAccountDetails],
            },
          ).as('entitiesRequestInternal');
        });

        cy.visit(creditAccountDetailsPageUrl);
        cy.wait('@entitiesRequestInternal');
      });

      it('detail button click should load details CreditAccountDetails page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('creditAccountDetails');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', creditAccountDetailsPageUrlPattern);
      });

      it('edit button click should load edit CreditAccountDetails page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('CreditAccountDetails');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', creditAccountDetailsPageUrlPattern);
      });

      it('edit button click should load edit CreditAccountDetails page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('CreditAccountDetails');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', creditAccountDetailsPageUrlPattern);
      });

      it('last delete button click should show delete blocked explanation for CreditAccountDetails', () => {
        cy.intercept('GET', '/api/credit-account-details/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('creditAccountDetails').should('exist');
        cy.get('[data-cy="creditAccountDetailsDeleteExplanation"]').should('be.visible');
        cy.get(entityConfirmDeleteButtonSelector).should('not.exist');
        cy.get('[data-cy="creditAccountDetailsDeleteCloseButton"]').click();
        cy.url().should('match', creditAccountDetailsPageUrlPattern);
      });
    });
  });

  describe('new CreditAccountDetails page', () => {
    beforeEach(() => {
      cy.visit(`${creditAccountDetailsPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('CreditAccountDetails');
    });

    it('should create an instance of CreditAccountDetails', () => {
      cy.get(`[data-cy="creditLimit"]`).type('5000');
      cy.get(`[data-cy="statementDay"]`).type('10');
      cy.get(`[data-cy="paymentDueDay"]`).type('20');
      cy.get(`[data-cy="createdAt"]`).type('2026-07-08T10:00');
      cy.get(`[data-cy="updatedAt"]`).type('2026-07-08T10:00');
      cy.get(`[data-cy="account"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.account.id).to.equal(financialAccount.id);
        creditAccountDetails = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', creditAccountDetailsPageUrlPattern);
    });
  });
});
