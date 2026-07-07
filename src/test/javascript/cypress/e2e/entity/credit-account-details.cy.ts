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
  // const creditAccountDetailsSample = {"creditLimit":26476.2,"statementDay":12,"paymentDueDay":26,"createdAt":"2026-07-07T16:24:02.688Z","updatedAt":"2026-07-07T15:36:04.632Z"};

  let creditAccountDetails;
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
      body: {"name":"splendid successfully about","institutionName":"correctly publicity shakily","accountType":"CREDIT_CARD","currency":"EUR","initialBalance":22745.83,"initialBalanceDate":"2026-07-07","lastFourDigits":"7300","description":"blowgun yuck","color":"#C335a7","icon":"formamide regarding","active":false,"includeInNetWorth":true,"createdAt":"2026-07-07T08:14:42.162Z","updatedAt":"2026-07-07T09:43:49.969Z"},
    }).then(({ body }) => {
      financialAccount = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/credit-account-details+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/credit-account-details').as('postEntityRequest');
    cy.intercept('DELETE', '/api/credit-account-details/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/financial-accounts', {
      statusCode: 200,
      body: [financialAccount],
    });

  });
   */

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
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/credit-account-details',
          body: {
            ...creditAccountDetailsSample,
            account: financialAccount,
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
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(creditAccountDetailsPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(creditAccountDetailsPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
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

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of CreditAccountDetails', () => {
        cy.intercept('GET', '/api/credit-account-details/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('creditAccountDetails').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', creditAccountDetailsPageUrlPattern);

        creditAccountDetails = undefined;
      });
    });
  });

  describe('new CreditAccountDetails page', () => {
    beforeEach(() => {
      cy.visit(`${creditAccountDetailsPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('CreditAccountDetails');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of CreditAccountDetails', () => {
      cy.get(`[data-cy="creditLimit"]`).type('14178.43');
      cy.get(`[data-cy="creditLimit"]`).should('have.value', '14178.43');

      cy.get(`[data-cy="statementDay"]`).type('30');
      cy.get(`[data-cy="statementDay"]`).should('have.value', '30');

      cy.get(`[data-cy="paymentDueDay"]`).type('13');
      cy.get(`[data-cy="paymentDueDay"]`).should('have.value', '13');

      cy.get(`[data-cy="annualInterestRate"]`).type('30435.38');
      cy.get(`[data-cy="annualInterestRate"]`).should('have.value', '30435.38');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-06T18:22');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-06T18:22');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-07T00:15');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-07T00:15');

      cy.get(`[data-cy="account"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        creditAccountDetails = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', creditAccountDetailsPageUrlPattern);
    });
  });
});
