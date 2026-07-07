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

describe('Budget e2e test', () => {
  const budgetPageUrl = '/budget';
  const budgetPageUrlPattern = new RegExp('/budget(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const budgetSample = {"name":"ah intermarry knottily","amount":5640.18,"currency":"EUR","period":"MONTHLY","startDate":"2026-07-07","status":"COMPLETED","tagMatchMode":"ALL","createdAt":"2026-07-06T19:34:25.746Z","updatedAt":"2026-07-06T23:31:18.196Z"};

  let budget;
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
      body: {"login":"I","firstName":"Hernán","lastName":"Carrasco Terrazas","email":"MariaEugenia_CantuMadrigal98@yahoo.com","imageUrl":"brightly psst","langKey":"whose"},
    }).then(({ body }) => {
      user = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/budgets+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/budgets').as('postEntityRequest');
    cy.intercept('DELETE', '/api/budgets/*').as('deleteEntityRequest');
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

  });
   */

  afterEach(() => {
    if (budget) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/budgets/${budget.id}`,
      }).then(() => {
        budget = undefined;
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

  it('Budgets menu should load Budgets page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('budget');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('Budget').should('exist');
    cy.url().should('match', budgetPageUrlPattern);
  });

  describe('Budget page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(budgetPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create Budget page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/budget/new$'));
        cy.getEntityCreateUpdateHeading('Budget');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', budgetPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/budgets',
          body: {
            ...budgetSample,
            user: user,
          },
        }).then(({ body }) => {
          budget = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/budgets+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [budget],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(budgetPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(budgetPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details Budget page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('budget');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', budgetPageUrlPattern);
      });

      it('edit button click should load edit Budget page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('Budget');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', budgetPageUrlPattern);
      });

      it('edit button click should load edit Budget page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('Budget');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', budgetPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of Budget', () => {
        cy.intercept('GET', '/api/budgets/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('budget').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', budgetPageUrlPattern);

        budget = undefined;
      });
    });
  });

  describe('new Budget page', () => {
    beforeEach(() => {
      cy.visit(`${budgetPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('Budget');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of Budget', () => {
      cy.get(`[data-cy="name"]`).type('lightly');
      cy.get(`[data-cy="name"]`).should('have.value', 'lightly');

      cy.get(`[data-cy="amount"]`).type('23650.25');
      cy.get(`[data-cy="amount"]`).should('have.value', '23650.25');

      cy.get(`[data-cy="currency"]`).select('EUR');

      cy.get(`[data-cy="period"]`).select('CUSTOM');

      cy.get(`[data-cy="startDate"]`).type('2026-07-06');
      cy.get(`[data-cy="startDate"]`).blur();
      cy.get(`[data-cy="startDate"]`).should('have.value', '2026-07-06');

      cy.get(`[data-cy="endDate"]`).type('2026-07-06');
      cy.get(`[data-cy="endDate"]`).blur();
      cy.get(`[data-cy="endDate"]`).should('have.value', '2026-07-06');

      cy.get(`[data-cy="status"]`).select('ACTIVE');

      cy.get(`[data-cy="tagMatchMode"]`).select('ALL');

      cy.get(`[data-cy="warningPercentage"]`).type('8.31');
      cy.get(`[data-cy="warningPercentage"]`).should('have.value', '8.31');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-06T21:26');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-06T21:26');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-07T14:42');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-07T14:42');

      cy.get(`[data-cy="user"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        budget = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', budgetPageUrlPattern);
    });
  });
});
