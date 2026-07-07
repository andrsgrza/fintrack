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

describe('FinancialSubscription e2e test', () => {
  const financialSubscriptionPageUrl = '/financial-subscription';
  const financialSubscriptionPageUrlPattern = new RegExp('/financial-subscription(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const financialSubscriptionSample = {"name":"stealthily observe unabashedly","status":"CANCELLED","currency":"VIG","recurrenceUnit":"MONTH","intervalCount":10752,"startDate":"2026-07-07","automaticPayment":false,"createdAt":"2026-07-06T22:50:37.634Z","updatedAt":"2026-07-07T15:33:48.115Z"};

  let financialSubscription;
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
      body: {"login":"r2D","firstName":"Agustín","lastName":"Santillán Argüello","email":"Manuela95@hotmail.com","imageUrl":"up swing","langKey":"miserably "},
    }).then(({ body }) => {
      user = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-subscriptions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/financial-subscriptions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/financial-subscriptions/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/users', {
      statusCode: 200,
      body: [user],
    });

    cy.intercept('GET', '/api/financial-accounts', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/categories', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/tags', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/financial-transactions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/transaction-rules', {
      statusCode: 200,
      body: [],
    });

  });
   */

  afterEach(() => {
    if (financialSubscription) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/financial-subscriptions/${financialSubscription.id}`,
      }).then(() => {
        financialSubscription = undefined;
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

  it('FinancialSubscriptions menu should load FinancialSubscriptions page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('financial-subscription');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('FinancialSubscription').should('exist');
    cy.url().should('match', financialSubscriptionPageUrlPattern);
  });

  describe('FinancialSubscription page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(financialSubscriptionPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create FinancialSubscription page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/financial-subscription/new$'));
        cy.getEntityCreateUpdateHeading('FinancialSubscription');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialSubscriptionPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-subscriptions',
          body: {
            ...financialSubscriptionSample,
            user: user,
          },
        }).then(({ body }) => {
          financialSubscription = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/financial-subscriptions+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [financialSubscription],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(financialSubscriptionPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(financialSubscriptionPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details FinancialSubscription page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('financialSubscription');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialSubscriptionPageUrlPattern);
      });

      it('edit button click should load edit FinancialSubscription page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialSubscription');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialSubscriptionPageUrlPattern);
      });

      it('edit button click should load edit FinancialSubscription page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('FinancialSubscription');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialSubscriptionPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of FinancialSubscription', () => {
        cy.intercept('GET', '/api/financial-subscriptions/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('financialSubscription').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', financialSubscriptionPageUrlPattern);

        financialSubscription = undefined;
      });
    });
  });

  describe('new FinancialSubscription page', () => {
    beforeEach(() => {
      cy.visit(`${financialSubscriptionPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('FinancialSubscription');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of FinancialSubscription', () => {
      cy.get(`[data-cy="name"]`).type('incidentally thorough');
      cy.get(`[data-cy="name"]`).should('have.value', 'incidentally thorough');

      cy.get(`[data-cy="description"]`).type('ack unbearably when');
      cy.get(`[data-cy="description"]`).should('have.value', 'ack unbearably when');

      cy.get(`[data-cy="status"]`).select('ACTIVE');

      cy.get(`[data-cy="expectedAmount"]`).type('900.91');
      cy.get(`[data-cy="expectedAmount"]`).should('have.value', '900.91');

      cy.get(`[data-cy="amountTolerancePercentage"]`).type('40.54');
      cy.get(`[data-cy="amountTolerancePercentage"]`).should('have.value', '40.54');

      cy.get(`[data-cy="currency"]`).type('HNP');
      cy.get(`[data-cy="currency"]`).should('have.value', 'HNP');

      cy.get(`[data-cy="recurrenceUnit"]`).select('DAY');

      cy.get(`[data-cy="intervalCount"]`).type('24088');
      cy.get(`[data-cy="intervalCount"]`).should('have.value', '24088');

      cy.get(`[data-cy="startDate"]`).type('2026-07-06');
      cy.get(`[data-cy="startDate"]`).blur();
      cy.get(`[data-cy="startDate"]`).should('have.value', '2026-07-06');

      cy.get(`[data-cy="nextExpectedDate"]`).type('2026-07-07');
      cy.get(`[data-cy="nextExpectedDate"]`).blur();
      cy.get(`[data-cy="nextExpectedDate"]`).should('have.value', '2026-07-07');

      cy.get(`[data-cy="endDate"]`).type('2026-07-07');
      cy.get(`[data-cy="endDate"]`).blur();
      cy.get(`[data-cy="endDate"]`).should('have.value', '2026-07-07');

      cy.get(`[data-cy="automaticPayment"]`).should('not.be.checked');
      cy.get(`[data-cy="automaticPayment"]`).click();
      cy.get(`[data-cy="automaticPayment"]`).should('be.checked');

      cy.get(`[data-cy="notes"]`).type('a vacantly er');
      cy.get(`[data-cy="notes"]`).should('have.value', 'a vacantly er');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T15:03');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T15:03');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-07T07:58');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-07T07:58');

      cy.get(`[data-cy="user"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        financialSubscription = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', financialSubscriptionPageUrlPattern);
    });
  });
});
