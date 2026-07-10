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
  const adminUsername = Cypress.env('E2E_ADMIN_USERNAME') ?? 'admin';
  const adminPassword = Cypress.env('E2E_ADMIN_PASSWORD') ?? 'admin';

  let budget;

  const buildBudgetPayload = (name: string) => {
    const now = new Date().toISOString();
    return {
      name,
      amount: 1000,
      currency: 'MXN',
      period: 'MONTHLY',
      startDate: '2026-07-01',
      status: 'ACTIVE',
      tagMatchMode: 'ANY',
      createdAt: now,
      updatedAt: now,
    };
  };

  const fillCreateForm = (name: string) => {
    cy.get('[data-cy="name"]').clear().type(name);
    cy.get('[data-cy="amount"]').clear().type('1000');
    cy.get('[data-cy="currency"]').select('MXN');
    cy.get('[data-cy="period"]').select('MONTHLY');
    cy.get('[data-cy="startDate"]').type('2026-07-01');
    cy.get('[data-cy="status"]').select('ACTIVE');
    cy.get('[data-cy="tagMatchMode"]').select('ANY');
    cy.get('[data-cy="createdAt"]').type('2026-07-08T10:00');
    cy.get('[data-cy="updatedAt"]').type('2026-07-08T10:00');
  };

  beforeEach(() => {
    cy.login(username, password);
  });

  beforeEach(() => {
    cy.intercept('GET', '/api/budgets+(?*|)').as('entitiesRequest');
    cy.intercept('POST', '/api/budgets').as('postEntityRequest');
    cy.intercept('DELETE', '/api/budgets/*').as('deleteEntityRequest');
  });

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
      beforeEach(() => {
        cy.authenticatedRequest({
          method: 'POST',
          url: '/api/budgets',
          body: buildBudgetPayload(`existing-${Date.now()}`),
        }).then(({ body }) => {
          budget = body;
        });

        cy.visit(budgetPageUrl);
        cy.wait('@entitiesRequest');
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

      it('last delete button click should delete instance of Budget', () => {
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
      cy.visit(`${budgetPageUrl}/new`);
      cy.getEntityCreateUpdateHeading('Budget');
    });

    it('should create an instance of Budget', () => {
      const budgetName = `create-${Date.now()}`;
      fillCreateForm(budgetName);
      cy.get('[data-cy="user"]').should('not.exist');
      cy.get(entityCreateSaveButtonSelector).click();

      cy.wait('@postEntityRequest').then(({ response }) => {
        expect(response?.statusCode).to.equal(201);
        expect(response?.body.user.login).to.equal(username);
        budget = response.body;
      });
      cy.url().should('match', budgetPageUrlPattern);
    });
  });

  describe('Budget ownership', () => {
    it('should not render user selector on create form', () => {
      cy.visit(`${budgetPageUrl}/new`);
      cy.get('[data-cy="user"]').should('not.exist');
    });

    it('regular user should not see budgets created by admin', () => {
      const adminBudgetName = `admin-only-${Date.now()}`;

      cy.login(adminUsername, adminPassword);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/budgets',
        body: buildBudgetPayload(adminBudgetName),
      }).then(({ body: adminBudget }) => {
        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/budgets',
        }).then(({ body: userBudgets }) => {
          expect(userBudgets.some(b => b.id === adminBudget.id)).to.equal(false);
        });

        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/budgets/${adminBudget.id}`,
        });
      });
    });

    it('admin should see budgets created by another user', () => {
      const userBudgetName = `user-owned-${Date.now()}`;

      cy.login(username, password);
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/budgets',
        body: buildBudgetPayload(userBudgetName),
      }).then(({ body: userBudget }) => {
        cy.login(adminUsername, adminPassword);
        cy.authenticatedRequest({
          method: 'GET',
          url: '/api/budgets',
        }).then(({ body: adminBudgets }) => {
          expect(adminBudgets.some(b => b.id === userBudget.id)).to.equal(true);
        });

        cy.login(username, password);
        cy.authenticatedRequest({
          method: 'DELETE',
          url: `/api/budgets/${userBudget.id}`,
        });
      });
    });
  });
});
