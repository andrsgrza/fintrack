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

describe('Category e2e test', () => {
  const categoryPageUrl = '/category';
  const categoryPageUrlPattern = new RegExp('/category(\\?.*)?$');
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';
  // const categorySample = {"name":"confiscate trusting bandwidth","categoryType":"INCOME","active":false,"createdAt":"2026-07-07T12:44:56.963Z","updatedAt":"2026-07-06T22:23:58.056Z"};

  let category;
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
      body: {"login":".V@sLcNt\\mBdr\\oQ\\|u7lXd","firstName":"Ángela","lastName":"Navarro Alejandro","email":"Armando.CerdaVaca@yahoo.com","imageUrl":"pro","langKey":"obnoxiousl"},
    }).then(({ body }) => {
      user = body;
    });
  });
   */

  beforeEach(() => {
    cy.intercept('GET', '/api/categories+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/categories').as('postEntityRequest');
    cy.intercept('DELETE', '/api/categories/*').as('deleteEntityRequest');
  });

  /* Disabled due to incompatibility
  beforeEach(() => {
    // Simulate relationships api for better performance and reproducibility.
    cy.intercept('GET', '/api/users', {
      statusCode: 200,
      body: [user],
    });

    cy.intercept('GET', '/api/categories', {
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

    cy.intercept('GET', '/api/financial-subscriptions', {
      statusCode: 200,
      body: [],
    });

    cy.intercept('GET', '/api/budgets', {
      statusCode: 200,
      body: [],
    });

  });
   */

  afterEach(() => {
    if (category) {
      cy.authenticatedRequest({
        method: 'DELETE',
        url: `/api/categories/${category.id}`,
      }).then(() => {
        category = undefined;
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

  it('Categories menu should load Categories page', () => {
    cy.visit('/');
    cy.clickOnEntityMenuItem('category');
    cy.wait('@entitiesRequest').then(({ response }) => {
      if (response?.body.length === 0) {
        cy.get(entityTableSelector).should('not.exist');
      } else {
        cy.get(entityTableSelector).should('exist');
      }
    });
    cy.getEntityHeading('Category').should('exist');
    cy.url().should('match', categoryPageUrlPattern);
  });

  describe('Category page', () => {
    describe('create button click', () => {
      beforeEach(() => {
        cy.visit(categoryPageUrl);
        cy.wait('@entitiesRequest');
      });

      it('should load create Category page', () => {
        cy.get(entityCreateButtonSelector).click();
        cy.url().should('match', new RegExp('/category/new$'));
        cy.getEntityCreateUpdateHeading('Category');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', categoryPageUrlPattern);
      });
    });

    describe('with existing value', () => {
      /* Disabled due to incompatibility
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/categories',
          body: {
            ...categorySample,
            user: user,
          },
        }).then(({ body }) => {
          category = body;

          cy.intercept(
            {
              method: 'GET',
              url: '/api/categories+(?*|)',
              times: 1,
            },
            {
              statusCode: 200,
              body: [category],
            }
          ).as('entitiesRequestInternal');
        });

        cy.visit(categoryPageUrl);

        cy.wait('@entitiesRequestInternal');
      });
       */

      beforeEach(function () {
        cy.visit(categoryPageUrl);

        cy.wait('@entitiesRequest').then(({ response }) => {
          if (response?.body.length === 0) {
            this.skip();
          }
        });
      });

      it('detail button click should load details Category page', () => {
        cy.get(entityDetailsButtonSelector).first().click();
        cy.getEntityDetailsHeading('category');
        cy.get(entityDetailsBackButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', categoryPageUrlPattern);
      });

      it('edit button click should load edit Category page and go back', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('Category');
        cy.get(entityCreateSaveButtonSelector).should('exist');
        cy.get(entityCreateCancelButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', categoryPageUrlPattern);
      });

      it('edit button click should load edit Category page and save', () => {
        cy.get(entityEditButtonSelector).first().click();
        cy.getEntityCreateUpdateHeading('Category');
        cy.get(entityCreateSaveButtonSelector).click();
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', categoryPageUrlPattern);
      });

      // Reason: cannot create a required entity with relationship with required relationships.
      it.skip('last delete button click should delete instance of Category', () => {
        cy.intercept('GET', '/api/categories/*').as('dialogDeleteRequest');
        cy.get(entityDeleteButtonSelector).last().click();
        cy.wait('@dialogDeleteRequest');
        cy.getEntityDeleteDialogHeading('category').should('exist');
        cy.get(entityConfirmDeleteButtonSelector).click();
        cy.wait('@deleteEntityRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(204);
        });
        cy.wait('@entitiesRequest').then(({ response }) => {
          expect(response?.statusCode).to.equal(200);
        });
        cy.url().should('match', categoryPageUrlPattern);

        category = undefined;
      });
    });
  });

  describe('new Category page', () => {
    beforeEach(() => {
      cy.visit(`${categoryPageUrl}`);
      cy.get(entityCreateButtonSelector).click();
      cy.getEntityCreateUpdateHeading('Category');
    });

    // Reason: cannot create a required entity with relationship with required relationships.
    it.skip('should create an instance of Category', () => {
      cy.get(`[data-cy="name"]`).type('shrill bah');
      cy.get(`[data-cy="name"]`).should('have.value', 'shrill bah');

      cy.get(`[data-cy="description"]`).type('scruple drat');
      cy.get(`[data-cy="description"]`).should('have.value', 'scruple drat');

      cy.get(`[data-cy="categoryType"]`).select('BOTH');

      cy.get(`[data-cy="color"]`).type('#D01e59');
      cy.get(`[data-cy="color"]`).should('have.value', '#D01e59');

      cy.get(`[data-cy="icon"]`).type('round finer gee');
      cy.get(`[data-cy="icon"]`).should('have.value', 'round finer gee');

      cy.get(`[data-cy="active"]`).should('not.be.checked');
      cy.get(`[data-cy="active"]`).click();
      cy.get(`[data-cy="active"]`).should('be.checked');

      cy.get(`[data-cy="createdAt"]`).type('2026-07-07T10:11');
      cy.get(`[data-cy="createdAt"]`).blur();
      cy.get(`[data-cy="createdAt"]`).should('have.value', '2026-07-07T10:11');

      cy.get(`[data-cy="updatedAt"]`).type('2026-07-06T21:01');
      cy.get(`[data-cy="updatedAt"]`).blur();
      cy.get(`[data-cy="updatedAt"]`).should('have.value', '2026-07-06T21:01');

      cy.get(`[data-cy="user"]`).select(1);

      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        category = response.body;
      });
      cy.wait('@entitiesRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(200);
      });
      cy.url().should('match', categoryPageUrlPattern);
    });
  });
});
