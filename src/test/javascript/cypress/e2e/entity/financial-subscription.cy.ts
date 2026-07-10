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
  const adminUsername = Cypress.env('E2E_ADMIN_USERNAME') ?? 'admin';
  const adminPassword = Cypress.env('E2E_ADMIN_PASSWORD') ?? 'admin';

  let financialSubscription;

  const buildFinancialSubscriptionPayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      status: 'ACTIVE',
      currency: 'MXN',
      recurrenceUnit: 'MONTHLY',
      intervalCount: 1,
      startDate: '2026-07-01',
      automaticPayment: false,
      createdAt: now,
      updatedAt: now,
    };
  };

  const fillCreateForm = (name: string) => {
    cy.get('[data-cy="name"]').clear().type(name);
    cy.get('[data-cy="status"]').select('ACTIVE');
    cy.get('[data-cy="currency"]').select('MXN');
    cy.get('[data-cy="recurrenceUnit"]').select('MONTHLY');
    cy.get('[data-cy="intervalCount"]').clear().type('1');
    cy.get('[data-cy="startDate"]').type('2026-07-01');
    cy.get('[data-cy="createdAt"]').type('2026-07-08T10:00');
    cy.get('[data-cy="updatedAt"]').type('2026-07-08T10:00');
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/financial-subscriptions+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/financial-subscriptions').as('postEntityRequest');
    cy.intercept('DELETE', '/api/financial-subscriptions/*').as('deleteEntityRequest');
  });

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
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/financial-subscriptions',
          body: buildFinancialSubscriptionPayload(`existing-${Date.now()}`),
        }).then(({ body }) => {
          financialSubscription = body;
        });

        cy.visit(financialSubscriptionPageUrl);
        cy.wait('@entitiesRequest');
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

      it('last delete button click should delete instance of FinancialSubscription', () => {
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
      cy.visit(`${financialSubscriptionPageUrl}/new`);
      cy.getEntityCreateUpdateHeading('FinancialSubscription');
    });

    it('should create an instance of FinancialSubscription', () => {
      const subscriptionName = `create-${Date.now()}`;
      fillCreateForm(subscriptionName);
      cy.get('[data-cy="user"]').should('not.exist');
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.user.login).to.equal(username);
        financialSubscription = response.body;
      });
      cy.url().should('match', financialSubscriptionPageUrlPattern);
    });
  });

  describe('FinancialSubscription ownership', () => {
    it('should not render user selector on create form', () => {
      cy.visit(`${financialSubscriptionPageUrl}/new`);
      cy.get('[data-cy="user"]').should('not.exist');
    });

    it('regular user should not see financial subscriptions created by admin', () => {
      const adminSubscriptionName = `admin-only-${Date.now()}`;

      cy.login(adminUsername, adminPassword);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-subscriptions',
        body: buildFinancialSubscriptionPayload(adminSubscriptionName),
      }).then(({ body: adminSubscription }) => {
        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/financial-subscriptions',
        }).then(({ body: userSubscriptions }) => {
          expect(userSubscriptions.some(s => s.id === adminSubscription.id)).to.equal(false);
        });

        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/financial-subscriptions/${adminSubscription.id}`,
        });
      });
    });

    it('admin should see financial subscriptions created by another user', () => {
      const userSubscriptionName = `user-owned-${Date.now()}`;

      cy.login(username, password);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/financial-subscriptions',
        body: buildFinancialSubscriptionPayload(userSubscriptionName),
      }).then(({ body: userSubscription }) => {
        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/financial-subscriptions',
        }).then(({ body: adminSubscriptions }) => {
          expect(adminSubscriptions.some(s => s.id === userSubscription.id)).to.equal(true);
        });

        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/financial-subscriptions/${userSubscription.id}`,
        });
      });
    });
  });
});
