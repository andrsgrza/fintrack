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

describe('FinancialAccount e2e test', () => {
  const financialAccountPageUrl = '/financial-account';
  const financialAccountPageUrlPattern = new RegExp('/financial-account(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const financialAccountSample = {"name":"banish growing","accountType":"INVESTMENT","currency":"AJJ","initialBalance":4667.09,"initialBalanceDate":"2026-07-07","active":true,"includeInNetWorth":false,"createdAt":"2026-07-07T10:34:13.579Z","updatedAt":"2026-07-07T17:22:30.104Z"};

  let financialAccount;
  // let user;

  beforeEach(() => {
    cy.login(username, password);
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // create an instance at the required relationship entity:
    cy.authenticatedRequest({
      method: 'POST',
      url: '/api/users',
      body: {"login":"1a","firstName":"María Cristina","lastName":"Toledo Heredia","email":"JuanCarlos.RasconMedina7@hotmail.com","imageUrl":"readmit","langKey":"wallaby"},
    }).then(({ body }) => {
      user = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-accounts+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/financial-accounts').as('postEntityRequest');
    cy.intercept('DELETE', '/api/financial-accounts/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/users', {
      statusCode: 200,
      body: [user],
    });

    cy.intercept('GET', '/api/credit-account-details', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/financial-transactions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/financial-subscriptions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/budgets', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/transaction-ingestions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/api-access-tokens', {
      statusCode: 200,
      body: [],
    });

  });
   */

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

  /* Disabled due to incompatibility
  afterEach(() => {
    if (user) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/users/${user.id}`,
      }).then(() => {
        user = undefined;
      });
    }
  });
   */

  it('FinancialAccounts menu should load FinancialAccounts page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('financial-account');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('FinancialAccount').should('exist');
    cy.url().should('match', financialAccountPageUrlPattern);
  });

  describe('FinancialAccount page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(financialAccountPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create FinancialAccount page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/financial-account/new$'));
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-accounts',
          body: {
            ...financialAccountSample,
            user: user,
          },
        }).then(({ body }) => {
          financialAccount = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/financial-accounts+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [financialAccount],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(financialAccountPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(financialAccountPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details FinancialAccount page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('financialAccount');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      it('edit button click should load edit FinancialAccount page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      it('edit button click should load edit FinancialAccount page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialAccount');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of FinancialAccount', () => {
        cy.intercept('GET', '/api/financial-accounts/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('financialAccount').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialAccountPageUrlPattern);

        financialAccount = undefined;
      });
    });
  });

  describe('new FinancialAccount page', () => {
    beforeEach(() => {
      cy.visit(`${financialAccountPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('FinancialAccount');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of FinancialAccount', () => {
      cy.get(`[data-cy="name"]`).type('frail instead');
      cy.get(`[data-cy="name"]`).should('have.value', 'frail instead');

      cy.get(`[data-cy="institutionName"]`).type('er');
      cy.get(`[data-cy="institutionName"]`).should('have.value', 'er');

      cy.get(`[data-cy="accountType"]`).select('DEBIT');

      cy.get(`[data-cy="currency"]`).type('KTK');
      cy.get(`[data-cy="currency"]`).should('have.value', 'KTK');

      cy.get(`[data-cy="initialBalance"]`).type('8687.34');
      cy.get(`[data-cy="initialBalance"]`).should('have.value', '8687.34');

      cy.get(`[data-cy="initialBalanceDate"]`).type('2026-07-07');
      cy.get(`[data-cy="initialBalanceDate"]`).blur();
      cy.get(`[data-cy="initialBalanceDate"]`).should('have.value', '2026-07-07');

      cy.get(`[data-cy="lastFourDigits"]`).type('4801');
      cy.get(`[data-cy="lastFourDigits"]`).should('have.value', '4801');

      cy.get(`[data-cy="description"]`).type('hmph');
      cy.get(`[data-cy="description"]`).should('have.value', 'hmph');

      cy.get(`[data-cy="color"]`).type('#2Ec2A8');
      cy.get(`[data-cy="color"]`).should('have.value', '#2Ec2A8');

      cy.get(`[data-cy="icon"]`).type('excited');
      cy.get(`[data-cy="icon"]`).should('have.value', 'excited');

      cy.get(`[data-cy="active"]`).should('not.be.checked');
      cy.get(`[data-cy="active"]`).click();
      cy.get(`[data-cy="active"]`).should('be.checked');

      cy.get(`[data-cy="includeInNetWorth"]`).should('not.be.checked');
      cy.get(`[data-cy="includeInNetWorth"]`).click();
      cy.get(`[data-cy="includeInNetWorth"]`).should('be.checked');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T12:44');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T12:44');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-07T06:28');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-07T06:28');

      cy.get(`[data-cy="user"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        financialAccount = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', financialAccountPageUrlPattern);
    });
  });
});
