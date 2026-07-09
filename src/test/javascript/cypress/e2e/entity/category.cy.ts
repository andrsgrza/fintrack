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
  const adminUsername = Cypress.env('E2E_ADMIN_USERNAME') ?? 'admin';
  const adminPassword = Cypress.env('E2E_ADMIN_PASSWORD') ?? 'admin';

  let category;

  const buildCategoryPayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      description: 'E2E category',
      categoryType: 'EXPENSE',
      color: '#a1b2c3',
      active: true,
      createdAt: now,
      updatedAt: now,
    };
  };

  const fillCreateForm = (name: string) => {
    cy.get('[data-cy="name"]').clear().type(name);
    cy.get('[data-cy="description"]').clear().type('E2E category');
    cy.get('[data-cy="categoryType"]').select('EXPENSE');
    cy.get('[data-cy="color"]').clear().type('#a1b2c3');
    cy.get('[data-cy="active"]').check();
    cy.get('[data-cy="createdAt"]').type('2026-07-08T10:00');
    cy.get('[data-cy="updatedAt"]').type('2026-07-08T10:00');
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/categories+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/categories').as('postEntityRequest');
    cy.intercept('DELETE', '/api/categories/*').as('deleteEntityRequest');
  });

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
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/categories',
          body: buildCategoryPayload(`existing-${Date.now()}`),
        }).then(({ body }) => {
          category = body;
        });

        cy.visit(categoryPageUrl);
        cy.wait('@entitiesRequest');
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

      it('last delete button click should delete instance of Category', () => {
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
      cy.visit(`${categoryPageUrl}/new`);
      cy.getEntityCreateUpdateHeading('Category');
    });

    it('should create an instance of Category', () => {
      const categoryName = `create-${Date.now()}`;
      fillCreateForm(categoryName);
      cy.get('[data-cy="user"]').should('not.exist');
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.user.login).to.equal(username);
        category = response.body;
      });
      cy.url().should('match', categoryPageUrlPattern);
    });
  });

  describe('Category ownership', () => {
    it('should not render user selector on create form', () => {
      cy.visit(`${categoryPageUrl}/new`);
      cy.get('[data-cy="user"]').should('not.exist');
    });

    it('regular user should not see categories created by admin', () => {
      const adminCategoryName = `admin-only-${Date.now()}`;

      cy.login(adminUsername, adminPassword);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/categories',
        body: buildCategoryPayload(adminCategoryName),
      }).then(({ body: adminCategory }) => {
        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/categories',
        }).then(({ body: userCategories }) => {
          expect(userCategories.some(c => c.id === adminCategory.id)).to.equal(false);
        });

        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/categories/${adminCategory.id}`,
        });
      });
    });

    it('admin should see categories created by another user', () => {
      const userCategoryName = `user-owned-${Date.now()}`;

      cy.login(username, password);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/categories',
        body: buildCategoryPayload(userCategoryName),
      }).then(({ body: userCategory }) => {
        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/categories',
        }).then(({ body: adminCategories }) => {
          expect(adminCategories.some(c => c.id === userCategory.id)).to.equal(true);
        });

        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/categories/${userCategory.id}`,
        });
      });
    });
  });
});
