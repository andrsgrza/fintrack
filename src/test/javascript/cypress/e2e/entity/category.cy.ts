import {
  entityConfirmDeleteButtonSelector,
  entityCreateButtonSelector,
  entityCreateSaveButtonSelector,
  entityTableSelector,
} from '../../support/entity';

describe('Category product UX e2e test', () => {
  const categoryPageUrl = '/category';
  const username = Cypress.env('E2E_USERNAME') ?? 'user';
  const password = Cypress.env('E2E_PASSWORD') ?? 'user';

  let categories: Array<{ id: number; name: string }> = [];
  let candidateId: number | undefined;

  const uniqueName = (prefix: string) => `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

  const createCategoryViaApi = (name: string, categoryType = 'EXPENSE') =>
    cy
      .authenticatedRequest({
        method: 'POST',
        url: '/api/categories',
        body: {
          name,
          description: 'Category product E2E fixture',
          categoryType,
          color: '#336699',
          active: true,
        },
      })
      .then(({ body }) => {
        categories.push(body);
        return body;
      });

  const captureCategoryCreatedInTheUi = (name: string) =>
    cy.authenticatedRequest({ method: 'GET', url: '/api/categories?sort=id,desc' }).then(({ body, status }) => {
      expect(status).to.equal(200);
      const category = body.find(item => item.name === name);
      expect(category, `category ${name} was created`).to.exist;
      categories.push(category);
    });

  const fillCategoryForm = ({ name, description, color }: { name: string; description: string; color: string }) => {
    cy.get('[data-cy="name"]').clear().type(name);
    cy.get('[data-cy="description"]').clear().type(description);
    cy.get('[data-cy="color"]').clear().type(color);
  };

  beforeEach(() => {
    categories = [];
    candidateId = undefined;
    cy.login(username, password);
    cy.intercept('GET', '/api/categories+(?*|)').as('categoriesRequest');
    cy.intercept('GET', '/api/categories/*').as('categoryRequest');
    cy.intercept('GET', '/api/categories/count*').as('categoryChildrenCountRequest');
    cy.intercept('POST', '/api/categories').as('createCategoryRequest');
    cy.intercept('PATCH', '/api/categories/*').as('updateCategoryRequest');
    cy.intercept('DELETE', '/api/categories/*').as('deleteCategoryRequest');
  });

  afterEach(() => {
    if (candidateId) {
      cy.authenticatedRequest({ method: 'DELETE', url: `/api/transaction-candidates/${candidateId}`, failOnStatusCode: false });
      candidateId = undefined;
    }

    [...categories].reverse().forEach(category => {
      cy.authenticatedRequest({ method: 'DELETE', url: `/api/categories/${category.id}`, failOnStatusCode: false });
    });
    categories = [];
  });

  it('creates, presents, edits, deactivates/reactivates, and safely deletes categories as a hierarchy', () => {
    const rootName = uniqueName('Travel');
    const childName = uniqueName('Ride share');
    const flexibleName = uniqueName('Flexible');

    cy.viewport(1440, 900);
    cy.visit(categoryPageUrl);
    cy.wait('@categoriesRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="CategoryHeading"]').should('exist');
    cy.get(entityCreateButtonSelector).click();

    cy.get('[data-cy="categoryColorPicker"]').should('exist');
    cy.get('[data-cy="color"]').should('exist');
    cy.get('[data-cy="categoryColorControlRow"]').within(() => {
      cy.get('[data-cy="categoryColorPicker"]').should('exist');
      cy.get('[data-cy="color"]').should('exist');
    });
    cy.get('[data-cy="categoryColorPreview"]').should('not.exist');
    fillCategoryForm({ name: rootName, description: 'Travel expenses', color: '#123456' });
    cy.get('[data-cy="categoryType"]').select('EXPENSE');
    cy.get('[data-cy="active"]').should('be.checked');
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@createCategoryRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
    });
    captureCategoryCreatedInTheUi(rootName);

    cy.get(entityCreateButtonSelector).click();
    fillCategoryForm({ name: childName, description: 'Rides to work', color: '#abcdef' });
    cy.get('[data-cy="categoryType"]').select('EXPENSE');
    cy.then(() => {
      expect(categories[0], 'root category is tracked').to.exist;
      cy.get('[data-cy="parentCategory"]').select(String(categories[0].id));
    });
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@createCategoryRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
    });
    captureCategoryCreatedInTheUi(childName);

    cy.get(entityCreateButtonSelector).click();
    fillCategoryForm({ name: flexibleName, description: 'Can be retested', color: '#445566' });
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@createCategoryRequest').then(({ response }) => {
      expect(response?.statusCode).to.equal(201);
    });
    captureCategoryCreatedInTheUi(flexibleName);

    cy.get(entityTableSelector).should('contain', rootName).and('contain', childName);
    cy.contains('[data-cy="entityTable"]', rootName)
      .find('[data-cy="categoryDisclosureSlot"]')
      .then($parentSlot => {
        const parentSlotWidth = $parentSlot.outerWidth();
        cy.contains('[data-cy="entityTable"]', flexibleName)
          .find('[data-cy="categoryDisclosureSlot"]')
          .should($leafSlot => {
            expect($leafSlot.outerWidth()).to.equal(parentSlotWidth);
          })
          .find('[data-cy="categoryDisclosurePlaceholder"]')
          .should('exist');
      });
    cy.contains('[data-cy="entityTable"]', flexibleName).find('[data-cy="categoryDisclosure"]').should('not.exist');
    cy.contains('[data-cy="entityTable"]', rootName)
      .find('[data-cy="categoryDisclosure"]')
      .should('have.attr', 'aria-expanded', 'true')
      .click();
    cy.contains('[data-cy="entityTable"]', childName).should('not.exist');
    cy.contains('[data-cy="entityTable"]', rootName)
      .find('[data-cy="categoryDisclosure"]')
      .should('have.attr', 'aria-expanded', 'false')
      .click();
    cy.contains('[data-cy="entityTable"]', childName).should('exist');
    cy.get('[data-cy="categoryHierarchyConnector"]').should('not.exist');

    cy.get('[data-cy="categoryViewFlat"]').click().should('have.attr', 'aria-pressed', 'true');
    cy.contains('[data-cy="entityTable"]', childName)
      .should('have.attr', 'data-category-depth', '0')
      .find('[data-cy="categoryFlatPath"]')
      .should('contain', rootName)
      .and('contain', '›')
      .and('contain', childName);
    cy.contains('[data-cy="entityTable"]', childName)
      .find('[data-cy="categoryFlatPath"]')
      .contains('[data-cy="categoryBreadcrumbAncestor"]', rootName)
      .click();
    cy.then(() => {
      cy.location('pathname').should('eq', `/category/${categories[0].id}`);
    });
    cy.get('[data-cy="categoryDetailsHeading"]').should('exist');
    cy.get('[data-cy="categoryDetailColorAccent"]').should($header => {
      expect($header.css('border-left-color')).to.match(/rgb\(18,\s*52,\s*86\)/);
    });
    cy.get('[data-cy="categoryDetailAppearance"]').should('not.exist');
    cy.contains('#123456').should('not.exist');
    cy.get('[data-cy="categoryDetailStatusHelpButton"]').focus();
    cy.get('[data-cy="categoryDetailStatusHelpTooltip"]').should('be.visible');
    cy.get('[data-cy="categoryDetailStatusHelpButton"]').blur();
    cy.viewport(1024, 768);
    cy.get('[data-cy="categoryDetailStatusHelpButton"]').focus();
    cy.get('[data-cy="categoryDetailStatusHelpTooltip"]').should('be.visible');
    cy.then(() => {
      cy.contains('[data-cy="categoryChildLink"]', childName).should('have.attr', 'href', `/category/${categories[1].id}`).click();
    });
    cy.then(() => {
      cy.location('pathname').should('eq', `/category/${categories[1].id}`);
    });
    cy.get('[data-cy="categoryDetailsHeading"]').should('contain', childName);
    cy.get('[data-cy="categoryPath"]').should('contain', rootName).and('contain', childName);
    cy.go('back');
    cy.then(() => {
      cy.location('pathname').should('eq', `/category/${categories[0].id}`);
    });
    cy.contains('[data-cy="categoryChildren"]', childName).should('exist');
    cy.go('back');
    cy.location('pathname').should('eq', categoryPageUrl);
    cy.contains('[data-cy="entityTable"]', childName).should('exist');
    cy.get('[data-cy="categoryViewNested"]').click().should('have.attr', 'aria-pressed', 'true');
    cy.contains('[data-cy="entityTable"]', childName)
      .should('have.attr', 'data-category-depth', '1')
      .within(() => {
        cy.get('[data-cy="entityActionsMenuToggle"]').click();
        cy.get('[data-cy="entityDetailsButton"]').click();
      });
    cy.get('[data-cy="categoryDetailsHeading"]').should('exist');
    cy.get('[data-cy="categoryPath"]').should('contain', rootName).and('contain', childName);
    cy.get('[data-cy="categoryChildrenSummary"]').should('exist');

    cy.then(() => cy.visit(`/category/${categories[1].id}/edit`));
    cy.get('[data-cy="parentCategory"]').should('be.disabled').and('have.value', rootName);
    cy.get('[data-cy="categoryParentImmutableHelp"]').should('exist');
    cy.get('[data-cy="categoryType"]').should('be.disabled');
    cy.get('[data-cy="categoryColorPicker"]').should('exist');
    cy.get('[data-cy="categoryColorControlRow"]').within(() => {
      cy.get('[data-cy="categoryColorPicker"]').should('exist');
      cy.get('[data-cy="color"]').should('exist');
    });
    cy.get('[data-cy="categoryColorPreview"]').should('not.exist');
    cy.get('[data-cy="description"]').clear().type('Updated rides to work');
    cy.get('[data-cy="color"]').clear().type('#112233');
    cy.get('[data-cy="active"]').uncheck();
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@updateCategoryRequest').its('response.statusCode').should('eq', 200);

    cy.then(() => cy.visit(`/category/${categories[1].id}`));
    cy.contains('Updated rides to work').should('exist');
    cy.get('[data-cy="categoryStatusBadge"]').should('exist');
    cy.get('[data-cy="categoryDetailStatusHelpButton"]').should('exist').focus();

    cy.then(() => cy.visit(`/category/${categories[1].id}/edit`));
    cy.get('[data-cy="active"]').check();
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@updateCategoryRequest').its('response.statusCode').should('eq', 200);

    cy.then(() => cy.visit(`/category/${categories[0].id}/delete`));
    cy.wait('@categoryChildrenCountRequest').its('response.body').should('eq', 1);
    cy.get('[data-cy="categoryDeleteBlockedMessage"]').should('exist');
    cy.get(entityConfirmDeleteButtonSelector).should('not.exist');

    cy.then(() => cy.visit(`/category/${categories[2].id}/edit`));
    cy.wait('@categoryRequest').its('response.statusCode').should('eq', 200);
    cy.get('[data-cy="categoryType"]')
      .should('not.be.disabled')
      .and('have.value', 'EXPENSE')
      .select('INCOME')
      .should('have.value', 'INCOME');
    cy.get(entityCreateSaveButtonSelector).click();
    cy.wait('@updateCategoryRequest').then(({ request, response }) => {
      expect(request.body.categoryType).to.equal('INCOME');
      expect(response?.statusCode).to.equal(200);
      expect(response?.body.categoryType).to.equal('INCOME');
    });
    cy.then(() => cy.visit(`/category/${categories[2].id}`));
    cy.get('[data-cy="categoryTypeValue"]').should('contain', 'Ingreso');

    cy.then(() => cy.visit(`/category/${categories[1].id}/delete`));
    cy.wait('@categoryChildrenCountRequest').its('response.body').should('eq', 0);
    cy.get(entityConfirmDeleteButtonSelector).click();
    cy.wait('@deleteCategoryRequest').its('response.statusCode').should('eq', 204);
    cy.then(() => {
      const childId = categories[1].id;
      categories = categories.filter(category => category.id !== childId);
    });
  });

  it('shows a product-safe delete block when an active transaction workflow still references a category', () => {
    const categoryName = uniqueName('Protected category');
    createCategoryViaApi(categoryName).then(category => {
      cy.authenticatedRequest({
        method: 'POST',
        url: '/api/transaction-candidates/manual',
        body: {
          description: 'Draft using the protected category',
          category: { id: category.id },
        },
      }).then(({ body }) => {
        candidateId = body.id;
      });
    });

    cy.then(() => {
      cy.visit(`/category/${categories[0].id}/delete`);
      cy.wait('@categoryChildrenCountRequest').its('response.body').should('eq', 0);
      cy.get(entityConfirmDeleteButtonSelector).click();
      cy.wait('@deleteCategoryRequest').its('response.statusCode').should('eq', 400);
      cy.get('[data-cy="categoryDeleteError"]').should('exist');
      cy.contains('transaction candidates.').should('not.exist');
    });
  });
});
